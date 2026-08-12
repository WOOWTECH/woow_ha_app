package io.homeassistant.companion.android.util

import android.os.Build
import android.os.strictmode.DiskReadViolation
import android.os.strictmode.DiskWriteViolation
import android.os.strictmode.IncorrectContextUseViolation
import android.os.strictmode.Violation
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.util.IgnoreViolationRule

val vmPolicyIgnoredViolationRules = listOf(
    IgnoreChromiumWebViewWrongContextUsage,
    IgnoreChromiumWebViewSetDebuggingEnabledWrongContextUsage,
    IgnoreBarcodeScannerRotationListenerWrongContextUsage,
)

val threadPolicyIgnoredViolationRules = listOf(
    IgnoreChangelogDiskRead,
    IgnoreNotificationHistoryFragmentLoadSharedPrefDiskRead,
    IgnoreComposeTextContextMenuDiskRead,
    IgnoreActivityThreadVsyncDiskReadWrite,
    IgnoreSamsungInputRuneDiskRead,
    IgnoreSamsungKnoxProKioskDiskRead,
    IgnoreSamsungSpegDiskRead,
    IgnoreAndroidAutoServiceConnectionDiskRead,
    IgnoreAndroidAutoRendererServiceDiskRead,
    IgnoreMiuiFontDiskRead,
    IgnoreMiuiTurboSchedMonitorDiskRead,
    IgnoreMiuiEnterpriseFrameworkDiskRead,
    IgnoreMediatekFrameworkDiskAccess,
    IgnoreChromiumKeyStoreDiskWrite,
    IgnoreAppCompatPersistLocalesDiskReadWrite,
)

/**
 * Ignore an [IncorrectContextUseViolation] that can occur in the Chromium WebView client
 * during configuration changes.
 *
 * This issue typically arises when the application context is incorrectly used during
 * configuration changes (e.g., screen rotation) within the WebView's internal mechanisms.
 * It reproduces across multiple WebView packaging variants, whose stack frames have different
 * file name prefixes (e.g. `chromium-TrichromeWebViewGoogle*`, `chromium-SystemWebViewGoogle*`),
 * so the match is kept broad on the `chromium-` prefix paired with `onConfigurationChanged`.
 *
 * It doesn't seem to be tracked anywhere.
 */
private data object IgnoreChromiumWebViewWrongContextUsage : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is IncorrectContextUseViolation) return false

        return violation.stackTrace.any {
            it.fileName?.startsWith("chromium-") == true &&
                it.methodName == "onConfigurationChanged"
        }
    }
}

/**
 * Ignore an [IncorrectContextUseViolation] triggered by Chromium WebView startup when the app
 * calls [android.webkit.WebView.setWebContentsDebuggingEnabled].
 *
 * The API is a process-wide static toggle with no Context parameter, so Chromium falls back to
 * the application context when constructing `ViewConfigurationHelper` during native initialization,
 * which trips `detectIncorrectContextUse()`. There is no way for the app to supply a UI context.
 */
private data object IgnoreChromiumWebViewSetDebuggingEnabledWrongContextUsage : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is IncorrectContextUseViolation) return false

        return violation.stackTrace.any {
            it.className == "android.webkit.WebView" &&
                it.methodName == "setWebContentsDebuggingEnabled"
        }
    }
}

/**
 * Ignores an IncorrectContextUseViolation specifically caused by the
 * com.journeyapps.barcodescanner.RotationListener using the application context
 * to get the WindowManager, which is incorrect for UI operations.
 *
 * This is a known issue in the zxing-android-embedded library.
 * See:
 * - https://github.com/journeyapps/zxing-android-embedded/issues/762
 * - https://github.com/journeyapps/zxing-android-embedded/blob/d09b7c76c3124fbfbd096a65d60b1997f37ff90f/zxing-android-embedded/src/com/journeyapps/barcodescanner/RotationListener.java#L31
 */
private data object IgnoreBarcodeScannerRotationListenerWrongContextUsage : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is IncorrectContextUseViolation) return false

        return violation.stackTrace.any {
            it.className == "com.journeyapps.barcodescanner.RotationListener" &&
                it.methodName == "listen"
        }
    }
}

/**
 * Ignore a DiskReadViolation inside https://github.com/AppDevNext/ChangeLog while
 * loading default shared preferences.
 */
private data object IgnoreChangelogDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false
        return violation.stackTrace.any {
            it.className == "info.hannes.changelog.ChangeLog"
        }
    }
}

/**
 * Ignore a DiskReadViolation inside [NotificationHistoryFragment] while loading the XML that contains the
 * preferences used to make the UI of the screen. See the class for more details.
 */
private data object IgnoreNotificationHistoryFragmentLoadSharedPrefDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false
        return violation.stackTrace.any {
            it.className == "io.homeassistant.companion.android.settings.notification.NotificationHistoryFragment" &&
                it.methodName == "onCreatePreferences"
        }
    }
}

