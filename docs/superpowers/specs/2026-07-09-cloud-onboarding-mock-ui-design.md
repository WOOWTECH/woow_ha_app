# Cloud Onboarding Mock UI — Design Spec

**Date:** 2026-07-09
**Status:** Draft
**Branch:** `dev/cloud-onboarding`

## Goal

Add 3 new Composable screens to the onboarding flow, allowing users to choose between
"local device" and "cloud service" connection. The cloud path walks through a mock sign-in
and a mock provisioning step, then hands off to the existing OAuth flow via
`startLaunchOnboarding()`.

**This iteration is UI skeleton only — no real HTTP calls, no Google sign-in, no changes
to existing HA connection/OAuth logic.**

---

## Architecture Decision

**Approach A — Replace `startDestination`** in `OnboardingNavigation.kt`.

When `!skipWelcome`, the current `startDestination` is `WelcomeRoute`. We change it to
`CloudChooserRoute`. The user picks "local" → navigates to `WelcomeRoute` (existing flow
untouched). The user picks "cloud" → `CloudSignInRoute` → `CloudProvisionRoute` →
`startLaunchOnboarding()`.

Rationale: this is the smallest possible change. The `startDestination` `when` block is
already designed to be extended. All other approaches (LaunchViewModel split, nested nav
graph) touch more files or introduce new architectural patterns not present in the codebase.

## Post-Provision Handoff

`CloudProvisionScreen` calls `context.startLaunchOnboarding(url, hideExistingServers = false,
skipWelcome = true)` on completion. This restarts `LaunchActivity` with the provisioned URL
pre-filled and skips directly to Connection/OAuth. It is the safest boundary — no need to
understand `ConnectionRoute` internals.

---

## New Files

All files follow the existing `onboarding/<screen_name>/` + `navigation/` convention.

### 1. `onboarding/cloudchooser/CloudChooserScreen.kt`

```
Package: io.homeassistant.companion.android.onboarding.cloudchooser
```

**UI:**
- Full-screen column layout (same pattern as `WelcomeScreen`)
- App branding image at top (`R.drawable.ic_woowtech_branding`, same as Welcome)
- Title: hardcoded `"選擇連線方式"` (TODO: move to strings.xml later)
- Two option cards, vertically stacked:
  - Card 1: "連結本地設備" / subtitle "連接您已架設好的 Home Assistant"
  - Card 2: "使用雲端服務" / subtitle "立即開通雲端 Woow HA"
- Each card is a clickable `Surface` with rounded corners, using existing
  `HADimens` spacing and `HATextStyle` typography
- No ViewModel needed — pure stateless Composable

**Callbacks:**
```kotlin
@Composable
internal fun CloudChooserScreen(
    onLocalClick: () -> Unit,
    onCloudClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**Navigation wiring (in `OnboardingNavigation.kt`):**
- `onLocalClick` → `navController.navigateToWelcome()`
- `onCloudClick` → `navController.navigateToCloudSignIn()`

### 2. `onboarding/cloudchooser/navigation/CloudChooserNavigation.kt`

```kotlin
@Serializable
internal data object CloudChooserRoute

internal fun NavController.navigateToCloudChooser(navOptions: NavOptions? = null) {
    navigate(route = CloudChooserRoute, navOptions)
}

internal fun NavGraphBuilder.cloudChooserScreen(
    onLocalClick: () -> Unit,
    onCloudClick: () -> Unit,
) {
    composable<CloudChooserRoute> {
        CloudChooserScreen(onLocalClick = onLocalClick, onCloudClick = onCloudClick)
    }
}
```

---

### 3. `onboarding/cloudsignin/CloudSignInScreen.kt`

```
Package: io.homeassistant.companion.android.onboarding.cloudsignin
```

**UI:**
- `Scaffold` with `HATopBar` (back button + optional help) — same pattern as `ManualServerScreen`
- Title: "雲端登入"
- `HATextField` for email (keyboard type: Email)
- `HATextField` for password (keyboard type: Password, visual transformation)
- `HAAccentButton`: "登入 / 註冊"
- Loading state: button shows `CircularProgressIndicator` and is disabled
- Error state: reserved but unused in mock (always succeeds)

**ViewModel:** `CloudSignInViewModel`

```kotlin
@HiltViewModel
internal class CloudSignInViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<SignInUiState>(SignInUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun onSignInClick(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = SignInUiState.Loading
            delay(500)  // Mock network delay
            _uiState.value = SignInUiState.Success
        }
    }
}

