# woowtech Home

[繁體中文](README.zh-TW.md)

The official woowtech Home Android companion app for smart home control.

## Download

[![Download APK](https://img.shields.io/github/v/release/WOOWTECH/woow_ha_app?label=Download%20APK&logo=android)](https://github.com/WOOWTECH/woow_ha_app/releases/latest)

## Overview

woowtech Home is a mobile companion app that connects to your woowtech smart home server at `https://aiot.woowtech.io`. It provides local control and privacy-first smart home automation powered by Home Assistant open source technology.

## Features

- **Remote Access**: Control your smart home from anywhere
- **Local Control**: Direct connection to your home server when on the same network
- **Sensors**: Background sensor collection for automation triggers
- **Notifications**: Push notifications for alerts and automations
- **Widgets**: Home screen widgets for quick device control
- **Wear OS**: Support for Wear OS smartwatches
- **Android Auto**: Integration with Android Auto for in-car control

## Branding

| Property | Value |
|----------|-------|
| Primary Color | #6183FC (woowtech Brand Blue) |
| App Name | woowtech Home |
| Package ID | com.woowtech.home |
| Server URL | https://aiot.woowtech.io |

## Building

### Requirements

- JDK 17+
- Android SDK
- Gradle

### Debug Build

```bash
./gradlew assembleFullDebug
```

### Release Build

```bash
./gradlew assembleFullRelease
```

## APK Location

After building, the APK can be found at:
```
app/build/outputs/apk/full/debug/app-full-debug.apk
```

## Server Configuration

The app connects to:
- **Server URL**: https://aiot.woowtech.io
- **OAuth Client ID**: https://aiot.woowtech.io/android

## Architecture

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| Dependency Injection | Hilt |
| Networking | Retrofit + OkHttp WebSocket |
| Database | Room |
| Serialization | Kotlinx.serialization |

## Modules

| Module | Description |
|--------|-------------|
| `:app` | Main mobile application |
| `:wear` | Wear OS application |
| `:automotive` | Android Automotive version |
| `:common` | Shared code across all apps |

## Code Quality

```bash
# Format code
./gradlew ktlintFormat

# Check code style
./gradlew ktlintCheck

# Run tests
./gradlew test

# Run linter
./gradlew lint
```

## License

Based on Home Assistant Companion for Android - Open Source Home Automation.

---

**woowtech** - Smart Home Solutions
