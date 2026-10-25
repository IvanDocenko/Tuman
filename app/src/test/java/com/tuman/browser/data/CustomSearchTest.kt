package com.tuman.browser.data

import org.junit.Assert.*
import org.junit.Test

class CustomSearchTest {
    @Test fun customSearchEncodesUnicodeAndDelimiters() {
        val settings = Customization(searchEngine = SearchEngine.CUSTOM, customSearchTemplate = "https://search.example/find?q={query}&lang=en")
        assertEquals("https://search.example/find?q=%D1%82%D1%83%D0%BC%D0%B0%D0%BD+%26+%23%3F&lang=en", UrlPolicy.resolve("туман & #?", settings))
    }
    @Test fun placeholderMayBeInPath() {
        assertEquals("https://example.com/find/hello+world", UrlPolicy.resolve("hello world", SearchEngine.CUSTOM, "https://example.com/find/{query}"))
    }
    @Test fun templatesRejectUnsafeSchemesAndCredentials() {
        listOf("http://example.com?q={query}", "javascript:{query}", "https://user:pass@example.com/?q={query}", "file:///{query}").forEach { assertFalse(it, UrlPolicy.isSearchTemplate(it)) }
    }
    @Test fun templatesRequireExactlyOnePlaceholder() {
        listOf("https://example.com?q=hello", "https://example.com/{query}?q={query}", "https://example.com/?q={text}").forEach { assertFalse(it, UrlPolicy.isSearchTemplate(it)) }
    }
    @Test fun placeholderCannotChangeHostOrFragment() {
        listOf("https://{query}.example.com/search", "https://example.com#{query}", "https://{query}/").forEach { assertFalse(it, UrlPolicy.isSearchTemplate(it)) }
    }
    @Test fun invalidCustomTemplateFailsInsteadOfLoadingUntrustedScheme() {
        assertThrows(IllegalArgumentException::class.java) { UrlPolicy.resolve("test", SearchEngine.CUSTOM, "javascript:{query}") }
    }
    @Test fun normalUrlsBypassCustomSearch() {
        assertEquals("https://example.org/path", UrlPolicy.resolve("example.org/path", Customization(searchEngine = SearchEngine.CUSTOM)))
    }
    @Test fun internationalCustomHostIsNormalized() {
        assertEquals("https://xn--e1afmkfd.xn--p1ai/?q=test", UrlPolicy.resolve("test", SearchEngine.CUSTOM, "https://пример.рф/?q={query}"))
    }
    @Test fun allBuiltInEnginesEncodeQuery() {
        SearchEngine.entries.filter { it != SearchEngine.CUSTOM }.forEach {
            val result = UrlPolicy.resolve("hello & world", it)
            assertTrue(result.startsWith("https://")); assertTrue(result.endsWith("hello+%26+world"))
        }
    }
    @Test fun defaultCustomTemplateWorks() {
        assertTrue(UrlPolicy.isSearchTemplate(Customization().customSearchTemplate))
        assertEquals(Language.EN, Customization().language)
    }
}
