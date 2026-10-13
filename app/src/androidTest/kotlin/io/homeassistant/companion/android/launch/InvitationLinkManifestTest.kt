package io.homeassistant.companion.android.launch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationLinkManifestTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun bothInvitationPathsResolveToThisApp() {
        for (path in listOf("/invite", "/invite/")) {
            assertTrue(resolves("https://aiot.woowtech.io$path#url=https%3A%2F%2Fha.example"))
        }
    }

    @Test
    fun insecureOrLookalikeInvitationHostsDoNotResolveToThisApp() {
        assertFalse(resolves("http://aiot.woowtech.io/invite"))
        assertFalse(resolves("https://aiot.woowtech.io.evil.example/invite"))
    }

    private fun resolves(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(context.packageName)
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
    }
}
