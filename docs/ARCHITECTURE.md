# 🏗️ System Architecture / Kiến trúc hệ thống

> **Healthy365** follows a layered architecture with clear separation of concerns between the IoT hardware, cloud services, and mobile application.

---

## 📐 High-Level Overview

```mermaid
graph LR
    subgraph "Edge Layer"
        HW["🔧 ESP32 + Sensors"]
    end

    subgraph "Cloud Layer"
        FB["☁️ Firebase Platform"]
    end

    subgraph "Application Layer"
        APP["📱 Android App"]
    end

    subgraph "External Services"
        AI["🤖 Gemini AI"]
        MAP["🗺️ Google Maps"]
        OSM["🏥 Overpass API"]
    end

    HW -->|Wi-Fi / JSON| FB
    FB <-->|Real-time SDK| APP
    APP -->|REST API| AI
    APP -->|SDK| MAP
    APP -->|HTTP| OSM
```

---

## 📱 Android Application Architecture

The app follows the **MVVM (Model-View-ViewModel)** pattern with the following layers:

```mermaid
graph TB
    subgraph "View Layer"
        ACT["Activities<br/>MainActivity, DashboardActivity"]
        FRAG["Fragments<br/>Home, Chat, Dashboard,<br/>Notifications, Account"]
        XML["XML Layouts<br/>ViewBinding"]
    end

    subgraph "ViewModel Layer"
        DVM["DashboardViewModel"]
        CVM["ChatViewModel"]
        AVM["AccountViewModel"]
    end

    subgraph "Data Layer"
        FB_AUTH["Firebase Auth"]
        FB_RTDB["Firebase RTDB"]
        FB_STORE["Firebase Storage"]
        GEMINI["Gemini AI Client"]
        MAPS["Maps + Overpass"]
    end

    ACT --> FRAG
    FRAG --> XML
    FRAG --> DVM
    FRAG --> CVM
    FRAG --> AVM
    DVM --> FB_RTDB
    DVM --> MAPS
    CVM --> GEMINI
    AVM --> FB_AUTH
    AVM --> FB_STORE
    FRAG --> FB_RTDB

    style ACT fill:#4CAF50,color:#fff
    style FRAG fill:#2196F3,color:#fff
    style DVM fill:#FF9800,color:#fff
    style CVM fill:#FF9800,color:#fff
    style AVM fill:#FF9800,color:#fff
    style FB_RTDB fill:#FFCA28,color:#333
```

---

## 🧭 Navigation Architecture

```mermaid
graph TD
    MAIN["MainActivity<br/>🔐 Login / Register"] -->|Auth Success| DASH["DashboardActivity<br/>📊 Main Screen"]
    
    DASH --> NAV["Bottom Navigation"]
    
    NAV --> HOME["🏠 HomeFragment<br/>Real-time Monitoring"]
    NAV --> MAP["🗺️ DashboardFragment<br/>Hospital Finder"]
    NAV --> CHAT["💬 ChatFragment<br/>AI Assistant"]
    NAV --> NOTIF["🔔 NotificationsFragment<br/>Alert History"]
    NAV --> ACC["👤 AccountFragment<br/>Profile & Settings"]

    HOME -->|Tap Metric Card| DETAIL["📈 MetricDetailFragment<br/>Charts & PDF Export"]
    HOME -->|Tap Map| FULLMAP["🗺️ FullMapFragment"]
    NOTIF -->|Tap Notification| NDETAIL["📋 NotificationDetailFragment"]
    ACC -->|Verify Phone| OTP["📱 OtpFragment"]
    DASH -->|Add Device| ADD["📷 AddDeviceActivity<br/>QR Scanner"]

    style MAIN fill:#F44336,color:#fff
    style DASH fill:#4CAF50,color:#fff
    style DETAIL fill:#9C27B0,color:#fff
```

---

## ☁️ Firebase Database Structure

### Real-time Data Nodes

```
Firebase Realtime Database
│
├── 📁 Users/
│   └── {uid}/
│       ├── name: "Nguyễn Văn A"
│       ├── email: "user@example.com"
│       ├── phone: "+84123456789"
│       ├── deviceID: "ESP32_001"
│       ├── gender: "male"
│       ├── birthday: "1990-01-01"
│       └── profileImageUrl: "https://..."
│
├── 📁 SensorData/
│   └── {deviceID}/
│       ├── HeartRate: 75          ← Updated every ~2s
│       ├── SpO2: 98              ← Updated every ~2s
│       ├── Temperature: 36.5     ← Updated every ~5s
│       ├── PM25: 12.3            ← Updated every ~10s
│       ├── FallDetected: false   ← Event-driven
│       ├── SOS: false            ← Event-driven
│       ├── DeviceTime: 1716789... ← Unix timestamp
│       ├── Latitude: 10.762622
│       └── Longitude: 106.660172
│
├── 📁 Control/
│   └── {deviceID}/
│       └── Cmd_CancelAlert: false  ← Written by app
│
├── 📁 Notifications/
│   └── {uid}/
│       └── {notificationId}/
│           ├── title: "⚠️ Cảnh báo nhịp tim"
│           ├── message: "BPM: 120 - Cao hơn bình thường"
│           ├── type: "heart_rate"
│           ├── timestamp: 1716789...
│           └── isRead: false
│
└── 📁 History/
    └── {deviceID}/
        └── {date}/
            └── {timestamp}/
                ├── heartRate: 75
                ├── spo2: 98
                └── temperature: 36.5
```