internal sealed interface SignInUiState {
    data object Idle : SignInUiState
    data object Loading : SignInUiState
    data object Success : SignInUiState
    // data class Error(val message: String) : SignInUiState  // Reserved for real API
}
```

**Callbacks:**
```kotlin
@Composable
internal fun CloudSignInScreen(
    viewModel: CloudSignInViewModel,
    onBackClick: () -> Unit,
    onSignInSuccess: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**Navigation wiring:**
- `onBackClick` → `navController.popBackStack()`
- `onSignInSuccess` → `navController.navigateToCloudProvision()`

### 4. `onboarding/cloudsignin/navigation/CloudSignInNavigation.kt`

```kotlin
@Serializable
internal data object CloudSignInRoute

internal fun NavController.navigateToCloudSignIn(navOptions: NavOptions? = null) {
    navigate(route = CloudSignInRoute, navOptions)
}

internal fun NavGraphBuilder.cloudSignInScreen(
    onBackClick: () -> Unit,
    onSignInSuccess: () -> Unit,
) {
    composable<CloudSignInRoute> {
        CloudSignInScreen(
            viewModel = hiltViewModel(),
            onBackClick = onBackClick,
            onSignInSuccess = onSignInSuccess,
        )
    }
}
```

---

### 5. `onboarding/cloudprovision/CloudProvisionScreen.kt`

```
Package: io.homeassistant.companion.android.onboarding.cloudprovision
```

**Mock config (top of file or separate `CloudOnboardingMockConfig.kt`):**
```kotlin
// TODO: Replace with real API call to Odoo /api/woow/tenant_service/provision
// and poll /status when integrating the actual cloud provisioning backend.
internal const val MOCK_TEST_SERVER_URL = "https://woowtechdemo-ha1.woowtech.io"
```

**UI:**
- `Scaffold` with `HATopBar` (back button)
- Two states driven by ViewModel:

  **Idle state:**
  - Icon/illustration (reuse existing drawable or simple cloud icon from Material Icons)
  - Title: "開通雲端服務"
  - Subtitle: "點擊下方按鈕，為您開啟專屬的 Woow HA 伺服器"
  - `HAAccentButton`: "開啟 Woow HA 服務"

  **Provisioning state:**
  - `CircularProgressIndicator` (centered)
  - Text: "正在開通您的 Woow HA 服務..."
  - Button hidden or disabled

  **Ready state:**
  - Success icon (checkmark)
  - Text: "開通完成！正在連線..."
  - Automatically calls `startLaunchOnboarding()` via `LaunchedEffect`

**ViewModel:** `CloudProvisionViewModel`

```kotlin
@HiltViewModel
internal class CloudProvisionViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<ProvisionUiState>(ProvisionUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun onProvisionClicked() {
        viewModelScope.launch {
            _uiState.value = ProvisionUiState.Provisioning
            delay(3000)  // Mock K3s/Odoo provisioning delay
            _uiState.value = ProvisionUiState.Ready(MOCK_TEST_SERVER_URL)
        }
    }
}

internal sealed interface ProvisionUiState {
    data object Idle : ProvisionUiState
    data object Provisioning : ProvisionUiState
    data class Ready(val serverUrl: String) : ProvisionUiState
}
```

**Handoff to existing flow (in Screen):**
```kotlin
val context = LocalContext.current
LaunchedEffect(uiState) {
    if (uiState is ProvisionUiState.Ready) {
        context.startLaunchOnboarding(
            urlToOnboard = uiState.serverUrl,
            hideExistingServers = false,
            skipWelcome = true,
        )
    }
}
```

### 6. `onboarding/cloudprovision/navigation/CloudProvisionNavigation.kt`

```kotlin
@Serializable
internal data object CloudProvisionRoute

internal fun NavController.navigateToCloudProvision(navOptions: NavOptions? = null) {
    navigate(route = CloudProvisionRoute, navOptions)
}

internal fun NavGraphBuilder.cloudProvisionScreen(
    onBackClick: () -> Unit,
) {
    composable<CloudProvisionRoute> {
        CloudProvisionScreen(
            viewModel = hiltViewModel(),
            onBackClick = onBackClick,
        )
    }
}
```

---

## Modified Files

### `OnboardingNavigation.kt`

Changes (minimal):

1. **Add imports** for `CloudChooserRoute`, `cloudChooserScreen`, `navigateToCloudSignIn`,
   `CloudSignInRoute`, `cloudSignInScreen`, `navigateToCloudProvision`,
   `CloudProvisionRoute`, `cloudProvisionScreen`

2. **Change `startDestination`** (one line):
   ```kotlin
   val startDestination = when {
       !skipWelcome -> CloudChooserRoute  // was: WelcomeRoute
       urlToOnboard.isNullOrEmpty() -> ServerDiscoveryRoute(serverDiscoveryMode)
       else -> ConnectionRoute(urlToOnboard)
   }
   ```

3. **Register 3 new screens** inside `navigation<OnboardingRoute>` block:
   ```kotlin
   cloudChooserScreen(
       onLocalClick = { navController.navigateToWelcome() },
       onCloudClick = { navController.navigateToCloudSignIn() },
   )
   cloudSignInScreen(
       onBackClick = navController::popBackStack,
       onSignInSuccess = { navController.navigateToCloudProvision() },
   )
   cloudProvisionScreen(
       onBackClick = navController::popBackStack,
   )
   ```

**No other existing files are modified.**

---

## File Summary

| Action | Path |
|--------|------|
| **NEW** | `onboarding/cloudchooser/CloudChooserScreen.kt` |
| **NEW** | `onboarding/cloudchooser/navigation/CloudChooserNavigation.kt` |
| **NEW** | `onboarding/cloudsignin/CloudSignInScreen.kt` |
| **NEW** | `onboarding/cloudsignin/CloudSignInViewModel.kt` |
| **NEW** | `onboarding/cloudsignin/navigation/CloudSignInNavigation.kt` |
| **NEW** | `onboarding/cloudprovision/CloudProvisionScreen.kt` |
| **NEW** | `onboarding/cloudprovision/CloudProvisionViewModel.kt` |
| **NEW** | `onboarding/cloudprovision/navigation/CloudProvisionNavigation.kt` |
| **MODIFY** | `onboarding/OnboardingNavigation.kt` (3 changes: imports, startDestination, screen registration) |

Total: 8 new files, 1 modified file.

---

## Styling Rules

- Use `HATextStyle.Headline` for titles, `HATextStyle.Body` for body text
- Use `HAAccentButton` for primary actions, `HAPlainButton` for secondary
- Use `HATextField` for input fields
- Use `HATopBar` for scaffold top bars with back navigation
- Use `HADimens.SPACE4` / `SPACE6` for padding/spacing
- Use `HAThemeForPreview` in `@HAPreviews` composables
- Use `LocalHAColorScheme.current` for any custom colors
- Do not introduce new UI libraries or design system elements

## Out of Scope

- Real HTTP requests to Odoo or any backend
- Google sign-in
- Changes to Welcome / ServerDiscovery / ManualServer screens
- Changes to LinkHandler / LinkActivity (deep links)
- Changes to HA connection / OAuth token exchange logic
- Changes to LaunchViewModel / HANavHost
- String resource extraction (hardcode Chinese text for now, extract later)

## Acceptance Criteria

1. `./gradlew assembleFullDebug` compiles without errors
2. Fresh install (no connected servers) → app opens → CloudChooser screen appears
3. Tap "連結本地設備" → Welcome screen (existing flow, unchanged)
4. Tap "使用雲端服務" → CloudSignIn → enter any email/password → tap "登入" →
   CloudProvision → tap "開啟 Woow HA 服務" → spinner 3 sec → auto-navigates to
   HA OAuth screen with URL pre-filled as `woowtechdemo-ha1.woowtech.io`
5. Back button works correctly on CloudSignIn and CloudProvision screens
