package com.tuman.browser.engine

import com.tuman.browser.data.*
import org.mozilla.geckoview.ContentBlocking

object ProtectionPolicy {
    fun cookieBehavior(settings: Customization): Int = when {
        settings.cookiePolicy == CookiePolicy.BLOCK_ALL -> ContentBlocking.CookieBehavior.ACCEPT_NONE
        settings.cookiePolicy == CookiePolicy.BLOCK_THIRD_PARTY || settings.protection != Protection.STRICT -> ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY
        else -> ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
    }
    fun antiTracking(settings: Customization): Int {
        var flags = ContentBlocking.AntiTracking.AD or ContentBlocking.AntiTracking.ANALYTIC or ContentBlocking.AntiTracking.TEST
        val maximum = settings.protection == Protection.MAXIMUM
        if (settings.blockSocialTrackers || maximum) flags = flags or ContentBlocking.AntiTracking.SOCIAL or ContentBlocking.AntiTracking.STP
        if (settings.blockFingerprinters || maximum) flags = flags or ContentBlocking.AntiTracking.FINGERPRINTING
        if (settings.blockCryptominers || maximum) flags = flags or ContentBlocking.AntiTracking.CRYPTOMINING
        if (settings.blockEmailTrackers || maximum) flags = flags or ContentBlocking.AntiTracking.EMAIL
        if (settings.blockContentTrackers || maximum) flags = flags or ContentBlocking.AntiTracking.CONTENT
        return flags
    }
    fun javascriptAllowed(settings: Customization) = settings.javascript && settings.protection != Protection.MAXIMUM
}