/**
 * Ignore a DiskReadViolation in Jetpack Compose's text selection context menu implementation.
 * This occurs when using SelectionContainer which enables text selection and shows a context menu.
 */
private data object IgnoreComposeTextContextMenuDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false
        return violation.stackTrace.any {
            it.className ==
                "androidx.compose.foundation.text.contextmenu.internal.AndroidTextContextMenuToolbarProvider"
        }
    }
}

/**
 * Ignore an [DiskWriteViolation] and [DiskReadViolation] in Android's ActivityThread vsync scheduling.
 * This occurs in the framework's internal vsync scheduling mechanism and is beyond
 * application control.
 */
private data object IgnoreActivityThreadVsyncDiskReadWrite : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskWriteViolation && violation !is DiskReadViolation) return false

        return violation.stackTrace.any {
            it.className == "android.app.ActivityThread" &&
                it.methodName == "scheduleVsyncSS"
        }
    }
}

/**
 * Ignore a [DiskReadViolation] in Samsung's InputRune framework component.
 * This occurs in Samsung's internal input configuration system and is beyond
 * application control.
 */
private data object IgnoreSamsungInputRuneDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false

        return violation.stackTrace.any {
            it.className == "com.samsung.android.rune.InputRune"
        }
    }
}

/**
 * Ignore a [DiskReadViolation] in Samsung Knox's ProKioskManager.
 * This occurs when Samsung Knox checks the kiosk state and is beyond application control.
 */
private data object IgnoreSamsungKnoxProKioskDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false

        return violation.stackTrace.any {
            it.className == "com.samsung.android.knox.custom.ProKioskManager" &&
                it.methodName == "getProKioskState"
        }
    }
}

/**
 * Ignore a [DiskReadViolation] caused by the OEM `isSpeg` package check reached through a Bluetooth
 * binder transaction.
 *
 * On some OEM ROMs (observed on Samsung), registering a Bluetooth adapter performs an internal
 * `File.exists()` check while handling the `registerAdapter` binder transaction. The StrictMode thread
 * policy propagates across the binder call, so the disk read is reported back to the app even though it
 * happens inside the system service and is beyond application control.
 *
 * The frame declaring the check varies between ROMs (`com.android.server.bluetooth.BluetoothManagerService`
 * and `android.content.pm.PackageManager` have both been seen), as does the method suffix
 * (`isSpeg`/`isSpegInWorking`), so the match is keyed on the vendor-specific method name rather than the class.
 */
private data object IgnoreSamsungSpegDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false

        return violation.stackTrace.any { it.methodName.startsWith("isSpeg") }
    }
}

/**
 * Ignore a [DiskReadViolation] in Android Auto/Automotive's ServiceConnectionManager.
 * This occurs when the Android Auto library initializes its service connection and is
 * beyond application control.
 */
private data object IgnoreAndroidAutoServiceConnectionDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false

        return violation.stackTrace.any {
            it.className == "androidx.car.app.activity.ServiceConnectionManager" &&
                it.methodName == "initializeService"
        }
    }
}

/**
 * Ignore a [DiskReadViolation] in Android Auto/Automotive's IRendererService.
 * This occurs when the Android Auto renderer service handles binder transactions and is
 * beyond application control.
 */
private data object IgnoreAndroidAutoRendererServiceDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false

        return violation.stackTrace.any {
            it.className == "androidx.car.app.activity.renderer.IRendererService\$Stub" &&
                it.methodName == "onTransact"
        }
    }
}

/**
 * Ignore a [DiskReadViolation] raised from MIUI's system font utilities (`miui.util.*`).
 *
 * MIUI reads font files from disk on the main thread from several sibling classes rooted at
 * `miui.util`:
 *   - `miui.util.font.FontSettings` — checks for custom theme fonts during Activity creation
 *   - `miui.util.font.MultiLangHelper` — probes multi-language font info during the first
 *     Compose text draw (runs inside a static initializer, fires once per process cold start,
 *     see https://github.com/WOOWTECH/woow_ha_app/issues/4)
 *   - `miui.util.TypefaceHelper` / `miui.util.TypefaceUtils` — resolves MIUI custom typefaces
 *     via `Font.Builder` when Compose renders a new typography variant (observed as an
 *     alpha15 crash on Mi 11 Ultra, HyperOS 2.0.7 CN, when opening the device/settings page)
 *
 * All of these are MIUI system font code beyond application control, and the frame that
 * declares the read varies between siblings, so the match is kept broad on the `miui.util.`
 * package prefix.
 */
private data object IgnoreMiuiFontDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false

        return violation.stackTrace.any {
            it.className.startsWith("miui.util.")
        }
    }
}

/**
 * Ignore a [DiskReadViolation] in MIUI's TurboSchedMonitor component.
 * This occurs when MIUI's performance scheduler checks file availability during
 * Choreographer frame rendering and is beyond application control.
 */
private data object IgnoreMiuiTurboSchedMonitorDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false

        return violation.stackTrace.any {
            it.className == "android.os.TurboSchedMonitorImpl"
        }
    }
}

