package com.tuman.browser.data

import org.junit.Assert.*
import org.junit.Test

class TrackingLinkTest {
    @Test fun removesOnlyTrackingParameters() {
        assertEquals("https://example.org/a%20b?q=a%26b#part", UrlPolicy.cleanTrackingUrl("https://example.org/a%20b?utm_source=x&q=a%26b&fbclid=y#part"))
    }
    @Test fun removesQueryMarkerWhenNothingRemains() { assertEquals("https://example.org/#part", UrlPolicy.cleanTrackingUrl("https://example.org/?gclid=x#part")) }
    @Test fun normalAddressesRemainUntouched() { assertEquals("https://example.org?q=a+b&item=1&item=2", UrlPolicy.cleanTrackingUrl("https://example.org?q=a+b&item=1&item=2")) }
    @Test fun namesAreDecodedAndCaseInsensitive() { assertEquals("https://example.org/", UrlPolicy.cleanTrackingUrl("https://example.org/?UTM_SOURCE=x&%66bclid=abc")) }
    @Test fun fragmentTextIsNotTreatedAsQuery() { assertEquals("https://example.org/#utm_source=x", UrlPolicy.cleanTrackingUrl("https://example.org/#utm_source=x")) }
    @Test fun switchControlsNavigation() {
        val url = "https://example.org/?utm_source=mail"
        assertEquals(url, UrlPolicy.resolve(url, Customization(stripTrackingParameters = false)))
        assertEquals("https://example.org/", UrlPolicy.resolve(url, Customization(stripTrackingParameters = true)))
    }
}
