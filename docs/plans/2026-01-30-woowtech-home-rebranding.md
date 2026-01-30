# woowtech Home Android App Rebranding Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rebrand the Home Assistant Android app as "woowtech Home" with woowtech branding while maintaining full HA functionality.

**Architecture:** Modify branding assets and configuration files in the cloned Home Assistant Android repository. Changes are limited to: package ID, app name strings, color themes, launcher icons, splash screen, notification icons, and about page content. No functional changes to HA communication layer.

**Tech Stack:** Android (Kotlin), Gradle build system, XML resources, PNG/Vector drawables

---

## Pre-Implementation: Asset Preparation

Before starting the tasks, generate all required icon assets from the provided woowtech logo.

### Task 0: Generate Icon Assets from Logo

**Files:**
- Source: `/home/woowtech-ai-coder/Desktop/woow_ha_app/wo_Logotype2.png`
- Create: Multiple PNG files at different densities

**Step 1: Install ImageMagick if needed**

Run: `which convert || sudo apt-get install -y imagemagick`

**Step 2: Create icon generation script**

Create file `/home/woowtech-ai-coder/Desktop/woow_ha_app/generate_icons.sh`:

```bash
#!/bin/bash
set -e

SOURCE="/home/woowtech-ai-coder/Desktop/woow_ha_app/wo_Logotype2.png"
ANDROID_DIR="/home/woowtech-ai-coder/Desktop/woow_ha_app/android"

# Legacy launcher icons (for pre-API 26)
# mdpi: 48x48, hdpi: 72x72, xhdpi: 96x96, xxhdpi: 144x144, xxxhdpi: 192x192
convert "$SOURCE" -resize 48x48 "$ANDROID_DIR/app/src/main/res/mipmap-mdpi/ic_launcher.png"
convert "$SOURCE" -resize 48x48 "$ANDROID_DIR/app/src/main/res/mipmap-mdpi/ic_launcher_round.png"
convert "$SOURCE" -resize 72x72 "$ANDROID_DIR/app/src/main/res/mipmap-hdpi/ic_launcher.png"
convert "$SOURCE" -resize 72x72 "$ANDROID_DIR/app/src/main/res/mipmap-hdpi/ic_launcher_round.png"
convert "$SOURCE" -resize 96x96 "$ANDROID_DIR/app/src/main/res/mipmap-xhdpi/ic_launcher.png"
convert "$SOURCE" -resize 96x96 "$ANDROID_DIR/app/src/main/res/mipmap-xhdpi/ic_launcher_round.png"
convert "$SOURCE" -resize 144x144 "$ANDROID_DIR/app/src/main/res/mipmap-xxhdpi/ic_launcher.png"
convert "$SOURCE" -resize 144x144 "$ANDROID_DIR/app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png"
convert "$SOURCE" -resize 192x192 "$ANDROID_DIR/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
convert "$SOURCE" -resize 192x192 "$ANDROID_DIR/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png"

# Play Store icon (512x512) - for future use
convert "$SOURCE" -resize 512x512 "$ANDROID_DIR/app/src/main/ic_launcher-playstore.png"

echo "Icons generated successfully!"
```

**Step 3: Run the script**

Run: `chmod +x /home/woowtech-ai-coder/Desktop/woow_ha_app/generate_icons.sh && /home/woowtech-ai-coder/Desktop/woow_ha_app/generate_icons.sh`
Expected: "Icons generated successfully!"

**Step 4: Verify icons were created**

Run: `ls -la /home/woowtech-ai-coder/Desktop/woow_ha_app/android/app/src/main/res/mipmap-*/ic_launcher.png`
Expected: All 5 density files listed with recent timestamps

---

## Task 1: Change Package ID (Application ID)

**Files:**
- Modify: `/home/woowtech-ai-coder/Desktop/woow_ha_app/android/build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt:8`

**Step 1: Update the APPLICATION_ID constant**

Change line 8 from:
```kotlin
private const val APPLICATION_ID = "io.homeassistant.companion.android"
```

