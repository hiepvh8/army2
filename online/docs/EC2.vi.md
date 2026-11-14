# Triển khai trên một EC2

Mục tiêu: **một máy Linux, một process Python + nginx + SQLite**, không RDS/Redis/load balancer/Docker. Build JAR ở máy phát triển, không cần Java trên EC2. Ưu tiên Ubuntu 24.04 LTS và máy có khoảng 1 GB RAM. Chọn loại EC2/AMI phù hợp quyền lợi Free Tier thực tế của tài khoản; điều kiện Free Tier, IPv4, EBS và băng thông có thể phát sinh phí. Không có thao tác tạo tài nguyên AWS tự động trong repo.

## Chuẩn bị

- Một EC2 với địa chỉ public, volume đủ cho hệ điều hành/code/backup (ví dụ 8–12 GB).
- Một domain/subdomain do bạn quản lý, bản ghi A trỏ đến IP EC2. Nếu có AAAA thì nó cũng phải trỏ đúng máy; nếu không dùng IPv6, không tạo AAAA.
- Security Group: TCP 22 chỉ từ IP quản trị; TCP 80 và 443 cho người chơi. **Không mở 8080**.
- Quyền SSH dùng qua file key cục bộ hoặc cơ chế AWS được hỗ trợ. Không chép key/token vào repo hoặc chat.

Domain và chứng chỉ HTTPS hợp lệ là prerequisite cho luồng từ xa được hỗ trợ. Repo không tự mua tên miền hay dùng chứng chỉ tự ký để giả vờ đã xác minh iOS.

## 1. Build và đóng gói ở máy phát triển

Từ thư mục `online`:

```bash
python3 tools/build_client.py --server https://game.example.com
python3 -m unittest discover -s tests -v
python3 tools/package_release.py
```

Thay `game.example.com` bằng tên miền thật. Nếu bạn đã kiểm thử emulator trước và không sửa code, không cần lặp lại sau khi chỉ thay endpoint manifest.

## 2. Chuyển release lên EC2

Ví dụ, dùng SSH profile đã cấu hình tên `army2-test`:

```bash
scp dist/army2-online-release.tar.gz army2-test:/tmp/
ssh army2-test
```

Trên EC2:

```bash
mkdir -p /tmp/army2-release
tar -xzf /tmp/army2-online-release.tar.gz -C /tmp/army2-release
cd /tmp/army2-release/army2-online
sudo bash deploy/bootstrap.sh game.example.com admin@example.com
```

Email dùng cho Let's Encrypt. Script cài Python/nginx/certbot từ APT, user dịch vụ riêng, systemd, nginx, chứng chỉ và backup mỗi ngày. Dữ liệu giữ ở `/var/lib/army2-online/army2.sqlite3`; nâng cấp không ghi đè database. Thư mục `/var/www/army2-client` chỉ chứa JAR/JAD được build.

Tài khoản và bản lưu nhân vật gốc của chức năng đồng bộ thủ công cũng nằm trong SQLite này. Endpoint `/profile/save` được giới hạn 3 MiB và yêu cầu phiên; reverse proxy chỉ cho HTTPS đối với kết nối từ ngoài. Khi nâng cấp từ cấu hình do script cũ tạo, script thêm route vào site hiện có và giữ cấu hình chứng chỉ; sao lưu DB trước nâng cấp. Các bản lưu do client tạo chưa được server xác thực tính hợp lệ của chỉ số và không biến trận thử nghiệm thành gameplay gốc.

## 3. Kiểm tra trước khi chơi

Trên EC2:

```bash
sudo systemctl status army2-online --no-pager
curl --fail http://127.0.0.1:8080/health
curl --fail https://game.example.com/health
sudo journalctl -u army2-online -n 50 --no-pager
sudo systemctl list-timers army2-backup.timer
sudo certbot certificates
```

Từ một máy bên ngoài EC2, thử lại `https://game.example.com/health`. Phải nhận `{"status":"ok","protocol":1}` và TLS hợp lệ. Sau đó tải `https://game.example.com/client/ARMY2_Online_IOS.jar` về Files và nhập vào Manic EMU. Không dùng link localhost làm link chơi trên điện thoại.

Kiểm thử với hai tài khoản trên hai phiên/thiết bị: đăng ký, vào cùng phòng, sẵn sàng, hoàn tất PvP, hoàn tất boss và nhận thưởng; tắt/mở mạng, mở lại app và kiểm tra tiến trình. Chưa đạt các bước này thì chưa kết luận bản Manic EMU chạy ổn.

## Vận hành và nâng cấp

```bash
sudo systemctl restart army2-online
sudo /opt/army2-online/backup.sh
sudo journalctl -u army2-online -f
```

Restart kết thúc các phòng/trận trong RAM; tài khoản và thưởng đã commit được giữ. Thông báo cho người thử trước khi nâng cấp. Giữ bản release cũ để rollback code; backup DB trước mọi thay đổi schema. Muốn thay domain cần cập nhật cấu hình nginx/chứng chỉ và URL trong client, không ghi đè cấu hình domain cũ một cách tự động.

Backup bảy bản gần nhất trong `/var/backups/army2-online`; đây vẫn là cùng ổ EC2, chưa bảo vệ khỏi mất volume. Chép bản backup ra nơi lưu riêng trước khi xóa EC2. Khi restore, dừng dịch vụ trước, dùng SQLite backup API để thay database, giữ ownership `army2-online`, rồi khởi động và kiểm tra tài khoản/tiền. Không phục hồi bằng cách chép DB đang chạy và bỏ qua WAL.

## Phần cần thông tin thực tế để triển khai từ chat

Repo đã chuẩn bị luồng triển khai; để chạy trên AWS thật còn cần EC2 reachable, SSH/access binding hợp lệ, domain và DNS. Không thể xác nhận cài nginx/systemd/cấp chứng chỉ từ kiểm thử local. Không nhập access key hoặc SSH private key vào chat; cung cấp quyền qua cơ chế an toàn của môi trường.
