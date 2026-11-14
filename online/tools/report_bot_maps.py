#!/usr/bin/env python3
"""Create an index of the original PvP bot map smoke tests."""
from pathlib import Path
import json
import shutil
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'test-results/bot-map-sweep'
OUT=ROOT/'docs/kiem-thu-chi-tiet'
DEST=OUT/'anh-map-bot';DEST.mkdir(parents=True,exist_ok=True)
items=json.loads((SRC/'results.json').read_text())
lines=['# Kiểm thử mở map PvP bot của game gốc','',
       'Mỗi map được mở trong một JVM MicroEmulator và RMS thử riêng. Bài thử tạo nhân vật mới, '
       'chọn map trong màn cấu hình PvP bot, nhấn bắt đầu, kiểm tra vào `GameScr` và '
       'kiểm tra các khối địa hình có ảnh. Chọn map được thực hiện qua công cụ kiểm thử; '
       'không phải thử cảm ứng iPhone. Đây là kiểm thử tải map, chưa phải chơi hết từng map.','',
       '| ID | Tên map | Kích thước gốc | Khối đã tải / tổng | Kết quả | Ảnh trận |',
       '|---|---|---|---|---|---|']
for item in items:
    selected=item['selected'];res=item['result'];slot=item['slot']
    mid=selected[1] if len(selected)>2 else '?';name=selected[2] if len(selected)>2 else '?'
    size=(res[3]+' × '+res[4]) if len(res)>6 else '?'
    tiles=(str(int(res[5])-int(res[6]))+'/'+res[5]) if len(res)>6 else '?'
    img=SRC/('slot-%02d'%slot)/'battle.png';link='Không có ảnh'
    if img.exists():
        dst=DEST/('map-%s.png'%mid);shutil.copyfile(img,dst);link='[Xem](anh-map-bot/'+dst.name+')'
    status='Đã vào trận, đủ ảnh khối' if item['passed'] else 'Lỗi: '+('; '.join(item['errors']) or 'mã '+str(item['exit']))
    lines.append('| '+mid+' | '+name.replace('|','\\|')+' | '+size+' | '+tiles+' | '+status.replace('|','\\|')+' | '+link+' |')
lines+=['','ID 27 không xuất hiện trong danh sách lựa chọn của kịch bản này. Một kịch bản khởi tạo '
        'làm danh sách 29 ID này bị lặp; báo cáo chính mô tả điều kiện tái hiện. '
        'Không khẳng định map hoạt động với tất cả nhân vật, số bot, độ khó, vũ khí hay điều kiện trận.','']
(OUT/'DANH_SACH_MAP_BOT.vi.md').write_text('\n'.join(lines),encoding='utf-8')
shutil.copyfile(SRC/'results.json',OUT/'ket_qua_map_bot.json')
print('Wrote',len(items),'map observations')
