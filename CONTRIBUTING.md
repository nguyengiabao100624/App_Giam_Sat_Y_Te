# Contributing to Healthy365 / Đóng góp cho Healthy365

First off, thank you for considering contributing to Healthy365! 🎉

Trước tiên, cảm ơn bạn đã quan tâm đến việc đóng góp cho Healthy365! 🎉

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Code Style Guidelines](#code-style-guidelines)
- [Branch Naming Convention](#branch-naming-convention)
- [Commit Message Convention](#commit-message-convention)
- [Pull Request Process](#pull-request-process)

---

## Code of Conduct

This project adheres to a code of conduct. By participating, you are expected to uphold this standard. Please be respectful and constructive in all interactions.

---

## How Can I Contribute?

### 🐛 Reporting Bugs

- Use the [GitHub Issues](https://github.com/nguyengiabao100624/healthy365-android/issues) tab
- Include detailed steps to reproduce the issue
- Attach screenshots or logs if possible
- Specify your Android version and device model

### 💡 Suggesting Features

- Open an issue with the `enhancement` label
- Describe the feature and its use case
- Include mockups or diagrams if applicable

### 🔧 Code Contributions

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

---

## Development Setup

### Prerequisites

| Tool | Version |
|------|---------|
| Android Studio | Ladybug 2024.2+ |
| JDK | 11+ |
| Android SDK | API 24+ |
| Gradle | 8.x |

### Setup Steps

```bash
# 1. Fork and clone
git clone https://github.com/<your-username>/healthy365-android.git
cd healthy365-android

# 2. Create local.properties
echo "MAPS_API_KEY=your_key_here" >> local.properties
echo "GEMINI_API_KEY=your_key_here" >> local.properties

# 3. Add google-services.json to app/ directory

# 4. Open in Android Studio and sync Gradle
```

---

## Code Style Guidelines

### Java Conventions

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use **4 spaces** for indentation (no tabs)
- Maximum line length: **120 characters**
- Always use `@Override` annotation
- Use `final` for immutable variables

### Naming Conventions

| Type | Convention | Example |
|------|-----------|---------|
| Classes | PascalCase | `HomeFragment` |
| Methods | camelCase | `loadSensorData()` |
| Constants | UPPER_SNAKE_CASE | `MAX_HEART_RATE` |
| Layout files | snake_case with prefix | `fragment_home.xml` |
| View IDs | snake_case with type prefix | `tv_heart_rate`, `btn_cancel` |
| Drawables | snake_case with prefix | `ic_heart_rate.xml` |

### XML Layout Rules

- Use `ConstraintLayout` as root layout when possible
- Define dimensions in `dimens.xml`, not inline
- Use semantic color names (e.g., `@color/status_normal` instead of `@color/green`)

---

## Branch Naming Convention

```
<type>/<short-description>

Examples:
  feature/ai-voice-input
  bugfix/map-crash-on-rotate
  hotfix/firebase-auth-timeout
  docs/update-readme
  refactor/notification-service
```

### Branch Types

| Type | Description |
|------|-------------|
| `feature/` | New feature or enhancement |
| `bugfix/` | Bug fix |
| `hotfix/` | Critical production fix |
| `docs/` | Documentation changes |
| `refactor/` | Code refactoring (no functional change) |
| `test/` | Adding or updating tests |

---

## Commit Message Convention

Follow the [Conventional Commits](https://www.conventionalcommits.org/) standard:

```
<type>(<scope>): <description>

[optional body]

Examples:
  feat(chat): add voice input with waveform animation
  fix(home): resolve offline detection false positives  
  docs(readme): update architecture diagram
  refactor(notification): extract alert logic to separate class
```

### Commit Types

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation |
| `style` | Code style (formatting, no logic change) |
| `refactor` | Code refactoring |
| `test` | Adding tests |
| `chore` | Build process or auxiliary tools |

---

## Pull Request Process

1. **Update your branch** with the latest `master`:
   ```bash
   git fetch origin
   git rebase origin/master
   ```

2. **Ensure the app builds** without errors:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Create a PR** with:
   - Clear title following commit convention
   - Description of changes
   - Screenshots for UI changes
   - Reference to related issues

4. **Wait for review** — at least one approval is required before merging

---

## 📝 Questions?

Feel free to open an issue or reach out to the maintainer:

- **Nguyễn Gia Bảo** — [@nguyengiabao100624](https://github.com/nguyengiabao100624)

Thank you for helping make Healthy365 better! 🏥❤️
