# woowtech Home

[English](README.md)

woowtech Home 是官方的 Android 智慧家庭控制應用程式。

## 下載

[![下載 APK](https://img.shields.io/github/v/release/WOOWTECH/woow_ha_app?label=下載%20APK&logo=android)](https://github.com/WOOWTECH/woow_ha_app/releases/latest)

## 概述

woowtech Home 是一款行動裝置應用程式，可連接至您的 woowtech 智慧家庭伺服器 `https://aiot.woowtech.io`。本應用程式提供本地控制功能，並採用以隱私為優先的智慧家庭自動化技術，基於 Home Assistant 開源技術打造。

## 功能特色

- **遠端存取**：隨時隨地控制您的智慧家庭
- **本地控制**：在同一網路時直接連接至家庭伺服器
- **感測器**：背景感測器資料收集，用於自動化觸發
- **通知**：推播通知功能，用於警報和自動化
- **小工具**：主畫面小工具，快速控制裝置
- **Wear OS**：支援 Wear OS 智慧手錶
- **Android Auto**：整合 Android Auto，車內控制功能

## 品牌資訊

| 屬性 | 值 |
|------|-----|
| 主要顏色 | #6183FC (woowtech 品牌藍) |
| 應用程式名稱 | woowtech Home |
| 套件 ID | com.woowtech.home |
| 伺服器網址 | https://aiot.woowtech.io |

## 建置

### 系統需求

- JDK 17+
- Android SDK
- Gradle

### 偵錯版本建置

```bash
./gradlew assembleFullDebug
```

### 正式版本建置

```bash
./gradlew assembleFullRelease
```

## APK 位置

建置完成後，APK 檔案位於：
```
app/build/outputs/apk/full/debug/app-full-debug.apk
```

## 伺服器設定

應用程式連接至：
- **伺服器網址**：https://aiot.woowtech.io
- **OAuth 用戶端 ID**：https://aiot.woowtech.io/android

## 架構

| 元件 | 技術 |
|------|------|
| 程式語言 | Kotlin |
| UI 框架 | Jetpack Compose |
| 依賴注入 | Hilt |
| 網路通訊 | Retrofit + OkHttp WebSocket |
| 資料庫 | Room |
| 序列化 | Kotlinx.serialization |

## 模組

| 模組 | 說明 |
|------|------|
| `:app` | 主要行動應用程式 |
| `:wear` | Wear OS 應用程式 |
| `:automotive` | Android Automotive 版本 |
| `:common` | 所有應用程式共用程式碼 |

## 程式碼品質

```bash
# 格式化程式碼
./gradlew ktlintFormat

# 檢查程式碼風格
./gradlew ktlintCheck

# 執行測試
./gradlew test

# 執行 Lint 檢查
./gradlew lint
```

## 授權條款

基於 Home Assistant Companion for Android - 開源家庭自動化專案。

---

**woowtech** - 智慧家庭解決方案
