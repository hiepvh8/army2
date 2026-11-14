# Chuyển `IOS_TuTien.jar` sang nhiều người: trạng thái thực tế

Mục tiêu là giữ engine, map, vũ khí, boss, nâng cấp và trải nghiệm của game gốc, cho nhiều người cùng chơi và để server quản lý dữ liệu. **Mục tiêu này chưa hoàn thành.** Đấu PvP/boss trong mục tạo phòng hiện vẫn là đấu trường thử nghiệm riêng, không phải trận game gốc. Không dùng bản thử để kết luận rằng người chơi đã bắn nhau hoặc cùng đánh boss trong gameplay gốc.

## Đã có trong bản hiện tại

- JAR mới giữ nguyên 1.192 class và toàn bộ tài nguyên của `IOS_TuTien.jar`; thêm giao diện đăng nhập, lobby và mục nhân vật. Bản JAR gốc không bị sửa.
- Server mới có đăng ký, đăng nhập, mật khẩu băm, phiên, phòng thử nghiệm và SQLite. Trận thử nghiệm diễn ra trên server và dữ liệu thưởng của nó độc lập với nhân vật gốc.
- Màn đấu thử nghiệm hiển thị map Cây cầu băng, nền, sprite và biểu tượng từ JAR gốc; chạm bản đồ nhỏ để chuyển camera. **Các khối băng chỉ là hình ảnh**: va chạm, phá địa hình và luật đạn gốc chưa được chuyển lên server.
- Mục **NHÂN VẬT GỐC** cho mở nhân vật đã tạo trong cùng bản JAR, lưu/tải bản nhân vật `AVP1` của game gốc theo tài khoản, và khôi phục dự phòng trên máy. Đây là lưu trữ thủ công, không phải đồng bộ liên tục.
- Máy chủ từ chối lưu khi phiên bản trên server đã thay đổi. Có nút **GHI ĐÈ SERVER** để người chơi chủ động chọn bản trên máy: bản server được lưu dự phòng trên thiết bị trước, và server vẫn kiểm tra phiên bản khi ghi.
- Kiểm thử MicroEmulator dùng hai phiên JAR độc lập, dữ liệu RMS trên đĩa, HTTPS với chứng chỉ thử nghiệm được tin cậy và nhiều lần khởi động lại để kiểm tra tải, phục hồi, giải quyết xung đột; đã đi vào boss offline và gọi bắn bằng engine gốc. Điều đó **không chứng minh multiplayer gốc** và chưa chứng minh hoạt động trên Manic EMU/iOS.

Ảnh từ lần kiểm thử: [menu nhân vật](screenshots/20-original-profile.png), [màn boss offline gốc](screenshots/21-original-boss-offline.png) và [lượt bắn offline](screenshots/22-original-shot-offline.png).

## Dùng nhân vật gốc trong bản thử nghiệm

1. Cài JAR thử nghiệm rồi chọn **CHƠI OFFLINE > CHƠI MỚI** để tạo nhân vật. Thoát hẳn rồi mở lại game.
2. Cấu hình **MÁY CHỦ** bằng URL HTTPS của server mới, sau đó đăng ký hoặc đăng nhập.
3. Chọn **NHÂN VẬT GỐC > VÀO GAME GỐC** để chơi offline nhân vật trong bản JAR này. Từ menu trong game quay lại menu chính để trở về lobby.
4. Chọn **LƯU LÊN SERVER** để tải nhân vật hiện tại lên tài khoản. Trên thiết bị khác, đăng nhập cùng server và tài khoản, chọn **TẢI TỪ SERVER**. Nếu máy đã có nhân vật, bản trên máy được dự phòng trước khi thay thế.
5. Sau khi tải hoặc phục hồi, **thoát hẳn game và mở lại**, rồi chọn **VÀO GAME GỐC**. Không tiếp tục chơi hoặc lưu trong cùng phiên ngay sau khi nhập dữ liệu: game gốc yêu cầu khởi động lại để tránh ghi đè dữ liệu cũ trong bộ nhớ.
6. **PHỤC HỒI** lấy bản dự phòng mới nhất và trước đó lại dự phòng nhân vật hiện tại, do đó có thể khôi phục ngược sau khi khởi động lại. Có tối đa tám bản dự phòng trong RMS; khi đầy phải kiểm tra và xóa bớt bản mới nhất trước khi nhập tiếp. **XÓA DỰ PHÒNG** chỉ xóa một bản mới nhất, không xóa tất cả.

