# ARMY2 Online — JAR cho Manic EMU, backend một EC2

Đây là **bản thử nghiệm online v0.1**, bổ sung lên `IOS_TuTien.jar` theo cách 1. Giữ nguyên 1.192 class gốc và tài nguyên; thêm launcher, màn hình đấu online và giao thức riêng. Không phải bản chuyển toàn bộ các vũ khí, map, tu tiên và sự kiện offline lên server.

**Chưa hoàn thành mục tiêu multiplayer giữ gameplay gốc.** Phòng PvP/boss hiện chạy đấu trường thử nghiệm riêng. Nhân vật gốc có thể được lưu và khôi phục theo tài khoản, nhưng chưa thể bắn nhau hoặc đánh boss cùng nhau trong engine gốc. Xem [trạng thái, cách dùng và các phần còn thiếu](docs/GAME_GOC_ONLINE.vi.md).

## Có gì trong bản này

- JAR CLDC 1.1/MIDP 2.0, bytecode Java 1.4, có bước preverification.
- Tài khoản online riêng; mật khẩu băm PBKDF2, phiên đăng nhập lưu RMS.
- Nhân vật gốc có thể lưu/tải thủ công theo tài khoản; server kiểm tra phiên bản và thiết bị dự phòng trước khi thay nhân vật.
- Lobby, tạo/vào phòng, sẵn sàng, chủ phòng bắt đầu.
- PvP 1v1; boss phối hợp 2–4 người. Server quản lý lượt, vị trí, HP, đường đạn, sát thương, AI, kết quả và thưởng.
- Mỗi lượt 30 giây, di chuyển tối đa một lần; lệnh cũ/sai lượt bị từ chối.
- Mất mạng: vào lại cùng tài khoản để lấy trạng thái hiện tại. Sau 90 giây không còn người đang sống kết nối, trận kết thúc không thưởng. Server restart làm mất phòng đang chơi; tài khoản/tiền/thưởng đã chốt vẫn còn.
- Sự kiện ngày: thắng boss 2 lần, nhận 200 xu một lần/ngày (ngày theo Asia/Bangkok). Mỗi trận boss thắng nhận 100 xu/người; PvP thắng nhận 50 xu. Bỏ cuộc không sinh tiền.
- Giữ lối vào game offline gốc trong launcher. Suite ID khác nên save offline cũ không tự chuyển sang suite mới; không xóa dữ liệu của JAR cũ.

## Chạy local

Để mở cửa sổ giả lập Java ME và server local cùng lúc, mở terminal trong Desktop của môi trường rồi chạy:

```bash
cd /workspace/army2/online
python3 tools/open_emulator.py
```

Giữ terminal mở trong khi thử. Lệnh tạo server khi cần và chỉ dừng server do chính lệnh này tạo khi đóng giả lập. Chạy `python3 tools/build_client.py` trước nếu vừa sửa mã client. Có thể kiểm tra các thành phần không cần màn hình bằng `python3 tools/open_emulator.py --check`. Đây là MicroEmulator Java ME trên Linux, không phải thiết bị iOS hay Manic EMU; cần kiểm tra thêm trên iPhone để xác nhận tương thích.

Cần Python 3.10+ và Java runtime 17+ để build/test; bài kiểm thử HTTPS dùng thêm `openssl` và `keytool` (có trong JRE hiện tại). Backend chỉ dùng thư viện chuẩn Python, không cần Docker, pip, Redis hoặc PostgreSQL cho bản test một máy.

Từ thư mục `online`:

```bash
python3 tools/build_client.py
python3 -m unittest discover -s tests -v
python3 -m server.main --host 127.0.0.1 --port 8080 --db data/army2.sqlite3
```

Kết quả: `dist/ARMY2_Online_IOS.jar` và `.jad`. Tool build xác minh SHA-256 các dependency, xác minh JAR gốc và kiểm tra các class gốc không bị thay đổi. Lần đầu cần truy cập Maven Central. Không đóng gói thư viện Java ME của emulator vào JAR.

Kiểm thử hai MIDlet thật bằng MicroEmulator 2.0.4 (JAR dependency được pin SHA-256, khởi chạy hai JVM độc lập):

```bash
python3 tools/test_emulator.py
python3 tools/test_profiles_emulator.py
```

Script chạy cả PvP và boss, điều khiển form đăng ký/nút bắn của JAR, chạy PvP qua HTTP loopback và boss qua HTTPS với CA thử nghiệm được tin cậy tạm thời, đọc kết quả và lưu ảnh trong `test-results/`. Emulator dùng để kiểm thử là MicroEmulator; **không thay thế việc kiểm thử Manic EMU trên iOS**.

## Đưa vào Manic EMU trên iPhone/iPad

