# 📡 API Reference / Tài liệu API

> This document describes the external APIs and Firebase data contracts used by the Healthy365 Android application.

---

## Table of Contents

- [Firebase Realtime Database](#firebase-realtime-database)
- [Firebase Authentication](#firebase-authentication)
- [Google Gemini AI](#google-gemini-ai)
- [Google Maps SDK](#google-maps-sdk)
- [Overpass API](#overpass-api)

---

## Firebase Realtime Database

### Base URL
```
https://cssuckhoe-default-rtdb.asia-southeast1.firebasedatabase.app
```

### Endpoints (Database Nodes)

#### `GET /SensorData/{deviceID}`

**Description:** Real-time sensor readings from ESP32 device.

| Field | Type | Unit | Range | Description |
|-------|------|------|-------|-------------|
| `HeartRate` | Integer | BPM | 40-200 | Heart rate from MAX30102 |
| `SpO2` | Integer | % | 70-100 | Blood oxygen saturation |
| `Temperature` | Float | °C | 30.0-42.0 | Body temperature from DS18B20 |
| `PM25` | Float | μg/m³ | 0-500 | Air quality (PM2.5 concentration) |
| `FallDetected` | Boolean | — | true/false | MPU6050 fall detection flag |
| `SOS` | Boolean | — | true/false | Manual SOS button press |
| `DeviceTime` | Long | Unix ms | — | Device timestamp for offline detection |
| `Latitude` | Double | ° | -90 to 90 | GPS latitude |
| `Longitude` | Double | ° | -180 to 180 | GPS longitude |

**Android Usage:**
```java
DatabaseReference sensorRef = FirebaseDatabase.getInstance()
    .getReference("SensorData")
    .child(deviceID);

sensorRef.addValueEventListener(new ValueEventListener() {
    @Override
    public void onDataChange(DataSnapshot snapshot) {
        int heartRate = snapshot.child("HeartRate").getValue(Integer.class);
        int spo2 = snapshot.child("SpO2").getValue(Integer.class);
        // ... process data
    }
});
```

---

#### `PUT /Control/{deviceID}/Cmd_CancelAlert`

**Description:** Send command to ESP32 to cancel emergency alert (disable buzzer).

| Field | Type | Description |
|-------|------|-------------|
| `Cmd_CancelAlert` | Boolean | `true` to cancel, auto-resets to `false` |

**Android Usage:**
```java
FirebaseDatabase.getInstance()
    .getReference("Control")
    .child(deviceID)
    .child("Cmd_CancelAlert")
    .setValue(true);
```

---

#### `GET /Users/{uid}`

**Description:** User profile information.

| Field | Type | Description |
|-------|------|-------------|
| `name` | String | Display name |
| `email` | String | Email address |
| `phone` | String | Phone number (E.164 format) |
| `deviceID` | String | Paired ESP32 device ID |
| `gender` | String | `"male"` / `"female"` / `"other"` |
| `birthday` | String | ISO date format `YYYY-MM-DD` |
| `profileImageUrl` | String | Firebase Storage URL |

---

#### `POST /Notifications/{uid}/{notificationId}`

**Description:** Alert notification record.

| Field | Type | Description |
|-------|------|-------------|
| `title` | String | Alert title (Vietnamese) |
| `message` | String | Detailed alert message |
| `type` | String | One of: `heart_rate`, `spo2`, `temperature`, `pm25`, `fall`, `sos` |
| `timestamp` | Long | Unix timestamp (ms) |
| `isRead` | Boolean | Read status |

---

## Firebase Authentication

### Supported Auth Methods

| Method | Provider | Description |
|--------|----------|-------------|
| Email/Password | `EmailAuthProvider` | Primary registration & login |
| Phone OTP | `PhoneAuthProvider` | Secondary verification for emergency contacts |

### Auth Flow

```
Register:
  1. createUserWithEmailAndPassword(email, password)
  2. sendEmailVerification()
  3. Create /Users/{uid} profile node

Login:
  1. signInWithEmailAndPassword(email, password)  
  2. Check emailVerified
  3. Load user profile from /Users/{uid}

Phone OTP:
  1. PhoneAuthProvider.verifyPhoneNumber(phoneNumber)
  2. Handle SMS auto-retrieval or manual input
  3. signInWithCredential(PhoneAuthCredential)
  4. Update /Users/{uid}/phone
```

---

## Google Gemini AI

### Configuration

| Parameter | Value |
|-----------|-------|
| Model | `gemini-2.0-flash` |
| API Key Source | `BuildConfig.GEMINI_API_KEY` |
| Temperature | Default (1.0) |
| Max Output Tokens | Default |

### System Prompt Template

```
Bạn là trợ lý sức khỏe AI tên "Healthy 365", một bác sĩ ảo chuyên nghiệp.

Quy tắc:
- Trả lời bằng tiếng Việt
- Sử dụng ngôn ngữ dễ hiểu, thân thiện
- Khi SpO2 < 90%: CẢNH BÁO KHẨN CẤP, yêu cầu gọi 115
- Khi BPM > 150 hoặc < 40: CẢNH BÁO KHẨN CẤP
- Luôn nhắc nhở: "Lời khuyên AI không thay thế bác sĩ chuyên khoa"
- Hỗ trợ phân tích: nhịp tim, SpO2, thân nhiệt, chất lượng không khí
```

### Input Types

| Type | Description | Implementation |
|------|-------------|----------------|
| Text | Direct user message | `EditText` input |
| Voice | Audio recording → text | `MediaRecorder` + Speech-to-Text |
| Image | Photo attachment | Camera/Gallery Intent |
| Quick Action | Pre-defined prompts | Suggestion chip buttons |

### Android Usage

```java
GenerativeModel model = new GenerativeModel(
    "gemini-2.0-flash",
    BuildConfig.GEMINI_API_KEY
);

Content content = new Content.Builder()
    .addText(systemPrompt + "\n\n" + userMessage)
    .build();

GenerateContentResponse response = model.generateContent(content);
String aiReply = response.getText();
```

---

## Google Maps SDK

### Configuration

| Parameter | Value |
|-----------|-------|
| API Key Source | `manifestPlaceholders["MAPS_API_KEY"]` |
| Map Type | Custom JSON style (medical theme) |
| Min Zoom | 10 |
| Max Zoom | 18 |

### Custom Map Style

The app uses a custom JSON map style that:
- Hides non-medical POIs (restaurants, shops)
- Emphasizes roads and transit routes
- Highlights medical facilities
- Uses soft color palette for readability

### Key Methods

```java
// Center on patient location
LatLng patientLocation = new LatLng(latitude, longitude);
googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(patientLocation, 15));

// Add patient marker
googleMap.addMarker(new MarkerOptions()
    .position(patientLocation)
    .title("Vị trí bệnh nhân")
    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
```

---

## Overpass API

### Base URL
```
https://overpass-api.de/api/interpreter
```

### Hospital Search Query

**Method:** `POST`  
**Content-Type:** `application/x-www-form-urlencoded`

**Query Template:**
```
[out:json][timeout:30];
(
  node["amenity"="hospital"](around:{radius},{latitude},{longitude});
  way["amenity"="hospital"](around:{radius},{latitude},{longitude});
  relation["amenity"="hospital"](around:{radius},{latitude},{longitude});
);
out center;
```

**Default Parameters:**

| Parameter | Value | Description |
|-----------|-------|-------------|
| `radius` | 5000 | Search radius in meters |
| `latitude` | Dynamic | Patient's GPS latitude |
| `longitude` | Dynamic | Patient's GPS longitude |

### Response Format

```json
{
  "elements": [
    {
      "type": "node",
      "id": 123456789,
      "lat": 10.762622,
      "lon": 106.660172,
      "tags": {
        "name": "Bệnh viện Chợ Rẫy",
        "amenity": "hospital",
        "healthcare": "hospital",
        "addr:street": "201B Nguyễn Chí Thanh",
        "phone": "+84 28 3855 4137"
      }
    }
  ]
}
```

### Android Usage

```java
String query = String.format(
    "[out:json][timeout:30];" +
    "(node[\"amenity\"=\"hospital\"](around:5000,%f,%f);" +
    "way[\"amenity\"=\"hospital\"](around:5000,%f,%f););" +
    "out center;",
    latitude, longitude, latitude, longitude
);

// Execute via AsyncTask or Coroutine
URL url = new URL("https://overpass-api.de/api/interpreter");
HttpURLConnection conn = (HttpURLConnection) url.openConnection();
conn.setRequestMethod("POST");
conn.setDoOutput(true);
conn.getOutputStream().write(("data=" + URLEncoder.encode(query, "UTF-8")).getBytes());
```

---

## 📊 Health Threshold Reference

These thresholds determine alert levels and UI color coding:

| Metric | Normal (🟢) | Warning (🟡) | Critical (🔴) |
|--------|-------------|--------------|----------------|
| Heart Rate | 60-100 BPM | 100-120 or 50-60 BPM | >120 or <50 BPM |
| SpO2 | >95% | 90-95% | <90% |
| Temperature | 36.0-37.2°C | 37.2-38.0°C | >38.0°C or <35.5°C |
| PM2.5 | 0-12 μg/m³ | 12-35 μg/m³ | >35 μg/m³ |
| Fall Detection | No fall | — | Fall detected |
| SOS | Inactive | — | SOS activated |

---

*For architecture-level documentation, see [ARCHITECTURE.md](ARCHITECTURE.md)*
