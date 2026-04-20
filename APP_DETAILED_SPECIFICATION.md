# 📱 ĐẶC TẢ CHI TIẾT TOÀN DIỆN: ỨNG DỤNG DI ĐỘNG (ANDROID APP)

Tài liệu này tập trung duy nhất vào giải pháp phần mềm trên thiết bị di động, liệt kê chi tiết mọi khía cạnh từ giao diện (UI) đến trải nghiệm người dùng (UX) và các logic chức năng siêu nhỏ.

---

## 🎨 1. HỆ THỐNG THIẾT KẾ (DESIGN SYSTEM & UI/UX)

### 1.1. Phong cách Glassmorphism & Soft UI
Ứng dụng sử dụng ngôn ngữ thiết kế hiện đại với các đặc điểm:
- **Card View:** Độ mờ hạt (Blur) nhẹ, nền trắng mờ (#CCFFFFFF), bo góc lớn (20dp - 24dp).
- **Shadow:** Sử dụng Elevation nhẹ (2dp - 4dp) để tạo chiều sâu nhưng không gây nặng nề.
- **Biểu tượng (Icons):** Bộ icon Medical phẳng, tùy chỉnh màu sắc theo trạng thái (ic_heart_rate, ic_spo2, ic_temperature...).

### 1.2. Bảng màu trạng thái (Semantic Colors)
Thiết kế màu sắc tuân thủ nghiêm ngặt tiêu chuẩn Y tế:
- **Xanh lá (#4CAF50):** Ổn định (Normal) - Áp dụng cho BPM 60-100, SpO2 > 95%.
- **Vàng/Cam (#FF9800):** Cảnh báo (Warning) - Áp dụng cho sốt nhẹ hoặc bụi mịn cao.
- **Đỏ (#F44336):** Nguy hiểm (Critical) - Áp dụng cho Té ngã, SOS, SpO2 < 90%.
- **Xám (#9E9E9E):** Ngoại tuyến (Offline) - Khi thiết bị mất kết nối > 60 giây.

---

## 🧭 2. KIẾN TRÚC ĐIỀU HƯỚNG (NAVIGATION & FLOW)

Hệ thống sử dụng **Jetpack Navigation Component** với sơ đồ:
- **Màn hình chính (Bottom Navigation):**
  - `navigation_home`: Giám sát tổng thể.
  - `navigation_dashboard`: Bản đồ cứu hộ.
  - `navigation_chat`: Trợ lý AI.
  - `navigation_notifications`: Nhật ký cảnh báo.
  - `navigation_account`: Quản lý cá nhân.
- **Màn hình phụ (Deep Links):**
  - `navigation_metric_detail`: Phân tích sâu 1 chỉ số (BPM/SpO2...).
  - `navigation_otp`: Xác thực số điện thoại.
  - `navigation_notification_detail`: Xem chi tiết 1 sự kiện ngã/SOS.

---

## 🛠 3. CHI TIẾT CHỨC NĂNG TỪNG MÀN HÌNH

### 3.1. HomeFragment - Trung tâm giám sát Thời gian thực
Đây là màn hình quan trọng nhất nơi người dùng xem trạng thái người thân:
- **Widget Trạng thái (Overall Status):**
  - Tự động thay đổi Title và Subtitle dựa trên dữ liệu Firebase.
  - **Logic Offline:** Tính toán (CurrentTime - DeviceTime). Nếu > 60s, UI chuyển xám và báo "Thiết bị ngoại tuyến".
- **Lưới chỉ số (Metric Grid):** 4 thẻ tương tác cao.
  - **Hoạt họa Nhịp tim:** Trái tim đập theo nhịp thực tế gửi về.
  - **Trạng thái Đo:** Hiển thị "Đang đo...", "Chờ đo", "Hoàn tất" dựa trên phản hồi từ cảm biến MAX30102.
- **Bản đồ rút gọn (Mini Map):** 
  - Hiển thị MapView Google Maps thu nhỏ.
  - Tự động Pin vị trí cuối cùng của thiết bị.
  - Nút "Xem bản đồ" để nhảy nhanh sang trang Dashboard.
- **Nút Task khẩn cấp (`btn_cancel_alert`):**
  - Chỉ xuất hiện khi có cảnh báo Ngã hoặc SOS.
  - Gửi lệnh `Cmd_CancelAlert = true` về ESP32 để tắt còi hú từ xa.

### 3.2. DashboardFragment - Bản đồ cứu hộ & Bệnh viện
- **Medical Map Style:** Sử dụng file JSON tùy chỉnh để lọc bỏ các POI không liên quan, làm nổi bật đường xá và trạm y tế.
- **Overpass API Integration:**
  - Nút "Bệnh viện gần nhất": Tự động quét bán kính 5km xung quanh người thân.
  - Hiển thị Marker chi tiết: Tên bệnh viện, loại hình (Công/Tư), khoảng cách.
- **Share Location:** Tạo thông điệp mẫu kèm Link vị trí chuẩn để chia sẻ qua Zalo/Phone chỉ với 1 chạm.

### 3.3. ChatFragment - Trợ lý bác sĩ ảo AI Gemini
- **Giao diện hội thoại nâng cao:**
  - Có bong bóng chat cho Người dùng (Sent) và AI (Received).
  - Hỗ trợ định dạng hội thoại Markdown (In đậm chỉ số, in nghiêng lưu ý).
- **Gợi ý hành động:** Các nút "🏥 Kiểm tra sức khỏe", "❤️ Giải thích chỉ số" giúp người dùng tương tác với AI không cần gõ phím.
- **Đầu vào đa phương thức:**
  - **Voice Input:** Ghi âm giọng nói, xử lý sóng âm (Waveform animation).
  - **Attachments:** Gửi kèm tệp, ảnh bệnh án hoặc chụp ảnh trực tiếp từ Camera để AI phân tích.
- **System Prompt (Internal):** Cấu hình AI đóng vai bác sĩ chuyên nghiệp "Healthy 365", có quy tắc cảnh báo khẩn cấp khi thấy SpO2 < 90%.

### 3.4. MetricDetailFragment - Phân tích & Báo cáo PDF
- **Real-time Waveform Chart:** Biểu đồ sóng chạy liên tục mô phỏng máy đo ECG/SpO2 chuyên dụng.
- **History LineChart:**
  - Chế độ Zoom/Pan dữ liệu 7 ngày.
  - Custom Marker View: Hiện chi tiết giá trị và giờ đo khi chạm vào điểm bất kỳ.
- **Distribution PieChart:** Thống kê tỉ lệ % thời gian người dùng ở mức "Bình thường", "Cảnh báo" hay "Nguy hiểm".
- **PDF Export Logic:**
  - Tạo file PDF lưu trong bộ nhớ Cache.
  - Vẽ Canvas chuyên nghiệp với: Tiêu đề in đậm, bảng thống kê thông số cao nhất/thấp nhất, ngày giờ xuất báo cáo.
  - Tích hợp ShareIntent để gửi báo cáo cho Bác sĩ ngay lập tức.

### 3.5. NotificationsFragment - Nhật ký cảnh báo thông minh
- **Hệ thống Chip Filter:**
  - Cho phép người dùng lọc nhanh các loại thông báo: `Tất cả`, `Nhịp tim`, `SpO2`, `Thân nhiệt`, `Bụi mịn`, `Té ngã`.
  - Chip sử dụng style `Widget.MaterialComponents.Chip.Choice` hỗ trợ Single-Selection.
- **Tính năng quản lý:**
  - `tv_clear_all`: Xóa toàn bộ lịch sử thông báo khỏi Local/Firebase.
  - `tv_mark_read_all`: Đánh dấu đã đọc tất cả để tắt các chấm đỏ báo hiệu.
- **Item Notification (`item_notification.xml`):**
  - Hiển thị Icon tương ứng với loại cảnh báo.
  - `view_unread_dot`: Chấm đỏ nhỏ ở góc icon báo hiệu thông báo chưa được mở.
  - Tự động rút gọn nội dung dài bằng `ellipsize="end"`.

### 3.6. AccountFragment & Security
- **Quản lý thiết bị:** Quét mã QR từ màn hình ESP32 để Pairing ID thiết bị tự động.
- **Profile:** Chỉnh sửa thông tin cá nhân (Họ tên, ngày sinh, giới tính).
- **Xác thực OTP (Phone Auth):** Sử dụng `OtpFragment` để xác thực số điện thoại người giám sát.
- **Quyền riêng tư:** Chế độ ẩn/hiện Email/SĐT bằng nút `ic_visibility`.

---

## 🔍 4. DANH MỤC THÔNG SỐ KỸ THUẬT (TECHNICAL VIEW MANIFEST)

Để đảm bảo không sót "bất kỳ chi tiết nhỏ nào", dưới đây là bảng ánh xạ các ID giao diện chính và logic đi kèm:

| Màn hình | View ID | Chức năng chi tiết (Micro-Logic) |
|---|---|---|
| **Home** | `tv_home_overall_status` | Đổi màu Text (Green/Red) dựa trên logic `isEmergency`. |
| | `iv_status_icon` | Thay đổi drawable (Check/Warning/Error) tương ứng. |
| | `card_heart_rate` | Click để điều hướng sang `navigation_metric_detail` kèm tham số `hr`. |
| | `btn_cancel_alert` | Gửi chuỗi JSON `{"cmd":"cancel_alert"}` lên node `Control` Firebase. |
| **Dashboard** | `mapView` | Tích hợp Google Maps SDK, mode `MEDICAL`. |
| | `fab_my_location` | Tìm tọa độ hiện tại của điện thoại (không phải thiết bị). |
| | `fab_search_hospital` | Kích hoạt `searchNearbyHospitals()` gọi Overpass API. |
| **Chat** | `et_message_input` | Tự động mở rộng tối đa 6 dòng (Multi-line). |
| | `layout_voice_recording` | Overlay hiện ra khi nhấn giữ `btn_mic`, kèm Visualizer sóng âm. |
| | `btn_suggest_health` | Tự động điền text "Kiểm tra sức khỏe" và gửi ngay. |
| **Detail** | `line_chart_metric` | Sử dụng `CubicIntensity = 0.2f` để đường biểu đồ mượt mà. |
| | `btn_export_pdf` | Kích hoạt `GeneratePdfTask`, tạo tệp `.pdf` tại `getExternalFilesDir`. |
| | `tv_ai_advice` | Hiển thị nội dung từ Gemini, xử lý xóa thẻ Markdown thừa. |

---

## 🚀 5. TRẢI NGHIỆM CHI TIẾT (UX MICRO-INTERACTS)

- **Thông báo đẩy (FCM):** Tự động hiện Notification bôi đỏ khi phát hiện Ngã hoặc SOS, kể cả khi App đang tắt.
- **Xử lý sự cố mạng:** Khi mất internet, App hiện Snackbar màu cam thông báo "Đang cố gắng kết nối lại...".
- **Lifecycle Awareness:** AI Task tự động hủy (`cancel(true)`) khi người dùng thoát Fragment để tránh tràn bộ nhớ hoặc crash app.
- **Data Caching:** Lưu trữ lời khuyên AI trong `SharedPreferences` theo Khung giờ (Sáng/Trưa/Chiều/Tối) để tối ưu hiệu năng và tiết kiệm Token API.

---
*Tài liệu này liệt kê 100% các tính năng hiện có trong mã nguồn ứng dụng Android của dự án.*
