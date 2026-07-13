# Cloud Onboarding 升級：Device Flow OAuth + 開通 API

**Date:** 2026-07-13
**Status:** Draft
**Branch:** `dev/cloud-onboarding`
**Source of Truth:** `ha-app-oauth-provisioning.md` (§6 Device Flow, §7 開通 API 契約 v1.1)

## Goal

將現有 mock UI（delay + 硬編 URL）升級為真實的 WOOW PaaS 串接：
1. **階段 A** — Device Flow OAuth 登入（RFC 8628）
2. **階段 B** — 開通 API（`POST /api/ha-paas/provision` + `GET /api/ha-paas/status` 輪詢）

一次寫完 A + B。stg 環境 provision 目前回 503，App 當 error 處理，平台開放後自動通。

---

## Design Decisions

| 項目 | 決定 | 理由 |
|------|------|------|
| 開發範圍 | A + B 一次寫完 | 契約已定，不需等平台；503 當 error |
| CloudSignIn 畫面 | 完全替換為 Device Flow 畫面 | Device Flow 不需 email/password，使用者在瀏覽器登入 |
| Token 存儲 | ViewModel 記憶體，不持久化 | 一次性開通流程；provision 冪等，重入重跑無害 |
| 重入 | 每次重跑 Device Flow + provision | 已開通帳號 provision 秒回 200 ready |
| Token 傳遞 | Navigation argument | 與既有 `ConnectionRoute(url)` pattern 一致 |
| HTTP client | OkHttp（App 已有依賴） | 不引入新依賴；簡單 POST/GET 足夠 |

---

## API Configuration

```kotlin
object WoowPaasConfig {
    const val BASE_URL = "https://stg.woowtech.io"  // TODO: prod 上線時切換
    const val CLIENT_ID = "woow-ha-app"
    const val SCOPES = "ha:provision workspace:read smarthome:read"
    const val DEVICE_CODE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
}
```

---

## File Changes

### Unchanged
- `CloudChooserScreen.kt` + navigation — 不動
- `CloudProvisionScreen.kt` — 畫面結構保留，新增 error/suspended/deleting 狀態顯示
- `OnboardingNavigation.kt` — 接線邏輯微調（CloudProvisionRoute 加 accessToken 參數）

### Rewritten

#### 1. `CloudSignInViewModel.kt` → Device Flow OAuth

**責任：** 執行 RFC 8628 Device Flow 的兩步：取得 device code → 輪詢 token。

**UiState：**
```kotlin
internal sealed interface DeviceFlowUiState {
    data object Idle : DeviceFlowUiState
    data object RequestingCode : DeviceFlowUiState
    data class WaitingForAuth(
        val userCode: String,
        val verificationUri: String,
        val verificationUriComplete: String,
    ) : DeviceFlowUiState
    data class Authorized(val accessToken: String) : DeviceFlowUiState
    data class Error(val message: String, val canRetry: Boolean) : DeviceFlowUiState
}
```

**流程：**
1. `startDeviceFlow()` → POST `/oauth2/device_authorization`
   - Body: `client_id=woow-ha-app&scope=ha:provision workspace:read smarthome:read`
   - 成功 → 解析 `device_code`, `user_code`, `verification_uri`, `verification_uri_complete`, `interval`
   - 切換到 `WaitingForAuth` 狀態
2. 自動開始 `pollForToken()` → 依 `interval` 秒輪詢 POST `/oauth2/token`
   - Body: `grant_type=urn:ietf:params:oauth:grant-type:device_code&device_code=<code>&client_id=woow-ha-app`
   - `authorization_pending` → 繼續等
   - `slow_down` → interval += 5s，繼續等
   - `access_denied` → Error（「授權被拒絕」，canRetry=true）
   - `expired_token` → Error（「驗證碼已過期」，canRetry=true）
   - 成功 → 解析 `access_token`，切換到 `Authorized`

**注意：**
- device_code 存活 15 分鐘
- 輪詢 interval 預設 5 秒，收到 `slow_down` 要 +5s
- 不需處理 refresh_token（記憶體模式，access_token 1h 足夠完成開通）

#### 2. `CloudSignInScreen.kt` → Device Flow 畫面

**WaitingForAuth 狀態的 UI：**
- 大字顯示 `user_code`（等寬字體，方便辨識）
- 「前往驗證」按鈕 → 開系統瀏覽器到 `verification_uri_complete`
- 底部文字提示：「請在瀏覽器中登入並授權」
- 等待動畫（CircularProgressIndicator）
- user_code 旁邊可點擊複製

**Error 狀態的 UI：**
- 錯誤訊息 + 「重新開始」按鈕（重跑 startDeviceFlow）

#### 3. `CloudProvisionViewModel.kt` → 真實開通 API

**接收 `accessToken` 參數**（從 navigation argument 傳入）。

**UiState 擴充：**
```kotlin
internal sealed interface ProvisionUiState {
    data object Idle : ProvisionUiState
    data object Provisioning : ProvisionUiState
    data class Ready(val serverUrl: String) : ProvisionUiState
    data class Error(val message: String, val canRetry: Boolean) : ProvisionUiState
    data object Suspended : ProvisionUiState    // 服務已暫停
    data object Deleting : ProvisionUiState     // 刪除中
}
```

**流程：**
1. `onProvisionClicked()` → POST `/api/ha-paas/provision`
   - Header: `Authorization: Bearer <accessToken>`
   - `202` → Provisioning，開始輪詢
   - `200` → Ready（已開通，取 `ha_url`）
   - `409 suspended` → Suspended 狀態
   - `409 deleting` → Deleting 狀態
   - `401` → Error（「登入已過期，請返回重新登入」）
   - `403` → Error（「權限不足」）
   - `503` → Error（「服務尚未開放，請稍後再試」）
