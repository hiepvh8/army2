#!/usr/bin/env python3
"""Copy current smoke-test images and document the new online flow."""
from pathlib import Path
import shutil
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/kiem-thu-chi-tiet';IMG=OUT/'anh-online';IMG.mkdir(parents=True,exist_ok=True)
shots=[('01-home','screen-audit/01-main.png'),('02-server','screen-audit/02-server.png'),
 ('03-invalid-server','screen-audit/03-server-invalid.png'),('04-login','screen-audit/04-login.png'),
 ('05-empty','screen-audit/05-empty.png'),('06-register','screen-audit/06-register.png'),
 ('07-name-error','screen-audit/06a-invalid-name.png'),('08-password-error','screen-audit/06b-short-password.png'),
 ('09-register-filled','screen-audit/07-register-filled.png'),('10-lobby','screen-audit/08-lobby.png'),
 ('11-event','screen-audit/09-event-not-ready.png'),('12-wrong-password','screen-audit/10-wrong-password.png'),
 ('13-login-success','screen-audit/11-login-success.png'),('14-duplicate','screen-audit/12-duplicate-account.png'),
 ('15-offline','screen-audit/13-offline-menu.png'),('16-room','duel/host-room.png'),
 ('17-room-error','duel/host-room-error.png'),('18-pvp','duel/host-battle.png'),
 ('19-camera','duel/host-camera.png'),('20-exchange','duel/host-exchange.png'),
 ('21-boss','boss/host-battle.png'),('22-boss-camera','boss/host-camera.png'),
 ('23-boss-result','boss/host-finished.png'),('24-profile','profile/01-profile-menu.png'),
 ('25-upload','profile/02-upload.png'),('26-download','profile/03-download.png'),
 ('27-restore','profile/04-restore.png')]
for name,source in shots:
    src=ROOT/'test-results'/source
    if not src.exists():raise SystemExit('Missing screenshot: '+str(src))
    shutil.copyfile(src,IMG/(name+'.png'))
text='''# Luồng online thử nghiệm: nút và màn hình

Ảnh được chụp trong MicroEmulator bằng tài khoản giả, hai JVM và server cục bộ. Đây là sân đấu thử nghiệm; xem [báo cáo chính](BAO_CAO.vi.md) để biết giới hạn gameplay và đồ họa.

| Đường đi / thao tác | Kết quả đã quan sát | Ảnh |
|---|---|---|
| Mở JAR | Menu chính có Đăng nhập, Đăng ký, Máy chủ, Chơi offline, Thoát | [Xem](anh-online/01-home.png) |
| Máy chủ | Mở ô URL; HTTP tới máy từ xa và URL giả dạng localhost bị từ chối | [URL](anh-online/02-server.png), [báo lỗi](anh-online/03-invalid-server.png) |
| Đăng nhập | Mở ô tài khoản/mật khẩu native; để trống và sai mật khẩu bị từ chối; đăng nhập đúng vào sảnh | [Màn hình](anh-online/04-login.png), [để trống](anh-online/05-empty.png), [sai mật khẩu](anh-online/12-wrong-password.png), [thành công](anh-online/13-login-success.png) |
| Đăng ký | Tạo tài khoản; tên ngắn, mật khẩu ngắn và tên trùng bị từ chối | [Màn hình](anh-online/06-register.png), [tên sai](anh-online/07-name-error.png), [mật khẩu ngắn](anh-online/08-password-error.png), [đã điền](anh-online/09-register-filled.png), [tên trùng](anh-online/14-duplicate.png) |
| Quay lại / Thoát | Đã thử quay lại từ đăng nhập, đăng ký, lưu máy chủ; nút thoát dừng phiên thử | [Menu](anh-online/01-home.png) |
| Sảnh | Tạo phòng PvP/boss, mở danh sách phòng, đăng xuất; chọn sự kiện khi chưa đủ điều kiện nhận thông báo | [Sảnh](anh-online/10-lobby.png), [sự kiện](anh-online/11-event.png) |
| Phòng → Sẵn sàng / Bắt đầu | Hai phiên cùng sẵn sàng, chủ phòng bắt đầu; bắt đầu khi chưa đủ người hiện lỗi | [Phòng](anh-online/16-room.png), [lỗi](anh-online/17-room-error.png) |
| PvP → góc, lực, di chuyển, bắn | Đã thử góc −/+, lực −/+, cả hai người lần lượt di chuyển một bước, bắn trúng nhau và đối chiếu HP; thao tác trái lượt không đổi trạng thái | [Trận](anh-online/18-pvp.png), [sau hai lượt](anh-online/20-exchange.png) |
| PvP/boss → bản đồ nhỏ | Chạm vị trí đối thủ hoặc boss để đổi góc nhìn | [Đối thủ](anh-online/19-camera.png), [boss](anh-online/22-boss-camera.png) |
| PvP → Bỏ lượt / Rời phòng | Đã thử callback bỏ lượt và rời phòng sau khi kết thúc trận; chưa khẳng định tương tác menu command trên Manic EMU | [Trận](anh-online/18-pvp.png) |
| Boss thử nghiệm | Hai phiên cùng bắn, thắng và nhận xu thử nghiệm | [Trận](anh-online/21-boss.png), [kết quả](anh-online/23-boss-result.png) |
| Nhân vật gốc | Vào menu, lưu lên server, tải về và phục hồi dự phòng sau khởi động lại; có thử xung đột phiên bản | [Menu](anh-online/24-profile.png), [lưu](anh-online/25-upload.png), [tải](anh-online/26-download.png), [phục hồi](anh-online/27-restore.png) |
| Chơi offline | Mở menu offline gốc; xem [252 thao tác menu gốc](DANH_SACH_MAN_HINH.vi.md) | [Menu](anh-online/15-offline.png) |

Sau khi vào game, các thao tác trong bảng được kiểm thử bằng chạm/ấn phím mô phỏng hoặc gọi callback của nút tùy trường hợp. Chưa thử mọi nút, mọi trạng thái, mất mạng trên iPhone, quyền mạng Manic EMU, server AWS thực hay nhiều người trong gameplay gốc.
'''
(OUT/'LUONG_ONLINE.vi.md').write_text(text,encoding='utf-8')
print('Wrote',len(shots),'online screenshots')
