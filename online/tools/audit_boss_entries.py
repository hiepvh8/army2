#!/usr/bin/env python3
"""Observe the first 15 original boss entries using isolated new characters."""
from pathlib import Path
import concurrent.futures
import json
import subprocess
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'test-results/boss-entry-sweep';OUT.mkdir(parents=True,exist_ok=True)
classes=ROOT/'build/boss-entry-sweep';classes.mkdir(parents=True,exist_ok=True)
emu=ROOT/'.tools/microemulator.jar'
subprocess.run(['java','-jar',str(ROOT/'.tools/ecj.jar'),'-1.8','-nowarn','-classpath',str(emu),'-d',str(classes),
                *(str(ROOT/'tests'/n) for n in ('EmulatorClient.java','BotAudit.java','BossEntrySweep.java'))],check=True)
def run(index):
    folder=OUT/('boss-%02d'%index);folder.mkdir(exist_ok=True)
    with (folder/'run.log').open('w') as log:
        try:
            result=subprocess.run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(emu),'BossEntrySweep',
                str(ROOT/'dist/ARMY2_Online_IOS.jar'),str(folder),str(index)],stdout=log,stderr=log,timeout=60)
            code=result.returncode
        except subprocess.TimeoutExpired:code=124
    lines=(folder/'run.log').read_text(errors='replace').splitlines()
    return dict(index=index,exit=code,result=next((x.split('\t') for x in lines if x.startswith('BOSS_RESULT\t')),[]),
                selected=next((x.split('\t') for x in lines if x.startswith('BOSS_SELECTED\t')),[]),
                inputs=[x.split('\t') for x in lines if x.startswith('BOSS_INPUT\t')],
                errors=[x for x in lines if x.startswith('BOSS_ERROR\t')])
items=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    for future in concurrent.futures.as_completed([pool.submit(run,i) for i in range(15)]):
        item=future.result();items.append(item)
        print('BOSS',len(items),'/15 index',item['index'],'result',item['result'][2:3],'exit',item['exit'],flush=True)
items.sort(key=lambda x:x['index'])
(OUT/'results.json').write_text(json.dumps(items,ensure_ascii=False,indent=2)+'\n')
print('BOSS_SWEEP_COMPLETE',len(items),flush=True)
