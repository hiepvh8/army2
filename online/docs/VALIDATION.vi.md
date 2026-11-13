# Kết quả xác minh — 13/11/2026

## Đã chạy thành công trong môi trường phát triển

1. `python3 tools/build_client.py`: biên dịch Java nguồn 1.3, bytecode 1.4/major 48, ProGuard CLDC preverification. Chín class mới có stack map cần thiết; toàn bộ 1.192 class gốc giữ nguyên byte. Hai lần build cùng source/endpoint tạo JAR có SHA-256 giống nhau. Checksum cụ thể nằm trong `dist/build.json`.
2. `python3 -m unittest discover -s tests -v`: **13 test, tất cả PASS**. Kiểm tra HTTP thật: đăng nhập, từ chối phiên giả/mật khẩu sai, quyền chủ phòng/sẵn sàng, lượt và sequence, giới hạn di chuyển/phòng, PvP, boss phối hợp, nhận thưởng sự kiện đồng thời nhưng chỉ một lần, lưu tài khoản qua mở lại DB, logout thu hồi phiên, timeout và không sinh tiền khi bỏ cuộc.
3. `python3 tools/test_emulator.py`: **PASS**. Chạy JAR được đóng gói bằng MicroEmulator 2.0.4 trong các JVM độc lập; không mock HTTP và không thay lớp mạng của emulator. Đăng ký qua form của MIDlet, bắn bằng handler bàn phím/chạm của Canvas, nhận snapshot binary thật:
   - PvP: người thắng 50 xu, người còn lại 0 xu.
   - Boss: boss về 0 HP, mỗi người nhận 100 xu.
   - Boss dùng HTTPS với chứng chỉ thử nghiệm được trust trong keystore tạm; hostname và chứng chỉ vẫn được xác minh. Đây không phải kiểm thử chứng chỉ/domain AWS.
   - Lối vào game gốc mở `coreLG.CCanvas` và hiển thị menu đăng nhập/đăng ký/chơi tiếp/chơi mới. Chưa kiểm thử trọn một trận offline. MicroEmulator headless phát cảnh báo font/audio không được hỗ trợ; không khẳng định âm thanh offline hoạt động.
4. Khởi động thực tế `python3 -m server.main --host 127.0.0.1 --port 8080 --db data/army2.sqlite3`; `/health` trả protocol 1/status ok; `/jme` trả lỗi xác thực đúng định dạng khi không đăng nhập.
5. `bash -n deploy/bootstrap.sh deploy/backup.sh` và Python compile check: PASS. Release tar chỉ chứa server, cấu hình triển khai, tài liệu và JAR/JAD; không có database, cache tool hoặc credential.

Ảnh và log của mỗi lượt emulator nằm trong `test-results/pvp`, `test-results/boss`, `test-results/offline` (ignored). Test dùng tài khoản và DB tạm, không chép dữ liệu thử nghiệm vào DB của server đang chạy.

## Chưa được xác minh

- Manic EMU trên iPhone/iPad thật, quyền mạng và TLS của phiên bản app đang cài.
- Triển khai lên EC2 thật: APT, nginx, systemd, DNS và Let's Encrypt. Kiểm tra systemd cục bộ không chạy được do sandbox không cho tạo `/run/systemd`; không coi đó là bằng chứng unit đã được triển khai hay bị lỗi cấu hình.
- Tải nhiều người dùng, phục hồi sau mất EC2/volume và giới hạn chi phí AWS.
- Toàn bộ nội dung game offline: bộ vũ khí, map, phá địa hình, trang bị, tu tiên, các boss và hệ thống sự kiện cũ chưa được port đầy đủ sang online.

## Bằng chứng khả năng mạng của Manic EMU

Đã đọc source công khai tại commit `5840a80d333753c22038420da5c111a7fa9103a4`:

- `ManicEmu/ManicEmu/Sources/Business/Common/Models/J2MENetworkBridge.swift`: nhận method/headers/body, gửi HTTP bằng URLSession và có TCP bridge.
- `ManicEmu/ManicEmu/Sources/Business/Common/Views/J2MEView.swift`: J2meJS dùng bridge trên; Freej2meWeb có xử lý mạng riêng.

Source công khai là cơ sở chọn giao thức, không chứng minh phiên bản ứng dụng trên thiết bị người dùng có cùng hành vi. Không dùng FreeJ2ME-Plus HTTP stub để tạo kết quả “online đã chạy”.
