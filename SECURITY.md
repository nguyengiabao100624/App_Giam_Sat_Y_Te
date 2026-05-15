# Security Policy / Chính sách bảo mật

## 🔐 Supported Versions

| Version | Supported |
|---------|-----------|
| 1.0.x   | ✅ Active support |

## 🛡️ Security Measures / Biện pháp bảo mật

### API Key Protection

This project uses sensitive API keys that are **never committed to the repository**:

| Key | Storage Method | Purpose |
|-----|---------------|---------|
| `MAPS_API_KEY` | `local.properties` (gitignored) | Google Maps SDK |
| `GEMINI_API_KEY` | `local.properties` (gitignored) | Google Gemini AI |
| `google-services.json` | `app/` directory (gitignored) | Firebase configuration |

All API keys are injected at build time via `BuildConfig` fields and `manifestPlaceholders`, ensuring they never appear in source code.

### Firebase Security

- **Authentication:** Email/Password + Phone OTP verification
- **Database Rules:** Read/write access restricted to authenticated users
- **Storage Rules:** User-specific access control for profile images

### Data Privacy

- Health data is transmitted over **HTTPS/TLS** (Firebase default)
- Sensor readings are associated with device IDs, not directly with personal information
- Users can delete their notification history from the app
- Phone numbers and emails can be hidden via privacy toggles

## 🚨 Reporting a Vulnerability

If you discover a security vulnerability, please report it responsibly:

1. **DO NOT** create a public GitHub issue
2. **Email:** Contact the maintainer directly via GitHub profile
3. **Include:**
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact assessment
4. **Response time:** We aim to respond within **48 hours**

## ⚠️ Known Limitations

- `local.properties` must be manually created by each developer (not version-controlled)
- Firebase Realtime Database rules should be reviewed and tightened for production use
- The app currently uses `isMinifyEnabled = false` — ProGuard should be enabled for production releases

---

*Last updated: May 2026*
