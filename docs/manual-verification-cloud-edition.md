# Manual verification: on-premise vs. cloud APK

Step-by-step walkthrough for verifying that PR #7 (`feature/cloud-edition`) produces two
functionally distinct APKs from one repo — the on-premise edition (unchanged behavior) and
the cloud edition (adds the WOOW cloud onboarding flow in front of the existing local flow).

Reviewer time budget: ~15 min once both APKs are installed.

## 1. Build both debug APKs

From the repo root:

```bash
# On-premise edition (identical to today's app)
./gradlew :app:assembleFullOnpremDebug

# Cloud edition (adds CloudChooser → device-flow sign-in → provisioning)
./gradlew :app:assembleFullCloudDebug
```

Output paths:

```
app/build/outputs/apk/fullOnprem/debug/app-full-onprem-debug.apk
app/build/outputs/apk/fullCloud/debug/app-full-cloud-debug.apk
```

The two APKs have different `applicationId`s, so they install side-by-side without either
uninstalling the other:

| APK | applicationId (debug) | Launcher label |
|---|---|---|
| `app-full-onprem-debug.apk` | `com.woowtech.home.debug`      | `woowtech Home`       |
| `app-full-cloud-debug.apk`  | `com.woowtech.homecloud.debug` | `woowtech Home Cloud` |

Install both (order does not matter):

```bash
adb install -r app/build/outputs/apk/fullOnprem/debug/app-full-onprem-debug.apk
adb install -r app/build/outputs/apk/fullCloud/debug/app-full-cloud-debug.apk
```

If `adb install` reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, uninstall that specific package
first (`adb uninstall com.woowtech.home.debug` or `adb uninstall com.woowtech.homecloud.debug`)
— never uninstall the other one; they are separate apps.

## 2. Verify the on-premise APK — behavior must be unchanged

Launch **woowtech Home** from the launcher.

Expected screens, in order:

1. **Welcome screen** — the exact screen the app has shown on first launch since main. Two
   buttons: `開始使用` and `我已經在使用 Home Assistant`.
   - ✅ Pass criterion: no `選擇連線方式` chooser is shown before this screen.
2. Tap `開始使用` → **Server discovery / manual server** screen — proceed however you
   normally would (mDNS discovery on Wi-Fi, or `manual` to type a URL).
3. After registration → **Name your device** screen → done → dashboard.

Sanity check that nothing cloud leaked in:

```bash
# Both must return no matches. If either matches, the edition dimension is broken.
adb shell pm dump com.woowtech.home.debug | grep -i cloud
apkanalyzer dex packages app/build/outputs/apk/fullOnprem/debug/app-full-onprem-debug.apk \
  | grep -iE 'cloudchooser|cloudsignin|cloudprovision|woowpaas'
```

## 3. Verify the cloud APK — CloudChooser gate + both branches

Launch **woowtech Home Cloud** from the launcher.

### 3a. First screen must be the CloudChooser (not Welcome)

Expected: `選擇連線方式` screen with the WoowTech logo and two cards:

- `連結本地設備` — `連結架設好的 woowtech smarthome`
- `使用雲端服務` — `立即開通使用 woowtech smarthome`

✅ Pass: this screen is the first screen, **not** the on-prem Welcome screen.
❌ Fail: if you see the Welcome screen first, `editionStartDestination()` is wrong or the
`cloud` source set was not picked up — abort and check the build variant selector.

### 3b. Local branch — must behave exactly like the on-prem APK

Tap `連結本地設備`.

Expected: the app navigates into the on-prem Welcome screen and continues through the
identical server-discovery / registration / name-your-device flow verified in step 2. This
branch shares code with on-prem and must not diverge.

Once you have registered against a local HA, back out (or clear app data) before running 3c.

### 3c. Cloud branch — OAuth 2.0 device flow

Tap `使用雲端服務`.

Expected sequence:

1. Loading spinner (`CloudSignInScreen` calls `requestDeviceCode()` against
   `https://stg.woowtech.io` — debug always talks to staging, never prod).
2. Screen shows a **user code** (e.g. `ABCD-1234`) and a **verification URI** (roughly
   `https://stg.woowtech.io/device?user_code=…`) with a `Copy` button and an `Open browser` /
   `前往驗證` action.
3. Tap the browser action → default browser opens the verification URI. Complete sign-in
   against the WOOW staging account there.
4. Back in the app, the screen advances automatically (the ViewModel is polling the token
   endpoint; RFC 8628 §3.4 — `authorization_pending` / `slow_down` are handled internally).
5. **Cloud provisioning screen** appears, showing progress while the app calls the WOOW PaaS
   `POST /provision` and then registers the returned HA instance.
6. On success → **Name your device** → dashboard (same shared flow as on-prem from this point
   onward).

Failure paths to smoke-test (each should show a recoverable error state, not crash):

- Airplane mode before tapping `使用雲端服務` → error screen with `重試` button.
- Deny authorization in the browser → screen shows `access_denied` and returns to
  CloudChooser cleanly (no infinite spinner).
- Kill the app during step 4 and reopen → device-flow session is not restored; user lands
  on CloudChooser again (this is the current, intentional behavior — session persistence is
  out of scope for Phase 0).

### 3d. Endpoint isolation sanity check

The cloud APK must talk to **staging** on debug and only staging:

```bash
adb logcat -c
# Perform step 3c up to the point where the user_code appears
adb logcat -d | grep -iE 'paas\.woowtech\.io'  # must be EMPTY on debug
adb logcat -d | grep -iE 'stg\.woowtech\.io'   # must have matches
```

If `paas.woowtech.io` appears in the debug APK's logs, the build-type → base URL wiring in
`AndroidEditionFlavorConventionPlugin.kt` regressed.

## 4. Cross-APK sanity — both installed at once

Both launcher icons must be present and independent:

```bash
adb shell pm list packages | grep woowtech.home
# Expected:
# package:com.woowtech.home.debug
# package:com.woowtech.homecloud.debug
```

Uninstalling one must not affect the other. If installing the second APK silently replaced
the first, the flavor dimension is misconfigured (both variants share an `applicationId`).

## 5. Release APK spot-check (optional, ~10 min)

Only do this if the debug walkthrough passed. Release wiring diverges in two places worth
confirming — R8/keystore selection and the cloud base URL flip staging → production:

```bash
./gradlew :app:assembleFullOnpremRelease :app:assembleFullCloudRelease
```

- Cloud release APK: the device-flow verification URI must resolve to
  `https://paas.woowtech.io/device?...`, **not** the staging host.
- On-prem release APK: `apkanalyzer dex packages` on the release APK must still show 0 cloud
  or `woowpaas` classes (dex-verified pre-merge, per PR description). Re-verify if any
  build-logic file changed.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| CloudChooser shows in the on-prem APK | Wrong variant selected in Android Studio, or `edition` dimension not applied to `:app` |
| Welcome shows first in the cloud APK | `editionStartDestination()` in `app/src/cloud/...` was not compiled into the variant |
| Second APK install replaces the first | Both variants resolved to the same `applicationId` — check `AndroidEditionFlavorConventionPlugin` |
| Cloud sign-in stays on spinner forever | Staging PaaS is down, or CF-Access is blocking the device — check network before assuming an app bug |
| `paas.woowtech.io` in a debug APK's logs | Build-type gate in the edition plugin regressed |
