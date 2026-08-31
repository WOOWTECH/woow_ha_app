package io.homeassistant.companion.android.util

import android.os.strictmode.DiskReadViolation
import android.os.strictmode.DiskWriteViolation
import android.os.strictmode.Violation
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the stack trace shapes that [threadPolicyIgnoredViolationRules] is allowed to suppress.
 *
 * These rules match on the class and method names of platform or third party internals, so they
 * silently stop working when that code changes. The traces below were captured from real devices
 * and are kept verbatim: if a library or ROM upgrade changes the frames, this test is the record
 * of what the rules were written against.
 *
 * Suppressing a violation removes a hole from the StrictMode safety net, so the negative cases
 * matter as much as the positive ones.
 */
class IgnoreViolationRulesTest {

    @Test
    fun `Given the real AppCompat persist locales trace when checking thread rules then violation is ignored`() {
        assertTrue(isIgnoredByThreadPolicy(diskReadViolation(APP_COMPAT_PERSIST_LOCALES_TRACE)))
    }

    @Test
    fun `Given the AppCompat persist locales trace as a write when checking thread rules then violation is ignored`() {
        // The same code path raises a DiskWriteViolation when a language is set rather than cleared.
        assertTrue(isIgnoredByThreadPolicy(diskWriteViolation(APP_COMPAT_PERSIST_LOCALES_TRACE)))
    }

    @Test
    fun `Given only two of the three required AppCompat frames when checking thread rules then violation is not ignored`() {
        val withoutAttachBaseContext = APP_COMPAT_PERSIST_LOCALES_TRACE.filterNot {
            it.methodName == "attachBaseContext2"
        }.toTypedArray()

        assertFalse(isIgnoredByThreadPolicy(diskReadViolation(withoutAttachBaseContext)))
    }

    @Test
    fun `Given an unrelated shared preferences disk write when checking thread rules then violation is not ignored`() {
        val unrelated = arrayOf(
            StackTraceElement("android.app.SharedPreferencesImpl\$EditorImpl", "commitToMemory", "SharedPreferencesImpl.java", 632),
            StackTraceElement("android.app.SharedPreferencesImpl\$EditorImpl", "commit", "SharedPreferencesImpl.java", 682),
            StackTraceElement("io.homeassistant.companion.android.Example", "save", "Example.kt", 12),
        )

        assertFalse(isIgnoredByThreadPolicy(diskWriteViolation(unrelated)))
    }

    @Test
    fun `Given an empty stack trace when checking thread rules then violation is not ignored`() {
        assertFalse(isIgnoredByThreadPolicy(diskReadViolation(emptyArray())))
    }

    @Test
    fun `Given the real MIUI MultiLangHelper font trace when checking thread rules then violation is ignored`() {
        assertTrue(isIgnoredByThreadPolicy(diskReadViolation(MIUI_MULTILANG_FONT_TRACE)))
    }

    @Test
    fun `Given a MIUI FontSettings font trace when checking thread rules then violation is ignored`() {
        // Regression: broadening the rule to the miui.util.font. package must keep covering the
        // original FontSettings class the rule was first written for.
        assertTrue(isIgnoredByThreadPolicy(diskReadViolation(MIUI_FONT_SETTINGS_TRACE)))
    }

    @Test
    fun `Given a MIUI class outside miui util when checking thread rules then violation is not ignored`() {
        // The rule deliberately covers the whole miui.util. package (see IgnoreMiuiFontDiskRead),
        // but must not swallow MIUI disk reads from entirely different MIUI packages: that would
        // turn a targeted exemption for system font code into a blanket pass for the vendor.
        val nearMiss = arrayOf(
            StackTraceElement("android.os.StrictMode\$AndroidBlockGuardPolicy", "onReadFromDisk", "StrictMode.java", 1666),
            StackTraceElement("libcore.io.BlockGuardOs", "access", "BlockGuardOs.java", 74),
            StackTraceElement("java.io.File", "exists", "File.java", 829),
            StackTraceElement("miui.securitycenter.PermissionManager", "check", "PermissionManager.java", 42),
        )

        assertFalse(isIgnoredByThreadPolicy(diskReadViolation(nearMiss)))
    }

    private fun isIgnoredByThreadPolicy(violation: Violation): Boolean {
        return threadPolicyIgnoredViolationRules.any { it.shouldIgnore(violation) }
    }

