package com.tuman.browser.engine

import android.content.Context
import android.os.Looper
import androidx.annotation.MainThread
import android.util.AtomicFile
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.io.File

object TumanGeckoRuntime {
    private var instance: GeckoRuntime? = null

    // GeckoRuntime belongs to the process, not to an Activity or a tab.
    @MainThread
    fun get(context: Context): GeckoRuntime {
        check(Looper.myLooper() == Looper.getMainLooper())
        return instance ?: create(context.applicationContext).also { instance = it }
    }

    private fun create(context: Context): GeckoRuntime {
        val config = File(context.noBackupFilesDir, "gecko-config.yaml")
        val bytes = context.assets.open("gecko-config.yaml").use { it.readBytes() }
        val atomic = AtomicFile(config)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (failure: Throwable) {
            atomic.failWrite(stream)
            throw failure
        }
        val blocking = ContentBlocking.Settings.Builder()
            .antiTracking(ContentBlocking.AntiTracking.STRICT or ContentBlocking.AntiTracking.STP)
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
            .strictSocialTrackingProtection(true)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .build()
        val settings = GeckoRuntimeSettings.Builder()
            .configFilePath(config.absolutePath)
            .contentBlocking(blocking)
            .remoteDebuggingEnabled(false)
            .consoleOutput(false)
            .debugLogging(false)
            .aboutConfigEnabled(false)
            .enterpriseRootsEnabled(false)
            .globalPrivacyControlEnabled(true)
            .allowInsecureConnections(GeckoRuntimeSettings.HTTPS_ONLY)
            .trustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_ONLY)
            .trustedRecursiveResolverUri("https://mozilla.cloudflare-dns.com/dns-query")
            .build()
        return GeckoRuntime.create(context, settings).also {
            it.notifyTelemetryPrefChanged(false)
        }
    }
}