### Data Flow Patterns

| Pattern | Direction | Frequency | Description |
|---------|-----------|-----------|-------------|
| Sensor Push | ESP32 → Firebase | 2-10s | Continuous vital signs |
| Real-time Listen | Firebase → App | Instant | `ValueEventListener` |
| Command Write | App → Firebase | On-demand | Cancel alert, control |
| History Query | Firebase → App | On-demand | Chart data loading |
| Auth Flow | App ↔ Firebase | On-demand | Login/Register/OTP |

---

## 🔌 IoT Hardware Integration

### Sensor Specifications

| Sensor | Measurement | Interface | Sampling Rate |
|--------|------------|-----------|---------------|
| **MAX30102** | Heart Rate, SpO2 | I2C | ~100 Hz (raw), 2s (processed) |
| **MPU6050** | Acceleration (Fall Detection) | I2C | 100 Hz |
| **DS18B20** | Body Temperature | OneWire | Every 5s |
| **PM2.5 Sensor** | Air Quality (μg/m³) | Analog/UART | Every 10s |

### ESP32 → Firebase Communication

```
ESP32 Boot
    │
    ├── Connect Wi-Fi
    ├── Initialize Firebase Client
    ├── Initialize Sensors
    │
    └── Main Loop (FreeRTOS Tasks)
        ├── Task 1: Read MAX30102 → Calculate BPM/SpO2
        ├── Task 2: Read MPU6050 → Fall Detection Algorithm
        ├── Task 3: Read DS18B20 → Temperature
        ├── Task 4: Read PM2.5 → Air Quality
        │
        └── Push JSON to Firebase RTDB
            {
              "HeartRate": 75,
              "SpO2": 98,
              "Temperature": 36.5,
              "PM25": 12.3,
              "FallDetected": false,
              "DeviceTime": 1716789000
            }
```

---

## 🤖 AI Integration Architecture

### Gemini AI Medical Assistant

```mermaid
sequenceDiagram
    participant U as 👤 User
    participant CF as 💬 ChatFragment
    participant CB as 🤖 Chatbot.java
    participant G as ☁️ Gemini API

    U->>CF: Type message or tap suggestion
    CF->>CB: sendMessage(userInput)
    
    Note over CB: Build prompt with<br/>system instructions +<br/>health context
    
    CB->>G: GenerativeModel.generateContent()
    G-->>CB: AI Response (Markdown)
    CB-->>CF: Format response
    CF->>U: Display in chat bubble

    Note over CF: Also supports:<br/>• Voice input (MediaRecorder)<br/>• Image attachment (Camera/Gallery)<br/>• Quick suggestions
```

### System Prompt Strategy

The AI assistant uses a carefully crafted system prompt that:
1. **Role**: Acts as "Healthy 365" medical professional
2. **Language**: Responds in Vietnamese
3. **Safety**: Includes emergency escalation rules (SpO2 < 90% → immediate hospital)
4. **Context**: Receives current sensor data for personalized advice
5. **Limitations**: Disclaims that AI advice doesn't replace professional medical care

---

## 🗺️ Maps & Hospital Finder

### Hospital Search Flow

```mermaid
graph LR
    A["📍 Get Patient<br/>GPS Location"] --> B["📡 Query Overpass API<br/>radius=5km"]
    B --> C["🔍 Filter Results<br/>amenity=hospital"]
    C --> D["📌 Display Markers<br/>on Google Maps"]
    D --> E["📋 Show Details<br/>Name, Type, Distance"]
    E --> F["📤 Share Location<br/>via Intent"]
```

### Overpass API Query

```
[out:json][timeout:30];
(
  node["amenity"="hospital"](around:5000,{lat},{lon});
  way["amenity"="hospital"](around:5000,{lat},{lon});
);
out center;
```

---

## 🔔 Notification System

### Alert Processing Pipeline

```mermaid
graph TD
    A["📡 Firebase Listener<br/>detects abnormal value"] --> B{"Threshold Check"}
    
    B -->|"BPM > 100 or < 60"| C["❤️ Heart Rate Alert"]
    B -->|"SpO2 < 95%"| D["🫁 SpO2 Alert"]
    B -->|"Temp > 37.5°C"| E["🌡️ Temperature Alert"]
    B -->|"PM2.5 > 35"| F["🌫️ Air Quality Alert"]
    B -->|"FallDetected = true"| G["🚨 Fall Alert"]
    B -->|"SOS = true"| H["🆘 SOS Alert"]

    C --> I["💾 Save to Firebase<br/>Notifications/{uid}/"]
    D --> I
    E --> I
    F --> I
    G --> I
    H --> I

    I --> J["📱 Push Notification<br/>NotificationService"]
    J --> K["🔊 Sound + Vibration"]

    style G fill:#F44336,color:#fff
    style H fill:#F44336,color:#fff
```

### Foreground Service

The `NotificationService` runs as an **Android Foreground Service** (`FOREGROUND_SERVICE_DATA_SYNC`) to ensure continuous monitoring even when the app is in the background. It:

1. Maintains a persistent Firebase listener
2. Evaluates incoming sensor data against health thresholds  
3. Creates high-priority notifications for critical alerts
4. Manages notification channels (Emergency, Info, Service)

---

*For API-level documentation, see [API_REFERENCE.md](API_REFERENCE.md)*