    private fun diskReadViolation(stackTrace: Array<StackTraceElement>): DiskReadViolation = mockk<DiskReadViolation>().also { every { it.stackTrace } returns stackTrace }

    private fun diskWriteViolation(stackTrace: Array<StackTraceElement>): DiskWriteViolation = mockk<DiskWriteViolation>().also { every { it.stackTrace } returns stackTrace }
}

/**
 * Captured verbatim from a `DiskReadViolation` on Android 12L (API 32) during a cold start of
 * `LaunchActivity`, the crash this rule was written for.
 */
private val APP_COMPAT_PERSIST_LOCALES_TRACE = arrayOf(
    StackTraceElement("android.os.StrictMode\$AndroidBlockGuardPolicy", "onReadFromDisk", "StrictMode.java", 1659),
    StackTraceElement("libcore.io.BlockGuardOs", "access", "BlockGuardOs.java", 74),
    StackTraceElement("java.io.File", "exists", "File.java", 813),
    StackTraceElement("android.app.ContextImpl", "getDataDir", "ContextImpl.java", 2909),
    StackTraceElement("android.app.ContextImpl", "getFilesDir", "ContextImpl.java", 785),
    StackTraceElement("android.app.ContextImpl", "deleteFile", "ContextImpl.java", 734),
    StackTraceElement("androidx.core.app.AppLocalesStorageHelper", "persistLocales", "AppLocalesStorageHelper.java", 123),
    StackTraceElement(
        "androidx.appcompat.app.AppCompatDelegate",
        "syncRequestedAndStoredLocales",
        "AppCompatDelegate.java",
        994,
    ),
    StackTraceElement(
        "androidx.appcompat.app.AppCompatDelegateImpl",
        "attachBaseContext2",
        "AppCompatDelegateImpl.java",
        403,
    ),
    StackTraceElement("androidx.appcompat.app.AppCompatActivity", "attachBaseContext", "AppCompatActivity.java", 137),
    StackTraceElement("android.app.Activity", "attach", "Activity.java", 7949),
)

/**
 * Captured verbatim from a `DiskReadViolation` on a Xiaomi Pad 6 (Android 14, API 34, MIUI) during
 * the first Compose text draw of a cold start, the crash reported in
 * [issue #4](https://github.com/WOOWTECH/woow_ha_app/issues/4). `MultiLangHelper.<clinit>` reads a
 * font file from disk, so the violation fires once per process on every cold start.
 */
private val MIUI_MULTILANG_FONT_TRACE = arrayOf(
    StackTraceElement("android.os.StrictMode\$AndroidBlockGuardPolicy", "onReadFromDisk", "StrictMode.java", 1666),
    StackTraceElement("libcore.io.BlockGuardOs", "access", "BlockGuardOs.java", 74),
    StackTraceElement("java.io.File", "exists", "File.java", 829),
    StackTraceElement("miui.util.font.MultiLangHelper", "initMultiLangInfo", "MultiLangHelper.java", 91),
    StackTraceElement("miui.util.font.MultiLangHelper", "<clinit>", "MultiLangHelper.java", 85),
    StackTraceElement("android.graphics.LayoutEngineStubImpl", "drawTextBegin", "LayoutEngineStubImpl.java", 26),
    StackTraceElement("android.text.Layout", "drawText", "Layout.java", 608),
    StackTraceElement("android.text.Layout", "draw", "Layout.java", 421),
    StackTraceElement("androidx.compose.ui.text.android.TextLayout", "paint", "TextLayout.android.kt", 820),
    StackTraceElement("androidx.compose.ui.text.AndroidParagraph", "paint", "AndroidParagraph.android.kt", 587),
)

/**
 * A representative `DiskReadViolation` for the original `miui.util.font.FontSettings` frame the MIUI
 * font rule was first written against, so widening the rule to the package prefix keeps a regression
 * guard on that sibling class.
 */
private val MIUI_FONT_SETTINGS_TRACE = arrayOf(
    StackTraceElement("android.os.StrictMode\$AndroidBlockGuardPolicy", "onReadFromDisk", "StrictMode.java", 1666),
    StackTraceElement("libcore.io.BlockGuardOs", "access", "BlockGuardOs.java", 74),
    StackTraceElement("java.io.File", "exists", "File.java", 829),
    StackTraceElement("miui.util.font.FontSettings", "isThemeFontEnable", "FontSettings.java", 55),
    StackTraceElement("android.app.Activity", "attach", "Activity.java", 7949),
)
