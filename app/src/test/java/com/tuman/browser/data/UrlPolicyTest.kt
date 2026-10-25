package com.tuman.browser.data

import org.junit.Assert.*
import org.junit.Test

class UrlPolicyTest {
    @Test fun cleartextInputIsUpgraded() {
        assertEquals("https://example.com/path?q=1", UrlPolicy.resolve("http://example.com/path?q=1", SearchEngine.DUCKDUCKGO))
    }
    @Test fun searchQueryCannotInjectParameters() {
        assertEquals("https://duckduckgo.com/?q=hello+%26+privacy", UrlPolicy.resolve("hello & privacy", SearchEngine.DUCKDUCKGO))
    }
    @Test fun homePageRejectsCredentialsAndUnsafeSchemes() {
        listOf("http://example.com", "javascript:alert(1)", "file:///etc/passwd", "https://user:password@example.com", "https://").forEach {
            assertFalse(it, UrlPolicy.isHomePage(it))
        }
    }
    @Test fun pastedJavascriptNeverExecutes() {
        assertThrows(IllegalArgumentException::class.java) { UrlPolicy.resolve("javascript:alert(1)", SearchEngine.BRAVE) }
    }
    @Test fun domainBecomesHttps() {
        assertEquals("https://example.com", UrlPolicy.resolve(" example.com ", SearchEngine.STARTPAGE))
    }
    @Test fun emptyInputOpensLocalHome() {
        assertEquals("about:blank", UrlPolicy.resolve("  ", SearchEngine.BRAVE))
    }
    @Test fun externalSchemesAreDenied() {
        listOf("intent://example", "file:///sdcard/foo", "data:text/html,hello", "javascript:void(0)", "about:config").forEach {
            assertFalse(it, UrlPolicy.allowsNavigation(it))
        }
    }
    @Test fun internationalDomainIsNormalized() {
        assertEquals("https://xn--e1afmkfd.xn--p1ai/", UrlPolicy.resolve("https://пример.рф/", SearchEngine.BRAVE))
    }
    @Test fun hostAndPortAreSupported() {
        assertEquals("https://example.com:8443/path", UrlPolicy.resolve("example.com:8443/path", SearchEngine.BRAVE))
    }
    @Test fun encodedPathIsNotDoubleEncoded() {
        assertEquals("https://example.com/a%20b", UrlPolicy.resolve("https://example.com/a%20b", SearchEngine.BRAVE))
    }
}