1. Triển khai backend HTTPS theo `docs/EC2.vi.md`.
2. Nhập `ARMY2_Online_IOS.jar` vào Manic EMU, bật quyền mạng nếu ứng dụng yêu cầu.
3. Trong menu game chọn **MÁY CHỦ**, nhập `https://game.tenmiencuaban.com` (không thêm `/jme`) rồi chọn **Lưu**. Lưu địa chỉ mới sẽ xóa phiên online cũ để tránh gửi token sang server khác.
4. Chọn **ĐĂNG KÝ** hoặc **ĐĂNG NHẬP**, nhập tên 3–16 ký tự ASCII, bắt đầu bằng chữ; mật khẩu 10–128 ký tự. Chạm ô nhập để mở bàn phím. Đây là tài khoản của backend mới, không phải tài khoản AnhVu Cloud.
5. Máy A tạo phòng PvP hoặc boss; máy B vào phòng. Tất cả chọn **Sẵn sàng**, chủ phòng chọn **Bắt đầu**.
6. Chạm **Góc -/+**, **Lực -/+**, **BẮN**. Phím 4/6 chỉnh góc; 2/8 chỉnh lực; 5 bắn; 1/3 di chuyển. Góc 45 bắn sang phải, 135 sang trái.
7. Sau trận chọn **Rời phòng** để tạo trận mới. Mục sự kiện trong lobby cho nhận thưởng khi đủ điều kiện.

`127.0.0.1` trên iPhone là iPhone, không phải EC2. Địa chỉ loopback mặc định chỉ dùng kiểm thử trên cùng máy. Client chỉ cho HTTP ở loopback; kết nối từ xa dùng HTTPS có chứng chỉ hợp lệ. Không tắt kiểm tra TLS để vượt lỗi chứng chỉ.

Có thể build sẵn địa chỉ server vào manifest:

```bash
python3 tools/build_client.py --server https://game.tenmiencuaban.com
python3 tools/package_release.py
```

## Manic EMU và hỗ trợ mạng

Đã kiểm tra source công khai ManicEMU ở commit `5840a80d333753c22038420da5c111a7fa9103a4`: `J2MENetworkBridge.swift` có HTTP POST, headers, body và TCP; bridge dùng cho core J2meJS. `J2MEView.swift` còn ghi nhận Freej2meWeb có xử lý mạng riêng. Đây là bằng chứng kiến trúc, không xác nhận phiên bản app trên thiết bị của bạn. Nếu app cho chọn core, ưu tiên thử **J2meJS** trước cho bản online này. FreeJ2ME-Plus bản desktop đã thử có HTTP stub nên không dùng để kết luận client online hoạt động; bộ kiểm thử đã chuyển sang MicroEmulator có HTTP thực.

## Kiến trúc và giới hạn

- `server/game.py`: luật online v1, phòng/trận và phần thưởng. Dữ liệu tài khoản/phiên/thưởng lưu SQLite WAL. Trạng thái trận trong RAM. Mỗi lệnh được xử lý dưới khóa toàn ứng dụng, phù hợp thử nghiệm ít người trên một process.
- `server/main.py`: HTTP POST, JSON `/api` để test và binary `/jme` cho JAR; `/profile/load`, `/profile/save` bảo vệ bằng phiên; `/health`. Endpoint JSON chỉ được phục vụ loopback trong cấu hình nginx mặc định.
- `server/profiles.py`: xác thực container AVP1, lưu dữ liệu gốc theo tài khoản với số phiên bản. Đây là dữ liệu do máy chơi cung cấp, chưa phải nhân vật/chỉ số đáng tin để tính kết quả online.
- `client/src/army2/online`: Java ME bổ sung. Poll trạng thái khoảng 0,9 giây/lần; mọi lệnh chiến đấu mang sequence. Không tự retry lệnh bắn sau lỗi mạng: poll sẽ xác nhận trạng thái server.
- Tất cả người chơi online bắt đầu cùng HP/vũ khí để kiểm thử công bằng; không nhận tiền/chỉ số/trang bị từ save cũ. Xu online hiện là tiến trình thử nghiệm, chưa có shop hoặc giao dịch.
- Vật lý online v1 hiện là một đạn parabol, bản đồ sàn phẳng, không gió/phá địa hình. Không phải bản port chính xác `OfflineBulletSim`. Boss là một AI đơn giản, dùng sprite gốc; chưa port toàn bộ boss/kỹ năng cũ.
- Chưa có chat, xếp hạng, bang hội, matchmaking tự động, chống nhiều tài khoản, khôi phục trận qua server restart hoặc công cụ admin web. Sự kiện hiện là nhiệm vụ ngày cố định; chưa phải hệ thống sự kiện tùy chỉnh đầy đủ.
- Tối đa 64 phòng, 4 người/phòng; đây là giới hạn bảo vệ tài nguyên, **không phải số tải đã đo**. Chưa kiểm thử tải đông người và chưa kiểm thử trực tiếp trên Manic EMU.

## Trước khi mở cho người dùng thật

Kiểm thử Manic EMU/iOS và TLS thực tế; port vũ khí/map mong muốn; bổ sung khôi phục trận nếu cần; chống gian lận theo nhiều tài khoản; công cụ quản trị, quản lý tài khoản và backup ngoài EC2. Không coi server thử nghiệm là dịch vụ production hoặc cam kết chi phí AWS bằng 0.
