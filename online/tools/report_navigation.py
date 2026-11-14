#!/usr/bin/env python3
"""Create a browseable Vietnamese report from the original-menu audit."""
from pathlib import Path
import json
import re
import shutil

ROOT=Path(__file__).resolve().parents[1]
RUN=ROOT/'test-results/navigation-full'
OUT=ROOT/'docs/kiem-thu-chi-tiet'
IMG=OUT/'anh'
IMG.mkdir(parents=True,exist_ok=True)


def clean(text):
    return str(text or '').replace('|','\\|').replace('\t',' ').replace('\n',' ').replace('\r',' ')


def action_caption(item, bases):
    targets=item.get('targets',[])
    base=bases.get(item['path'],{})
    state=base.get('states',[])
    captions=state[-1][6] if state else ''
    if state and state[-1][3] and base.get('dialog_commands'):
        captions=base['dialog_commands'][-1][2]
    names=[]
    for target in targets:
        if len(target)>=4 and target[1]=='popup':names.append(target[3])
        elif len(target)>=3:
            key=target[2];label=key
            for segment in captions.split(';'):
                if segment.startswith(key+'='):label=segment[len(key)+1:]
            names.append(label)
    return ' → '.join(names) if names else item.get('action','')


def detail(item):
    states=item.get('states',[])
    if not states:return 'Không có trạng thái; xem lỗi.'
    s=states[-1];parts=['Màn `'+clean(s[2].replace('screen.','').replace('shop.',''))+'`']
    if s[3]:parts.append('hộp `'+clean(s[3])+'`: '+clean(s[4]))
    if s[5]=='true':
        log=RUN/item.get('id','')/'run.log'
        nav=[line for line in log.read_text(errors='replace').splitlines() if line.startswith('NAV_')] if log.exists() else []
        positions=[i for i,line in enumerate(nav) if line.startswith('NAV_STATE\t')]
        labels=[line.split('\t',3)[3] for line in nav[positions[-1]+1:] if line.startswith('NAV_POPUP\t')] if positions else []
        parts.append('menu: '+clean(', '.join(labels)))
    for d in item.get('displays',[]):
        if d[1]==s[1] and d[2] not in ('coreLG.CCanvas','null'):
            parts.append('hiển thị `'+clean(d[2])+'` '+clean(d[3] if len(d)>3 else ''))
    return '; '.join(parts)


def generate():
    batches=[]
    for name in ('base','actions','nested','frontier'):
        path=RUN/(name+'.json')
        if not path.exists():raise SystemExit('Missing audit output: '+str(path))
        batches.append((name,json.loads(path.read_text())))
    bases={item['path']:item for item in batches[0][1]}
    for p in IMG.glob('*.png'):p.unlink()
    lines=['# Nhật ký chi tiết màn hình game gốc','',
           'Mỗi dòng là một phiên giả lập tách biệt với nhân vật thử nghiệm và RMS tạm. '
           'Các lựa chọn menu được đặt qua công cụ kiểm thử và gọi đúng action của game; '
           'không phải tất cả đều được chạm tay trên iPhone. Kết quả là trạng thái quan sát sau thao tác, '
           'không tự chứng minh toàn bộ tính năng phía sau đã hoạt động.','',
           'Bản đồ họa online, kết quả hai người bắn nhau và các giới hạn xem trong [báo cáo tổng hợp](BAO_CAO.vi.md).','']
    count=0
    for name,items in batches:
        title={'base':'Mở từng nhánh menu chính','actions':'Nút và lựa chọn ở màn tiếp theo','nested':'Lựa chọn trong menu con','frontier':'Menu con sâu hơn'}[name]
        lines+=['## '+title,'','| Đường đi / nút thử | Kết quả quan sát | Ảnh |','|---|---|---|']
        for item in items:
            count+=1
            steps=[x[3] for x in item.get('steps',[]) if len(x)>3]
            route=' → '.join(steps) or 'Màn game gốc'
            if item.get('action'):
                route+=' → **'+action_caption(item,bases)+'**'
            result=detail(item)
            if item.get('errors') or item.get('exit'):
                result='**Lỗi/chưa hoàn tất:** '+clean('; '.join(item.get('errors',[])) or 'mã '+str(item.get('exit'))) + '; '+result
            log=RUN/item.get('id','')/'run.log'
            if log.exists():
                faults=[line for line in log.read_text(errors='replace').splitlines()
                        if re.match(r'^java\.lang\.(NullPointerException|ArrayIndexOutOfBoundsException|VerifyError|IllegalStateException)',line)]
                if faults and not item.get('errors'):
                    result='**Có lỗi trong luồng nền/vẽ:** '+clean(faults[0])+'; '+result
            src=RUN/item.get('id','')/('after-action.png' if item.get('action') else 'screen.png')
            if not src.exists():src=RUN/item.get('id','')/'error.png'
            picture='Không có ảnh'
            if src.exists():
                dst=IMG/(item['id']+'.png');shutil.copyfile(src,dst);item['screenshot']='anh/'+dst.name;picture='[Xem]('+item['screenshot']+')'
            else:
                item['screenshot']=None
            lines.append('| '+clean(route)+' | '+result+' | '+picture+' |')
        lines.append('')
    (OUT/'DANH_SACH_MAN_HINH.vi.md').write_text('\n'.join(lines).rstrip()+'\n',encoding='utf-8')
    with (OUT/'ket_qua_chi_tiet.json').open('w',encoding='utf-8') as data:
        data.write('{"batches":[\n')
        for batch_index,(name,items) in enumerate(batches):
            if batch_index:data.write(',\n')
            data.write('['+json.dumps(name,ensure_ascii=False)+',[\n')
            data.write(',\n'.join(json.dumps(item,ensure_ascii=False,separators=(',',':')) for item in items))
            data.write('\n]]')
        data.write('\n]}\n')
    print('Recorded',count,'individual menu scenarios;',len(list(IMG.glob('*.png'))),'screenshots in',OUT)


if __name__=='__main__':generate()
