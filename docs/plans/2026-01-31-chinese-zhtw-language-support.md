# PRD: 繁體中文 (zh-TW) 語言支援

## 概述

為 woowtech Home Android 應用程式新增繁體中文 (zh-TW) 語言支援，讓使用者可以在 App 設定中選擇繁體中文作為顯示語言。

## 目標

- 讓台灣及其他繁體中文使用者能以母語使用 App
- 提升使用者體驗和 App 的可及性
- 擴大 woowtech Home 的目標市場

## 功能範圍

### 包含
- 翻譯所有 App 介面字串（約 1400+ 字串）
- 在語言選擇器中新增「繁體中文」選項
- 支援跟隨裝置語言自動切換

### 不包含
- 簡體中文 (zh-CN) 支援（未來可擴充）
- 語言選擇 UI 的改動
- RTL (右到左) 排版支援

## 技術設計

### 1. 新增語言資源檔

**建立目錄：**
```
common/src/main/res/values-zh-rTW/
```

**建立檔案：**
```
common/src/main/res/values-zh-rTW/strings.xml
```

### 2. 更新語言設定檔

**檔案：** `common/src/main/res/xml/locales_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en"/>
    <locale android:name="zh-TW"/>
</locale-config>
```

### 3. 翻譯字串

從 `common/src/main/res/values/strings.xml` 翻譯所有字串至繁體中文。

**翻譯原則：**
- 保持技術術語的一致性
- 使用台灣慣用的繁體中文用語
- 保留無需翻譯的字串（如 URL、品牌名稱）
- 注意字串中的格式化參數（%1$s、%d 等）

### 4. 語言選擇器顯示

系統會自動在語言選擇器中顯示新語言：
- 顯示名稱：「中文 (台灣)」或「繁體中文」
- 語言代碼：`zh-TW`

## 實作步驟

1. **建立 values-zh-rTW 目錄**
2. **建立 strings.xml 並翻譯所有字串**
3. **更新 locales_config.xml 新增 zh-TW**
4. **測試語言切換功能**
5. **驗證 UI 顯示正確**

## 測試計畫

### 功能測試
- [ ] 語言選擇器顯示「繁體中文」選項
- [ ] 選擇繁體中文後 App 介面正確切換
- [ ] 選擇「裝置語言」時正確跟隨系統設定
- [ ] App 重啟後語言設定保持

### UI 測試
- [ ] 所有畫面文字正確顯示繁體中文
- [ ] 文字長度不會造成 UI 排版問題
- [ ] 特殊字元正確顯示

### 相容性測試
- [ ] Android 12 及以下版本正常運作
- [ ] Android 13+ 系統語言整合正常

## 時程預估

| 任務 | 預估時間 |
|------|----------|
| 建立資源檔結構 | 5 分鐘 |
| 翻譯字串 | 2-3 小時 |
| 更新 locales_config.xml | 5 分鐘 |
| 測試與驗證 | 30 分鐘 |

## 成功標準

- 使用者可在設定中選擇繁體中文
- 所有 App 介面文字正確顯示繁體中文
- 語言切換流暢，無需重啟 App

## 參考資料

- [Android 多語言支援文件](https://developer.android.com/guide/topics/resources/localization)
- [AppCompatDelegate Locale API](https://developer.android.com/reference/androidx/appcompat/app/AppCompatDelegate)
- 現有語言系統：`settings/language/LanguagesManager.kt`
