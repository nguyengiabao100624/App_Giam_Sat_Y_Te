package com.example.loginanimatedapp.chatbot;

import java.util.Map;

public class Chatbot {

    public static String getResponse(String message, Map<String, Object> data) {
        String lowerCaseMessage = message.toLowerCase();

        // 1. Lấy dữ liệu từ Map (Nếu có)
        String bpm = "--";
        String spo2 = "--";
        String temp = "--";
        String dust = "--";
        String trangThai = "Không rõ";
        String lastMeasure = "--";

        if (data != null) {
            bpm = String.valueOf(data.getOrDefault("BPM", "--"));
            spo2 = String.valueOf(data.getOrDefault("SpO2", "--"));
            temp = String.valueOf(data.getOrDefault("TempObj", "--"));
            dust = String.valueOf(data.getOrDefault("Dust", "--"));
            trangThai = String.valueOf(data.getOrDefault("TrangThai", "BÌNH THƯỜNG"));
            lastMeasure = String.valueOf(data.getOrDefault("LastMeasureTime", "--"));
        }

        // 2. Phân tích câu hỏi và trả lời dựa trên dữ liệu thật
        if (lowerCaseMessage.contains("hello") || lowerCaseMessage.contains("hi") || lowerCaseMessage.contains("xin chào")) {
            return "Xin chào! Tôi là trợ lý ảo Healthy 365. Tôi đang kết nối trực tiếp với thiết bị của bạn. Bạn muốn kiểm tra chỉ số nào?";
        } 
        
        else if (lowerCaseMessage.contains("nhịp tim")) {
            if ("--".equals(bpm) || "0".equals(bpm)) return "Thiết bị chưa có dữ liệu nhịp tim. Bạn hãy thực hiện đo nhé!";
            return "Nhịp tim hiện tại của bạn là " + bpm + " bpm. (Đo lúc: " + lastMeasure + "). Chỉ số này đang ở mức " + getHealthComment("bpm", bpm);
        } 
        
        else if (lowerCaseMessage.contains("spo2") || lowerCaseMessage.contains("oxy")) {
            if ("--".equals(spo2) || "0".equals(spo2)) return "Tôi chưa thấy dữ liệu SpO2. Hãy đảm bảo bạn đã đeo thiết bị đúng cách.";
            return "Nồng độ Oxy trong máu (SpO2) của bạn là " + spo2 + "%. " + getHealthComment("spo2", spo2);
        } 
        
        else if (lowerCaseMessage.contains("nhiệt độ") || lowerCaseMessage.contains("thân nhiệt") || lowerCaseMessage.contains("sốt")) {
            return "Thân nhiệt hiện tại là " + temp + "°C. " + getHealthComment("temp", temp);
        } 
        
        else if (lowerCaseMessage.contains("bụi") || lowerCaseMessage.contains("không khí") || lowerCaseMessage.contains("pm2.5")) {
            return "Nồng độ bụi mịn PM2.5 xung quanh bạn là " + dust + " µg/m³. " + getHealthComment("dust", dust);
        }

        else if (lowerCaseMessage.contains("tổng quát") || lowerCaseMessage.contains("sức khỏe thế nào") || lowerCaseMessage.contains("trạng thái")) {
            return "Báo cáo nhanh: Trạng thái hệ thống là [" + trangThai + "]. Nhịp tim: " + bpm + " bpm, SpO2: " + spo2 + "%, Thân nhiệt: " + temp + "°C. Mọi thứ vẫn ổn chứ?";
        }

        else if (lowerCaseMessage.contains("té ngã") || lowerCaseMessage.contains("ngã")) {
            return "Hệ thống đang giám sát té ngã 24/7. Trạng thái hiện tại: " + ("TE NGA".equalsIgnoreCase(trangThai) ? "CẢNH BÁO CÓ TÉ NGÃ!" : "An toàn.");
        } 
        
        else if (lowerCaseMessage.contains("cấp cứu") || lowerCaseMessage.contains("khẩn cấp")) {
            return "Nếu bạn đang gặp nguy hiểm, hãy nhấn giữ nút SOS trên thiết bị hoặc gọi ngay 115. Tôi có thể hỗ trợ gọi người thân giúp bạn không?";
        } 
        
        else {
            return "Tôi có thể cung cấp chính xác các chỉ số: Nhịp tim, SpO2, Thân nhiệt và Bụi mịn từ thiết bị của bạn. Bạn muốn xem gì nào?";
        }
    }

    private static String getHealthComment(String type, String valStr) {
        try {
            float val = Float.parseFloat(valStr);
            switch (type) {
                case "bpm":
                    if (val < 60) return "hơi thấp. Bạn nên nghỉ ngơi.";
                    if (val > 100) return "hơi cao. Hãy hít thở sâu và thư giãn nhé.";
                    return "rất tốt và bình thường.";
                case "spo2":
                    if (val < 94) return "Cảnh báo: Chỉ số Oxy hơi thấp, bạn nên chú ý hơi thở hoặc liên hệ y tế.";
                    return "Mức Oxy này hoàn toàn khỏe mạnh.";
                case "temp":
                    if (val >= 37.0) return "Cảnh báo: Bạn có dấu hiệu bị sốt. Hãy theo dõi sát sao nhé.";
                    if (val < 30.0) return "CẢNH BÁO NGUY HIỂM: Thân nhiệt hạ quá thấp (dưới 30°C). Hãy giữ ấm khẩn cấp!";
                    if (val < 31.0) return "Thân nhiệt hơi thấp (hơi lạnh), bạn nên chú ý giữ ấm cơ thể.";
                    return "Thân nhiệt ổn định và bình thường (đối với cảm biến đo ngoài da).";
                case "dust":
                    if (val > 50) return "Chất lượng không khí không tốt, bạn nên đeo khẩu trang hoặc bật máy lọc khí.";
                    return "Không khí xung quanh rất trong lành.";
            }
        } catch (Exception e) { return ""; }
        return "";
    }
}
