#!/usr/bin/env python3
"""Explore original menus in isolated emulator processes; record observations, not assertions."""
import concurrent.futures
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'test-results/navigation-full'
CLASSES=ROOT/'build/navigation-audit'
EMU=ROOT/'.tools/microemulator.jar'
JAR=ROOT/'dist/ARMY2_Online_IOS.jar'


def run_one(job):
    path, action=job
    ident=('root' if not path else 'path_'+path.replace('-','_'))
    if action: ident+='__'+action.replace(':','_').replace(',','__')
    folder=OUT/ident;folder.mkdir(parents=True,exist_ok=True)
    with (folder/'run.log').open('w') as log:
        try:
            result=subprocess.run(['java','-Djava.awt.headless=true','-cp',str(CLASSES)+':'+str(EMU),
                'GameNavigationAudit',str(JAR),str(folder),path,action],stdout=log,stderr=log,timeout=50)
            code=result.returncode
        except subprocess.TimeoutExpired: code=124
    lines=(folder/'run.log').read_text(errors='replace').splitlines()
    nav=[s for s in lines if s.startswith('NAV_')]
    state=[s.split('\t') for s in nav if s.startswith('NAV_STATE\t')]
    steps=[s.split('\t') for s in nav if s.startswith('NAV_CHOOSE\t')]
    popups=[s.split('\t') for s in nav if s.startswith('NAV_POPUP\t')]
    dialog=[s.split('\t') for s in nav if s.startswith('NAV_DIALOG_COMMANDS\t')]
    displays=[s.split('\t') for s in nav if s.startswith('NAV_DISPLAY\t')]
    targets=[s.split('\t') for s in nav if s.startswith('NAV_ACTION_TARGET\t')]
    errors=[s for s in nav if s.startswith(('NAV_ERROR','NAV_BLOCKED'))]
    item=dict(id=ident,path=path,action=action,exit=code,steps=steps,states=state,
              popups=popups,dialog_commands=dialog,displays=displays,targets=targets,errors=errors,
              done='NAV_DONE' in nav,screenshot=(ident+'/screen.png'))
    return item


def execute(jobs, name):
    results=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        futures={pool.submit(run_one,job):job for job in jobs}
        for future in concurrent.futures.as_completed(futures):
            job=futures[future]
            try: result=future.result()
            except Exception as exc: result=dict(path=job[0],action=job[1],exit=125,errors=[str(exc)])
            results.append(result)
            print(f"{name} {len(results)}/{len(jobs)}: {result.get('id',job)} exit={result['exit']}",flush=True)
    results.sort(key=lambda x:(x['path'],x['action']))
    (OUT/(name+'.json')).write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
    return results


def final_popup(item):
    """Popup items belonging to the final state, even for older duplicate tags."""
    path=OUT/item['id']/'run.log'
    lines=[s for s in path.read_text(errors='replace').splitlines() if s.startswith('NAV_')]
    positions=[i for i,s in enumerate(lines) if s.startswith('NAV_STATE\t')]
    if not positions:return []
    return [s.split('\t') for s in lines[positions[-1]+1:] if s.startswith('NAV_POPUP\t')]


def main():
    OUT.mkdir(parents=True,exist_ok=True);CLASSES.mkdir(parents=True,exist_ok=True)
    subprocess.run(['java','-jar',str(ROOT/'.tools/ecj.jar'),'-1.8','-nowarn','-classpath',str(EMU),
        '-d',str(CLASSES),str(ROOT/'tests/EmulatorClient.java'),str(ROOT/'tests/GameNavigationAudit.java')],check=True)
    if '--frontier' in sys.argv:
        nested=json.loads((OUT/'nested.json').read_text())
        # Returning to an already-explored popup is a loop, not a new screen.
        seen=set()
        for filename in ('base.json','actions.json'):
            for item in json.loads((OUT/filename).read_text()):
                if item.get('states') and item['states'][-1][5]=='true':
                    seen.add(tuple(p[3] for p in final_popup(item)))
        frontier=[]; skipped=[]
        for item in nested:
            if not item.get('done') or item.get('errors') or not item.get('states') or item['states'][-1][5]!='true':continue
            options=final_popup(item);signature=tuple(p[3] for p in options)
            if signature in seen:
                skipped.append(item['id']);continue
            seen.add(signature)
            for p in options:frontier.append((item['path'],item['action']+',popup:'+p[2]))
        (OUT/'frontier-skipped-loops.json').write_text(json.dumps(skipped,ensure_ascii=False,indent=2)+'\n')
        print('FRONTIER_ACTIONS:',len(frontier),'RETURN_LOOPS:',len(skipped),flush=True)
        execute(frontier,'frontier')
        return
    base=[('', '')]+[(str(i),'') for i in range(4)]+[(f'0-{i}','') for i in range(12)]
    base += [(f'0-0-{i}','') for i in range(5)]
    base += [('0-2-0','')]+[(f'0-3-{i}','') for i in range(5)]
    initial=execute(base,'base')
    if '--base-only' in sys.argv:return
    jobs=[]
    for item in initial:
        if not item.get('done') or item.get('errors') or not item.get('states'):continue
        state=item['states'][-1]
        # Old cloud/relay may contact an external service; do not submit forms there.
        if 'Cloud' in state[2] or 'Relay' in state[2]:continue
        if state[5]=='true':
            last_tag=state[1]
            for p in item['popups']:
                if p[1]==last_tag: jobs.append((item['path'],'popup:'+p[2]))
        elif state[3]:
            commands=item.get('dialog_commands',[])
            if commands:
                for name in re.findall(r'(left|center|right)=',commands[-1][2]):jobs.append((item['path'],name))
        elif state[2]!='screen.MenuScr':
            for name in re.findall(r'(left|center|right)=',state[6]):jobs.append((item['path'],name))
    print('SECONDARY_ACTIONS:',len(jobs),flush=True)
    actions=execute(jobs,'actions')
    deep=[]
    for item in actions:
        if not item.get('done') or item.get('errors') or not item.get('states'):continue
        state=item['states'][-1]
        if state[5]!='true' or 'Cloud' in state[2] or 'Relay' in state[2]:continue
        for p in item['popups']:
            if p[1]==state[1]:deep.append((item['path'],item['action']+',popup:'+p[2]))
    print('NESTED_POPUP_ACTIONS:',len(deep),flush=True)
    execute(deep,'nested')


if __name__=='__main__':main()
