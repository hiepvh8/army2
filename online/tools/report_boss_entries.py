#!/usr/bin/env python3
"""Create an index of original boss entry observations."""
from pathlib import Path
import json
import shutil
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'test-results/boss-entry-sweep';OUT=ROOT/'docs/kiem-thu-chi-tiet';DEST=OUT/'anh-boss-goc'
DEST.mkdir(parents=True,exist_ok=True)
items=json.loads((SRC/'results.json').read_text())
lines=['# Kiểm thử cửa vào boss gốc','',
       'Mỗi dòng là một JVM MicroEmulator và nhân vật mới với RMS thử. Bài thử chọn từng mục '
       'boss có ID 0–14, nhập 1 vào các hộp độ khó/cấp khi xuất hiện và nhấn bắt đầu ở màn '
       'chuẩn bị. Kết quả chỉ phản ánh điều kiện nhân vật mới và thời điểm chạy; '
       'chưa khẳng định có thể đánh thắng hay nhận thưởng.','',
       '| ID | Cửa boss | Kết quả | Thông báo | Ảnh |','|---|---|---|---|---|']
for item in items:
    result=item.get('result',[]);selected=item.get('selected',[]);index=item['index']
    name=result[3] if len(result)>3 and result[3] else (selected[2] if len(selected)>2 else '?')
    status=result[2] if len(result)>2 else 'ERROR'
    label={'BATTLE':'Đã mở màn trận','DIALOG':'Hiện hộp thoại','SCREEN':'Mở màn khác','NOT_LISTED':'Không thấy trong danh sách'}.get(status,'Lỗi/chưa hoàn tất')
    message=result[5] if len(result)>5 else ('; '.join(item.get('errors',[])) or '-')
    image=SRC/('boss-%02d'%index)/'screen.png';link='Không có ảnh'
    if image.exists():
        dest=DEST/('boss-%02d.png'%index);shutil.copyfile(image,dest);link='[Xem](anh-boss-goc/'+dest.name+')'
    lines.append('| '+str(index)+' | '+name.replace('|','\\|')+' | '+label+' | '+message.replace('|','\\|')+' | '+link+' |')
lines+=['','Kết quả không chứng minh mọi boss, mọi cấp, sát thương, điều khiển, phần thưởng '
        'hoặc điều kiện giờ hoạt động đúng; boss thế giới có thể bị chặn ngoài khung giờ.','']
(OUT/'DANH_SACH_BOSS_GOC.vi.md').write_text('\n'.join(lines),encoding='utf-8')
shutil.copyfile(SRC/'results.json',OUT/'ket_qua_boss_goc.json')
print('Wrote',len(items),'boss observations')