/**
 * Ignore a [DiskReadViolation] in MIUI's Enterprise framework stub.
 *
 * On MIUI/HyperOS, any call to [android.app.NotificationManager.notify] transits through
 * `miui.enterprise.ApplicationHelperStub` to check MDM/enterprise notification restrictions.
 * The stub's static initializer resolves via `miui.enterprise.EpFrameworkFactory.get`, which
 * calls `isEnterpriseJarExists` — a `File.exists()` on the main-thread notification path.
 *
 * Observed as an alpha13 cold-start FailFast on Xiaomi Pad 6 (Android 14, HyperOS) when
 * WorkManager's `SystemForegroundService.notify` posted the first foreground service
 * notification (frames: `androidx.work.impl.foreground.SystemForegroundService.notify` →
 * `NotificationManager.notify` → `miui.enterprise.ApplicationHelperStub.<clinit>`).
 *
 * The frame that declares the read may vary between `EpFrameworkFactory` and other siblings
 * in the same MIUI enterprise stub package, so the match is kept broad on the
 * `miui.enterprise.` package prefix — all of which is MIUI system MDM code beyond application
 * control.
 */
private data object IgnoreMiuiEnterpriseFrameworkDiskRead : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation) return false

        return violation.stackTrace.any {
            it.className.startsWith("miui.enterprise.")
        }
    }
}

/**
 * Ignore a [DiskWriteViolation] and [DiskReadViolation] from AppCompat's locale auto-storage sync.
 *
 * On every cold activity start, [androidx.appcompat.app.AppCompatDelegateImpl.attachBaseContext2]
 * synchronously calls [androidx.appcompat.app.AppCompatDelegate.syncRequestedAndStoredLocales],
 * which deletes (or writes) the persisted locales file via
 * [androidx.core.app.AppLocalesStorageHelper.persistLocales] on the main thread. The delete itself
 * is the [DiskWriteViolation]; the [DiskReadViolation] comes from the framework's
 * `Context.deleteFile` first probing `filesDir` to ensure it exists. The app opts in to this
 * storage via the `AppLocalesMetadataHolderService` manifest entry, so both violations are
 * intrinsic to the library and beyond application control.
 */
private data object IgnoreAppCompatPersistLocalesDiskReadWrite : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskWriteViolation && violation !is DiskReadViolation) return false

        return violation.stackTrace.any {
            it.className == "androidx.core.app.AppLocalesStorageHelper" &&
                it.methodName == "persistLocales"
        } &&
            violation.stackTrace.any {
                it.className == "androidx.appcompat.app.AppCompatDelegate" &&
                    it.methodName == "syncRequestedAndStoredLocales"
            } &&
            violation.stackTrace.any {
                it.className == "androidx.appcompat.app.AppCompatDelegateImpl" &&
                    it.methodName == "attachBaseContext2"
            }
    }
}

/**
 * Ignore a [DiskWriteViolation] in Chromium's WebView when the Android KeyStore
 * performs a signing operation during a client certificate TLS handshake.
 * The disk write happens inside `KeyStoreSecurityLevel.createOperation` which is
 * beyond application control.
 */
private data object IgnoreChromiumKeyStoreDiskWrite : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskWriteViolation) return false

        return violation.stackTrace.any {
            it.className == "android.security.KeyStoreSecurityLevel" &&
                it.methodName == "createOperation"
        } &&
            violation.stackTrace.any {
                it.fileName?.startsWith("chromium-") == true
            }
    }
}

/**
 * Ignore a [DiskReadViolation] or [DiskWriteViolation] raised from MediaTek OEM performance /
 * boost framework packages (`com.mediatek.*`).
 *
 * On Xiaomi/MTK devices (observed on POCO C85, SoC `mt6768` Helio G88, HyperOS 2.0), the vendor
 * PowerHAL and BoostFwk performance-hint frameworks perform synchronous `File.exists()` /
 * `File.length()` probes on the main thread to classify the foreground app as game vs non-game.
 * The reads propagate back to the app via in-process callbacks, fatally crashing debug builds
 * through the FailFast handler. Two distinct call paths were seen on alpha14:
 *
 *   - `com.mediatek.boostfwk.utils.Util.isGameApp` (via `perfHint` from touch input dispatch)
 *   - `com.mediatek.scnmodule.ScnModule.isGameAppFileSize` (via `PowerHalWrapper.amsBoostNotify`
 *     from Activity state changes)
 *
 * The frame declaring the read varies between MediaTek sibling packages, so the match is kept
 * broad on the `com.mediatek.` package prefix — all of which is MTK vendor framework code
 * beyond application control.
 */
private data object IgnoreMediatekFrameworkDiskAccess : IgnoreViolationRule {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun shouldIgnore(violation: Violation): Boolean {
        if (violation !is DiskReadViolation && violation !is DiskWriteViolation) return false

        return violation.stackTrace.any {
            it.className.startsWith("com.mediatek.")
        }
    }
}
