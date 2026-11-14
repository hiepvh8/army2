# Báo cáo kiểm thử JAR thử nghiệm

Bản kiểm thử: `ARMY2_Online_IOS.jar` SHA-256 `d2ba672808a502ec72080aaafa782875665e7a73aa66580907a22475ababec21`. Giả lập MicroEmulator 2.0.4 trên Linux, server Python/SQLite cục bộ, hai JVM độc lập. Đây không phải Manic EMU hay iPhone và không phải server AWS.

## Kết quả theo màn hình

| Màn/nút | Cách thử và kết quả |
|---|---|
| Menu đầu → Máy chủ | Đã mở, thử từ chối URL HTTP từ xa và URL giả dạng loopback, lưu URL cục bộ và quay lại. |
| Menu đầu → Đăng ký | Đã mở các ô nhập native; thử tên sai, mật khẩu ngắn, tạo tài khoản thành công và tên trùng bị từ chối. |
| Menu đầu → Đăng nhập | Đã thử để trống, sai mật khẩu, đăng nhập thành công, đăng xuất và mở lại phiên. |
| Sảnh → Sự kiện | Đã thử khi chưa đủ điều kiện bị từ chối. Backend thử thắng boss hai lần, nhận thưởng và từ chối nhận lặp. Sự kiện này là thử nghiệm, chưa phải sự kiện gốc. |
| Sảnh → Nhân vật gốc | Hai phiên JVM dùng cùng tài khoản truyền nhân vật qua HTTPS; thử dự phòng, phục hồi sau khởi động lại, xung đột và chủ động ghi đè. |
| Sảnh → PvP thử nghiệm | Hai JVM đăng ký, tạo/vào phòng, sẵn sàng, bắt đầu, đánh đến kết quả, rời phòng. Thử nút chỉnh góc −/+, lực −/+, di chuyển sang phải, bắn và bỏ lượt; trạng thái/HP được hai phiên theo dõi từ server. Đây là sân đấu riêng. |
| Sảnh → Boss thử nghiệm | Hai JVM vào phòng, sẵn sàng, cùng bắn, hoàn tất trận và nhận xu thử nghiệm. Không phải boss gốc. |
| Menu đầu → Chơi offline | Đã mở menu offline gốc. Các nút cloud cũ có lỗi VerifyError khi thử JAR gốc bằng MicroEmulator; xem báo cáo kiểm thử JAR gốc. |
| Nhân vật gốc → Vào game gốc → Boss | Đã vào màn lựa chọn boss, chọn độ khó, vào phòng chuẩn bị và trận; gọi hàm bắn của nhân vật. Đây là offline. |
| Game gốc → PvP bot | Mở màn cấu hình, chọn map mặc định và một bot, vào chuẩn bị, bắt đầu; gọi hàm bắn của nhân vật, quan sát bot nhận lượt và cờ bắn của bot. Ảnh: [cấu hình](01-bot-setup.png), [phòng](02-bot-prepare.png), [trận](03-bot-battle.png), [bot trong lượt](05-bot-turn.png). Bài thử gọi hàm bắn trực tiếp, chưa chứng minh mọi nút chạm trên iPhone hoạt động. |

Ảnh đấu trường thử nghiệm: [PvP](07-pvp-online-test.png), [boss](08-boss-online-test.png). Xem thêm [kiểm thử đăng nhập và đường đi màn hình](../KIEM_THU_MAN_HINH.vi.md) và [kiểm thử JAR gốc](../kiem-thu-jar-goc/BAO_CAO.vi.md).

## Phát hiện

- Trong kịch bản tạo nhân vật rồi gọi các hàm mở menu và cấu hình PvP bot, danh sách map xuất hiện lặp: có 145 mục map với 29 ID riêng (0–26, 28, 29), cộng mục ngẫu nhiên. Chưa xác minh mọi thao tác người dùng bình thường có tái hiện giống vậy; chưa sửa trong lần kiểm thử này.
- Hai người bắn nhau **có hoạt động trong sân PvP thử nghiệm**, nhưng chưa có PvP nhiều người trong engine gốc. Các bài thử không chứng minh tính năng đó đã hoàn thành.
- Bộ kiểm thử bot đã thấy bot phát lệnh bắn; chưa xác nhận đạn trúng, trừ HP, kết thúc trận hoặc phần thưởng bot trong lần này.
- Nút di chuyển sang phải được thử cho người tạo phòng trong PvP thử nghiệm. Chưa xác nhận đầy đủ trái/phải trên mọi máy, cả hai chiều, điều khiển cảm ứng trên iPhone, hoặc di chuyển trong mọi map gốc.

## Giới hạn kiểm thử

Không khẳng định đã thử tất cả nút, map, nhân vật, vũ khí, nâng cấp, vật phẩm, boss, sự kiện hoặc kết quả trận. Chưa thử trên Manic EMU/iOS, mạng Internet thực, AWS, tải nhiều người, phục hồi trận khi server khởi động lại hay một trận multiplayer bằng gameplay gốc. Các ảnh được chụp từ dữ liệu và tài khoản thử nghiệm.

## Lệnh chạy lại

```bash
cd online
python3 -m unittest discover -s tests -v
python3 tools/test_emulator.py
python3 tools/audit_screens.py
python3 tools/test_profiles_emulator.py
python3 tools/audit_bot.py
```

25 kiểm thử backend đã qua. Các bộ giả lập nêu trên đã qua trong lần chạy báo cáo; bot được quan sát `BOT_TURN_INDEX=1`, `BOT_SHOT_OBSERVED=true`. File log đầy đủ của lần chạy được tạo trong `test-results/` và không được commit vì là kết quả tạm.
