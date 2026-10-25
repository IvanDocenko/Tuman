package com.tuman.browser.engine

import com.tuman.browser.data.*
import org.junit.Assert.*
import org.junit.Test
import org.mozilla.geckoview.ContentBlocking

class ProtectionPolicyTest {
    @Test fun shieldsCanBeIndividuallyDisabled() {
        val s = Customization(blockSocialTrackers = false, blockFingerprinters = false, blockCryptominers = false, blockEmailTrackers = false, blockContentTrackers = false)
        val flags = ProtectionPolicy.antiTracking(s)
        assertEquals(0, flags and (ContentBlocking.AntiTracking.SOCIAL or ContentBlocking.AntiTracking.STP or ContentBlocking.AntiTracking.FINGERPRINTING or ContentBlocking.AntiTracking.CRYPTOMINING or ContentBlocking.AntiTracking.EMAIL or ContentBlocking.AntiTracking.CONTENT))
        assertTrue(flags and ContentBlocking.AntiTracking.AD != 0)
        assertTrue(flags and ContentBlocking.AntiTracking.ANALYTIC != 0)
    }
    @Test fun maximumOverridesDisabledShields() {
        val s = Customization(protection = Protection.MAXIMUM, blockSocialTrackers = false, blockFingerprinters = false, blockCryptominers = false, blockEmailTrackers = false, blockContentTrackers = false)
        assertEquals(ContentBlocking.AntiTracking.STRICT or ContentBlocking.AntiTracking.STP, ProtectionPolicy.antiTracking(s))
        assertFalse(ProtectionPolicy.javascriptAllowed(s))
    }
    @Test fun strictIsolatesCookiesByDefault() {
        assertEquals(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS, ProtectionPolicy.cookieBehavior(Customization()))
    }
    @Test fun strongerProfileCannotBeWeakenedByCookiePicker() {
        assertEquals(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY, ProtectionPolicy.cookieBehavior(Customization(protection = Protection.HARDENED)))
    }
    @Test fun blockingAllCookiesTakesPriority() {
        Protection.entries.forEach { assertEquals(ContentBlocking.CookieBehavior.ACCEPT_NONE, ProtectionPolicy.cookieBehavior(Customization(protection = it, cookiePolicy = CookiePolicy.BLOCK_ALL))) }
    }
    @Test fun javascriptChoiceIsRetainedAcrossProfiles() {
        assertTrue(ProtectionPolicy.javascriptAllowed(Customization()))
        assertFalse(ProtectionPolicy.javascriptAllowed(Customization(javascript = false)))
        assertTrue(ProtectionPolicy.javascriptAllowed(Customization(protection = Protection.MAXIMUM).copy(protection = Protection.STRICT)))
    }
}