To:
```kotlin
private const val APPLICATION_ID = "com.woowtech.home"
```

**Step 2: Verify the change**

Run: `grep -n "APPLICATION_ID" /home/woowtech-ai-coder/Desktop/woow_ha_app/android/build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`
Expected: Line 8 shows `com.woowtech.home`

**Step 3: Commit**

```bash
cd /home/woowtech-ai-coder/Desktop/woow_ha_app/android && git add build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt && git commit -m "chore: change package ID to com.woowtech.home

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 2: Change App Name

**Files:**
- Modify: `/home/woowtech-ai-coder/Desktop/woow_ha_app/android/common/src/main/res/values/strings.xml`

**Step 1: Find and update the app_name string**

Search for the app_name string:
Run: `grep -n "app_name" /home/woowtech-ai-coder/Desktop/woow_ha_app/android/common/src/main/res/values/strings.xml | head -5`

Change:
```xml
<string name="app_name">Home Assistant</string>
```

To:
```xml
<string name="app_name">woowtech Home</string>
```

**Step 2: Verify the change**

Run: `grep "app_name" /home/woowtech-ai-coder/Desktop/woow_ha_app/android/common/src/main/res/values/strings.xml`
Expected: `<string name="app_name">woowtech Home</string>`

**Step 3: Commit**

```bash
cd /home/woowtech-ai-coder/Desktop/woow_ha_app/android && git add common/src/main/res/values/strings.xml && git commit -m "chore: rename app to woowtech Home

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 3: Update Brand Colors (Light Theme)

**Files:**
- Modify: `/home/woowtech-ai-coder/Desktop/woow_ha_app/android/common/src/main/res/values/colors.xml`

**Step 1: Update primary colors**

