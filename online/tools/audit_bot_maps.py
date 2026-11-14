#!/usr/bin/env python3
"""Test all 29 distinct listed original PvP-bot maps in fresh JVMs."""
from pathlib import Path
import concurrent.futures
import json
import subprocess

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'test-results/bot-map-sweep';OUT.mkdir(parents=True,exist_ok=True)
classes=ROOT/'build/bot-map-sweep';classes.mkdir(parents=True,exist_ok=True)
emu=ROOT/'.tools/microemulator.jar'
subprocess.run(['java','-jar',str(ROOT/'.tools/ecj.jar'),'-1.8','-nowarn','-classpath',str(emu),
                '-d',str(classes),*(str(ROOT/'tests'/n) for n in ('EmulatorClient.java','BotAudit.java','BotMapSweep.java'))],check=True)

def run(slot):
    folder=OUT/('slot-%02d'%slot);folder.mkdir(exist_ok=True)
    with (folder/'run.log').open('w') as log:
        try:
            r=subprocess.run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(emu),
                              'BotMapSweep',str(ROOT/'dist/ARMY2_Online_IOS.jar'),str(folder),str(slot)],
                             stdout=log,stderr=log,timeout=70)
            code=r.returncode
        except subprocess.TimeoutExpired:code=124
    lines=(folder/'run.log').read_text(errors='replace').splitlines()
    return dict(slot=slot,exit=code,selected=next((x.split('\t') for x in lines if x.startswith('MAP_SELECTED\t')),[]),
                result=next((x.split('\t') for x in lines if x.startswith('MAP_RESULT\t')),[]),
                errors=[x for x in lines if x.startswith('MAP_ERROR\t')],passed='MAP_PASS' in lines)

results=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    for future in concurrent.futures.as_completed([pool.submit(run,i) for i in range(29)]):
        item=future.result();results.append(item)
        print('MAP',len(results),'/29 slot',item['slot'],'pass',item['passed'],'exit',item['exit'],flush=True)
results.sort(key=lambda x:x['slot'])
(OUT/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
print('MAP_SUMMARY',sum(item['passed'] for item in results),'/',len(results),flush=True)
if not all(item['passed'] for item in results):raise SystemExit('Some maps failed: inspect results.json')