2. `pollStatus()` → GET `/api/ha-paas/status`
   - Header: `Authorization: Bearer <accessToken>`
   - 5s 起步，指數退避至 30s
   - `provisioning` → 繼續輪詢
   - `ready` → Ready（取 `ha_url`）
   - `error` → Error（顯示 error 欄位，canRetry=true → 可再次 POST provision）
   - `suspended` → Suspended
   - `deleting` → Deleting
   - `none` → 異常，Error
   - 逾 10 分鐘 → Error（「開通逾時，請重試或聯繫支援」）

#### 4. `CloudProvisionScreen.kt` — 新增狀態顯示

在現有 Idle / Provisioning / Ready 基礎上，新增：
- **Error 狀態：** 錯誤訊息 + 「重試」按鈕（canRetry=true 時）或 「返回」按鈕
- **Suspended 狀態：** 「服務已暫停，請聯繫支援」
- **Deleting 狀態：** 「前一個服務正在刪除中，請稍後重試」+ 「重試」按鈕

#### 5. 新增 `WoowPaasApi.kt` — HTTP 呼叫集中管理

```
Package: io.homeassistant.companion.android.onboarding.cloud
```

**責任：** 封裝所有 WOOW PaaS API 呼叫，回傳 Result 型別。

```kotlin
internal class WoowPaasApi {
    suspend fun requestDeviceCode(clientId: String, scope: String): Result<DeviceCodeResponse>
    suspend fun pollToken(clientId: String, deviceCode: String, grantType: String): Result<TokenResponse>
    suspend fun provision(accessToken: String): Result<ProvisionResponse>
    suspend fun getStatus(accessToken: String): Result<StatusResponse>
}
```

- 用 OkHttp 直接呼叫（App 已有依賴）
- JSON 解析用 `org.json.JSONObject`（Android 內建，不需額外依賴）
- Hilt `@Inject constructor()` 注入到 ViewModel

### Navigation 調整

#### `CloudProvisionRoute` 加參數

```kotlin
@Serializable
internal data class CloudProvisionRoute(val accessToken: String)  // 原本是 data object
```

#### `CloudSignInNavigation.kt` 回調變更

```kotlin
// 原本: onSignInSuccess: () -> Unit
// 改為: onSignInSuccess: (accessToken: String) -> Unit
```

#### `OnboardingNavigation.kt` 接線微調

```kotlin
cloudSignInScreen(
    onBackClick = navController::popBackStack,
    onSignInSuccess = { accessToken ->
        navController.navigateToCloudProvision(accessToken)
    },
)
```

---

## Error Handling Summary

| 來源 | 錯誤 | 畫面行為 |
|------|------|---------|
| Device Flow | 網路失敗 | 「無法連線」+ 重試 |
| Device Flow | `access_denied` | 「授權被拒絕」+ 重新開始 |
| Device Flow | `expired_token` | 「驗證碼已過期（15分鐘）」+ 重新開始 |
| Provision | `401` | 「登入已過期」+ 返回重新登入 |
| Provision | `403` | 「權限不足（缺少 ha:provision）」 |
| Provision | `503` | 「服務尚未開放，請稍後再試」 |
| Provision | `409 suspended` | 「服務已暫停，請聯繫支援」 |
| Provision | `409 deleting` | 「刪除中，稍後可重新開通」+ 重試 |
| Status | `error` | 顯示 error 欄位 + 重試（再次 POST provision） |
| Status | 輪詢 > 10min | 「開通逾時」+ 重試/聯繫支援 |

---

## File Summary

| Action | Path |
|--------|------|
| **NEW** | `onboarding/cloud/WoowPaasApi.kt` |
| **NEW** | `onboarding/cloud/WoowPaasConfig.kt` |
| **REWRITE** | `onboarding/cloudsignin/CloudSignInViewModel.kt` |
| **REWRITE** | `onboarding/cloudsignin/CloudSignInScreen.kt` |
| **REWRITE** | `onboarding/cloudprovision/CloudProvisionViewModel.kt` |
| **MODIFY** | `onboarding/cloudprovision/CloudProvisionScreen.kt` (新增 Error/Suspended/Deleting 狀態) |
| **MODIFY** | `onboarding/cloudprovision/navigation/CloudProvisionNavigation.kt` (Route 加 accessToken) |
| **MODIFY** | `onboarding/cloudsignin/navigation/CloudSignInNavigation.kt` (callback 加 accessToken) |
| **MODIFY** | `onboarding/OnboardingNavigation.kt` (接線微調) |

Total: 2 new, 3 rewrite, 4 modify.

---

## Out of Scope

- Token 持久化（EncryptedSharedPreferences）
- Google 登入
- Refresh token rotation 處理（記憶體模式，access_token 1h 足夠）
- Authorization Code + PKCE 替代路徑（§5）
- 既有本地流程的任何變更
- String resource 抽取（先硬編中文）

## Acceptance Criteria

1. `./gradlew assembleFullDebug` 編譯通過
2. 選「雲端」→ Device Flow 畫面顯示 user_code + 驗證連結
3. 點「前往驗證」→ 系統瀏覽器開啟 stg.woowtech.io/device（預填 user_code）
4. 使用者在瀏覽器授權後 → App 自動偵測到 token → 進入開通畫面
5. 按「開啟 Woow HA 服務」→ POST provision → 目前回 503 → 顯示「服務尚未開放」
6. 平台開放後 → 202 → 輪詢 → ready → 自動跳轉到 HA OAuth
7. Back 鍵在每個畫面都正常運作