Change these colors from HA Blue (#03A9F4) to woowtech Brand Blue (#6183FC):

```xml
<!-- Before -->
<color name="colorPrimary">#03A9F4</color>
<color name="colorPrimaryDark">#0288D1</color>
<color name="colorAccent">#03A9F4</color>
<color name="colorActionBar">#03A9F4</color>

<!-- After -->
<color name="colorPrimary">#6183FC</color>
<color name="colorPrimaryDark">#4A6BD9</color>
<color name="colorAccent">#6183FC</color>
<color name="colorActionBar">#6183FC</color>
```

Also update the preference suggestion color (12% opacity of accent):
```xml
<!-- Before -->
<color name="colorPreferenceSuggestion">#1F03A9F4</color>

<!-- After -->
<color name="colorPreferenceSuggestion">#1F6183FC</color>
```

**Step 2: Verify the changes**

Run: `grep -E "colorPrimary|colorAccent|colorActionBar" /home/woowtech-ai-coder/Desktop/woow_ha_app/android/common/src/main/res/values/colors.xml`
Expected: All show `#6183FC` or `#4A6BD9`

**Step 3: Commit**

```bash
cd /home/woowtech-ai-coder/Desktop/woow_ha_app/android && git add common/src/main/res/values/colors.xml && git commit -m "style: update brand colors to woowtech blue (#6183FC)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 4: Update Launcher Background Color

**Files:**
- Modify: `/home/woowtech-ai-coder/Desktop/woow_ha_app/android/common/src/main/res/values/colors.xml`
- Modify: `/home/woowtech-ai-coder/Desktop/woow_ha_app/android/app/src/main/res/values/colors.xml`

**Step 1: Update launcher background color in common/colors.xml**

The adaptive icon background color needs to match the woowtech logo background.

Find and update:
```xml
<!-- Before -->
<color name="colorLauncherBackground">#18BCF2</color>

<!-- After -->
<color name="colorLauncherBackground">#6183FC</color>
```

**Step 2: Update in app/colors.xml if present**

Run: `grep -n "colorLauncherBackground\|ic_launcher_foreground" /home/woowtech-ai-coder/Desktop/woow_ha_app/android/app/src/main/res/values/colors.xml`

If `ic_launcher_foreground` exists, update it to white for the woowtech logo foreground:
```xml
<color name="ic_launcher_foreground">#FFFFFF</color>
```

**Step 3: Verify the changes**

Run: `grep "colorLauncherBackground" /home/woowtech-ai-coder/Desktop/woow_ha_app/android/common/src/main/res/values/colors.xml`
Expected: `#6183FC`

**Step 4: Commit**

```bash
cd /home/woowtech-ai-coder/Desktop/woow_ha_app/android && git add common/src/main/res/values/colors.xml app/src/main/res/values/colors.xml && git commit -m "style: update launcher background to woowtech brand color

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 5: Create woowtech Adaptive Icon Foreground

**Files:**
- Modify: `/home/woowtech-ai-coder/Desktop/woow_ha_app/android/app/src/main/res/drawable/ic_launcher_foreground.xml`

**Step 1: Replace the HA house icon with woowtech logo pattern**

The woowtech logo has a geometric pattern with "WOOW" stylized characters. Create a vector drawable that approximates the logo pattern in white on the brand blue background.

Replace the entire content of `ic_launcher_foreground.xml` with:

```xml
<vector android:height="108dp" android:viewportHeight="108"
    android:viewportWidth="108" android:width="108dp" xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- woowtech logo pattern - white geometric shapes on transparent (background is colorLauncherBackground) -->
    <!-- Centered in the 108dp canvas with safe zone (66dp usable area centered) -->
    <group android:translateX="21" android:translateY="21">
        <!-- Left vertical bar -->
        <path android:fillColor="#FFFFFF" android:pathData="M8,12 L8,54 L12,54 L12,12 Z"/>
        <!-- First A shape -->
        <path android:fillColor="#FFFFFF" android:pathData="M14,12 L22,54 L26,54 L18,12 Z"/>
        <path android:fillColor="#FFFFFF" android:pathData="M18,12 L26,54 L30,54 L22,12 Z"/>
        <!-- Second vertical bar -->
        <path android:fillColor="#FFFFFF" android:pathData="M24,12 L24,54 L28,54 L28,12 Z"/>
        <!-- First O circle (top right) -->
        <path android:fillColor="#FFFFFF" android:pathData="M38,12 A10,10 0 1,1 38,32 A10,10 0 1,1 38,12 M38,16 A6,6 0 1,0 38,28 A6,6 0 1,0 38,16"/>
        <!-- Second O circle (bottom left) -->
        <path android:fillColor="#FFFFFF" android:pathData="M16,34 A10,10 0 1,1 16,54 A10,10 0 1,0 16,34 M16,38 A6,6 0 1,0 16,50 A6,6 0 1,1 16,38"/>
        <!-- Right side inverted A shapes -->
        <path android:fillColor="#FFFFFF" android:pathData="M44,12 L52,54 L56,54 L48,12 Z"/>
        <path android:fillColor="#FFFFFF" android:pathData="M48,12 L56,54 L60,54 L52,12 Z"/>
        <!-- Right vertical bar -->
        <path android:fillColor="#FFFFFF" android:pathData="M54,12 L54,54 L58,54 L58,12 Z"/>
    </group>
</vector>
```

**Step 2: Verify the file was created properly**

Run: `head -20 /home/woowtech-ai-coder/Desktop/woow_ha_app/android/app/src/main/res/drawable/ic_launcher_foreground.xml`
Expected: Vector drawable with white paths

**Step 3: Copy to round variant**

Run: `cp /home/woowtech-ai-coder/Desktop/woow_ha_app/android/app/src/main/res/drawable/ic_launcher_foreground.xml /home/woowtech-ai-coder/Desktop/woow_ha_app/android/app/src/main/res/drawable/ic_launcher_foreground_round.xml`

**Step 4: Commit**

```bash
cd /home/woowtech-ai-coder/Desktop/woow_ha_app/android && git add app/src/main/res/drawable/ic_launcher_foreground.xml app/src/main/res/drawable/ic_launcher_foreground_round.xml && git commit -m "feat: replace launcher icon with woowtech logo pattern

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 6: Update Monochrome Icon

**Files:**
- Modify: `/home/woowtech-ai-coder/Desktop/woow_ha_app/android/app/src/main/res/drawable/ic_launcher_monochrome.xml`

**Step 1: Update monochrome icon to woowtech pattern**

Replace with the same pattern as foreground (single color for themed icons):

```xml
<vector android:height="108dp" android:viewportHeight="108"
    android:viewportWidth="108" android:width="108dp" xmlns:android="http://schemas.android.com/apk/res/android">
    <group android:translateX="21" android:translateY="21">
        <!-- Left vertical bar -->
        <path android:fillColor="#000000" android:pathData="M8,12 L8,54 L12,54 L12,12 Z"/>
        <!-- First A shape -->
        <path android:fillColor="#000000" android:pathData="M14,12 L22,54 L26,54 L18,12 Z"/>
        <path android:fillColor="#000000" android:pathData="M18,12 L26,54 L30,54 L22,12 Z"/>
        <!-- Second vertical bar -->
        <path android:fillColor="#000000" android:pathData="M24,12 L24,54 L28,54 L28,12 Z"/>
        <!-- First O circle -->
        <path android:fillColor="#000000" android:pathData="M38,12 A10,10 0 1,1 38,32 A10,10 0 1,1 38,12 M38,16 A6,6 0 1,0 38,28 A6,6 0 1,0 38,16"/>
        <!-- Second O circle -->
        <path android:fillColor="#000000" android:pathData="M16,34 A10,10 0 1,1 16,54 A10,10 0 1,0 16,34 M16,38 A6,6 0 1,0 16,50 A6,6 0 1,1 16,38"/>
        <!-- Right inverted A -->
        <path android:fillColor="#000000" android:pathData="M44,12 L52,54 L56,54 L48,12 Z"/>
        <path android:fillColor="#000000" android:pathData="M48,12 L56,54 L60,54 L52,12 Z"/>
        <!-- Right vertical bar -->
        <path android:fillColor="#000000" android:pathData="M54,12 L54,54 L58,54 L58,12 Z"/>
    </group>
</vector>
```

**Step 2: Copy to round monochrome variant**

Run: `cp /home/woowtech-ai-coder/Desktop/woow_ha_app/android/app/src/main/res/drawable/ic_launcher_monochrome.xml /home/woowtech-ai-coder/Desktop/woow_ha_app/android/app/src/main/res/drawable/ic_launcher_monochrome_round.xml`

**Step 3: Commit**

```bash
cd /home/woowtech-ai-coder/Desktop/woow_ha_app/android && git add app/src/main/res/drawable/ic_launcher_monochrome.xml app/src/main/res/drawable/ic_launcher_monochrome_round.xml && git commit -m "feat: update monochrome icon to woowtech pattern

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 7: Update Splash Screen Icon

**Files:**
- Modify: `/home/woowtech-ai-coder/Desktop/woow_ha_app/android/app/src/main/res/drawable/app_icon_launch.xml`

**Step 1: Replace splash screen icon with woowtech logo**

Replace the entire content with a woowtech-branded version:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="120dp"
    android:height="120dp"
    android:viewportWidth="120"
    android:viewportHeight="120">
    <!-- Circular background -->
    <path
        android:pathData="M60,0 A60,60 0 1,1 60,120 A60,60 0 1,1 60,0"
        android:fillColor="#6183FC"/>
    <!-- woowtech logo pattern in white, centered -->
    <group android:translateX="20" android:translateY="30">
        <!-- Simplified logo pattern -->
        <path android:fillColor="#FFFFFF" android:pathData="M10,15 L10,45 L14,45 L14,15 Z"/>
        <path android:fillColor="#FFFFFF" android:pathData="M16,15 L24,45 L28,45 L20,15 Z"/>
        <path android:fillColor="#FFFFFF" android:pathData="M20,15 L28,45 L32,45 L24,15 Z"/>
        <path android:fillColor="#FFFFFF" android:pathData="M26,15 L26,45 L30,45 L30,15 Z"/>
        <path android:fillColor="#FFFFFF" android:pathData="M40,15 A8,8 0 1,1 40,31 A8,8 0 1,1 40,15 M40,19 A4,4 0 1,0 40,27 A4,4 0 1,0 40,19"/>
        <path android:fillColor="#FFFFFF" android:pathData="M20,29 A8,8 0 1,1 20,45 A8,8 0 1,0 20,29 M20,33 A4,4 0 1,0 20,41 A4,4 0 1,1 20,33"/>
        <path android:fillColor="#FFFFFF" android:pathData="M50,15 L58,45 L62,45 L54,15 Z"/>
        <path android:fillColor="#FFFFFF" android:pathData="M54,15 L62,45 L66,45 L58,15 Z"/>
        <path android:fillColor="#FFFFFF" android:pathData="M60,15 L60,45 L64,45 L64,15 Z"/>
    </group>
</vector>
```

**Step 2: Update API 31+ splash screen if exists**

Run: `ls /home/woowtech-ai-coder/Desktop/woow_ha_app/android/app/src/main/res/drawable-v31/`

If `app_icon_launch_screen.xml` exists, update it similarly.

**Step 3: Commit**

```bash
cd /home/woowtech-ai-coder/Desktop/woow_ha_app/android && git add app/src/main/res/drawable/app_icon_launch.xml app/src/main/res/drawable-v31/ && git commit -m "feat: update splash screen icon to woowtech branding

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 8: Update Notification Icons

**Files:**
- Modify: `/home/woowtech-ai-coder/Desktop/woow_ha_app/android/common/src/main/res/drawable/ic_stat_ic_notification.xml`
- Modify: `/home/woowtech-ai-coder/Desktop/woow_ha_app/android/common/src/main/res/drawable/ic_stat_ic_notification_blue.xml`

**Step 1: Update main notification icon to simplified woowtech symbol**

Replace `ic_stat_ic_notification.xml` with a simplified "W" shape:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <!-- Simplified woowtech "W" pattern for notification -->
    <path
        android:pathData="M4,6 L4,18 L6,18 L6,6 Z M7,6 L11,18 L13,18 L9,6 Z M10,6 L14,18 L16,18 L12,6 Z M15,6 L15,18 L17,18 L17,6 Z M18,6 L18,18 L20,18 L20,6 Z"
        android:fillColor="#ffffff"/>
</vector>
```

**Step 2: Update blue notification icon variant**

Replace `ic_stat_ic_notification_blue.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M4,6 L4,18 L6,18 L6,6 Z M7,6 L11,18 L13,18 L9,6 Z M10,6 L14,18 L16,18 L12,6 Z M15,6 L15,18 L17,18 L17,6 Z M18,6 L18,18 L20,18 L20,6 Z"
        android:fillColor="#6183FC"/>
</vector>
```

**Step 3: Commit**

```bash
cd /home/woowtech-ai-coder/Desktop/woow_ha_app/android && git add common/src/main/res/drawable/ic_stat_ic_notification.xml common/src/main/res/drawable/ic_stat_ic_notification_blue.xml && git commit -m "feat: update notification icons to woowtech branding

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 9: Update About Page Content

**Files:**
- Modify: `/home/woowtech-ai-coder/Desktop/woow_ha_app/android/app/src/main/res/xml/preferences.xml`

**Step 1: Find and update the GitHub changelog URL**

Change line ~241:
```xml
<!-- Before -->
<Preference
    android:key="changelog_github"
    android:title="@string/changelog"
    android:icon="@drawable/ic_github"
    android:summary="https://github.com/home-assistant/android/releases"
    app:enableCopying="true" />

<!-- After - Update to woowtech website -->
<Preference
    android:key="changelog_github"
    android:title="@string/changelog"
    android:icon="@drawable/ic_github"
    android:summary="https://www.woowtech.com/support"
    app:enableCopying="true" />
```

**Step 2: Verify the change**

Run: `grep -A2 "changelog_github" /home/woowtech-ai-coder/Desktop/woow_ha_app/android/app/src/main/res/xml/preferences.xml`
Expected: Shows woowtech URL

**Step 3: Commit**

```bash
cd /home/woowtech-ai-coder/Desktop/woow_ha_app/android && git add app/src/main/res/xml/preferences.xml && git commit -m "chore: update about page links to woowtech

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 10: Add Open Source Attribution

**Files:**
- Modify: `/home/woowtech-ai-coder/Desktop/woow_ha_app/android/common/src/main/res/values/strings.xml`

**Step 1: Add attribution string**

Add a new string for open source credits (find the appropriate location near other about-related strings):

```xml
<string name="open_source_credits">Based on Home Assistant Companion App (Apache 2.0 License)</string>
<string name="woowtech_company">woowtech Smart Home Solutions</string>
```

**Step 2: Commit**

```bash
cd /home/woowtech-ai-coder/Desktop/woow_ha_app/android && git add common/src/main/res/values/strings.xml && git commit -m "chore: add open source attribution strings

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 11: Update Dark Mode Colors

**Files:**
- Modify: `/home/woowtech-ai-coder/Desktop/woow_ha_app/android/common/src/main/res/values-night/colors.xml`

**Step 1: Read the dark mode colors file**

Run: `cat /home/woowtech-ai-coder/Desktop/woow_ha_app/android/common/src/main/res/values-night/colors.xml`

**Step 2: Update any accent/primary colors to woowtech brand**

Change any `#03A9F4` references to `#6183FC`:

```xml
<color name="colorPrimary">#6183FC</color>
<color name="colorAccent">#6183FC</color>
```

**Step 3: Commit**

```bash
cd /home/woowtech-ai-coder/Desktop/woow_ha_app/android && git add common/src/main/res/values-night/colors.xml && git commit -m "style: update dark mode colors to woowtech brand

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 12: Build and Verify

**Step 1: Clean and build the project**

Run: `cd /home/woowtech-ai-coder/Desktop/woow_ha_app/android && ./gradlew clean assembleFullRelease --no-daemon`
Expected: BUILD SUCCESSFUL

If build fails, check error messages and fix any issues.

**Step 2: Verify APK was generated**

Run: `ls -la /home/woowtech-ai-coder/Desktop/woow_ha_app/android/app/build/outputs/apk/full/release/`
Expected: APK file present

**Step 3: Copy APK to output directory**

Run: `cp /home/woowtech-ai-coder/Desktop/woow_ha_app/android/app/build/outputs/apk/full/release/*.apk /home/woowtech-ai-coder/Desktop/woow_ha_app/woowtech-home.apk`

**Step 4: Final commit**

```bash
cd /home/woowtech-ai-coder/Desktop/woow_ha_app/android && git add -A && git commit -m "chore: complete woowtech Home rebranding

- Changed package ID to com.woowtech.home
- Renamed app to woowtech Home
- Updated brand colors to #6183FC
- Replaced launcher icons with woowtech logo
- Updated splash screen branding
- Updated notification icons
- Updated about page links
- Added open source attribution

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Summary Checklist

| Task | Description | Status |
|------|-------------|--------|
| 0 | Generate icon assets | ⬜ |
| 1 | Change package ID | ⬜ |
| 2 | Change app name | ⬜ |
| 3 | Update light theme colors | ⬜ |
| 4 | Update launcher background color | ⬜ |
| 5 | Create adaptive icon foreground | ⬜ |
| 6 | Update monochrome icon | ⬜ |
| 7 | Update splash screen icon | ⬜ |
| 8 | Update notification icons | ⬜ |
| 9 | Update about page content | ⬜ |
| 10 | Add open source attribution | ⬜ |
| 11 | Update dark mode colors | ⬜ |
| 12 | Build and verify | ⬜ |

---

## Notes

- **Signing:** Release APK requires keystore. For unsigned debug build, use `assembleFullDebug` instead.
- **Wear OS:** If Wear OS support is needed, similar changes must be applied to `/android/wear/` module.
- **Android Automotive:** If automotive support is needed, apply to `/android/automotive/` module.