Bản JAR mới có định danh MIDlet khác. Nhân vật đã tạo trong `IOS_TuTien.jar` cài trước đó **không tự xuất hiện** trong bản mới vì dữ liệu RMS thường tách theo ứng dụng; không xóa bản cũ. Chưa có luồng nhập tự động dữ liệu từ bản cài cũ của Manic EMU. Việc chuyển nhân vật cũ cần kiểm tra chức năng xuất/tệp của chính trình giả lập trên thiết bị thật.

Menu **CHƠI OFFLINE** vẫn giữ các nút đăng nhập/cloud của bản cũ. Các nút đó không dùng server mới; mã gốc cấu hình dịch vụ cũ bằng HTTP và từng lỗi kiểm chứng bytecode trên MicroEmulator. Dùng nút đăng nhập ở menu đầu của bản thử nghiệm cho tài khoản mới, không nhập mật khẩu mới vào các nút cloud cũ.

## Xung đột hai thiết bị

Nếu máy A lưu sau khi máy B đã thay đổi dữ liệu, server từ chối. **TẢI TỪ SERVER** giữ bản đang có trên A trong dự phòng rồi tải bản server. Nếu đã xác định muốn dùng bản trên A, chọn **GHI ĐÈ SERVER**: thao tác tải bản server vào dự phòng trên A rồi mới gửi bản A; nếu server lại thay đổi trong lúc đó, thao tác vẫn bị từ chối. Không xóa dự phòng trước khi đã xác nhận bản cần giữ.

## Dữ liệu nằm ở đâu

- Trên thiết bị: bản game đang chơi và dự phòng trong RMS của MIDlet; địa chỉ máy chủ, token đăng nhập và số phiên bản cũng lưu trong RMS. Đây là dữ liệu cục bộ của ứng dụng giả lập; không coi là kho bí mật được mã hóa.
- Server mới: bảng `users` và `sessions` lưu tài khoản/phiên; bảng `original_profiles` lưu nguyên gói `AVP1`, số phiên bản và SHA-256 theo người dùng trong SQLite. Với cấu hình EC2 có sẵn, file là `/var/lib/army2-online/army2.sqlite3`.
- Bản lưu `AVP1` vẫn là dữ liệu do máy chơi gửi lên. Kiểm tra cấu trúc và checksum phát hiện dữ liệu hỏng, **không** chứng minh chỉ số, tiền hay vật phẩm hợp lệ; chưa sử dụng bản này làm nguồn trạng thái thi đấu đáng tin cậy. Bản thử có xu/sự kiện của đấu trường riêng, không gộp với tiền/trang bị gốc.
- Job backup mặc định giữ bản SQLite trên cùng ổ EC2, không bảo vệ khỏi mất ổ; cần chép bản sao ra nơi khác. Server và HTTPS thật chưa được triển khai hoặc kiểm tra trên AWS trong repo này.

## Những phần còn phải xây để đạt mục tiêu

1. Tách và tái tạo state trận gốc: map, địa hình phá hủy, vị trí, lượt, gió, nhân vật, vật phẩm, trạng thái, sát thương và ngẫu nhiên. Hiện `OfflineCombat`, `OfflineBulletSim` và các lớp boss vận hành tại máy người chơi và tự điều khiển bot.
2. Nối các nút di chuyển, góc, lực, bắn, vật phẩm trong `GameScr`/`GameService` vào giao thức trận; đồng bộ kết quả về tất cả người chơi, xử lý thứ tự lệnh, timeout, mất mạng và kết thúc trận. Không dùng client tự báo thắng/sát thương làm kết quả tin cậy.
3. Chuyển và kiểm thử toàn bộ vũ khí, nhân vật, trang bị, AI và boss với dữ liệu gốc; xây sự kiện thực tế, quyền thưởng và chống ghi trùng. Cần so sánh từng trường hợp với game gốc, không suy ra tương đương từ việc giữ nguyên sprite.
4. Kiểm thử nhiều người thực sự trên phiên bản Manic EMU và iPhone của người dùng, HTTPS, tài nguyên EC2, sao lưu/khôi phục, nâng cấp và xử lý ngắt kết nối. Chưa có số tải hoặc bảo đảm chi phí Free Tier.

Nguồn server gốc và source gốc có thể build đầy đủ không có trong repo; chỉ có bytecode JAR và bản dịch ngược phục vụ phân tích. Vì vậy đây là một dự án chuyển đổi và kiểm thử đáng kể, không thể coi việc thêm đăng nhập, chuyển save hoặc giữ nguyên class là đã hoàn thành gameplay online.
