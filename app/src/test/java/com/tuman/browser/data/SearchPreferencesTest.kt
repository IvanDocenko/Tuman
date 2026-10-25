package com.tuman.browser.data

import org.junit.Assert.*
import org.junit.Test

class SearchPreferencesTest {
    @Test fun englishDoesNotOfferYandex() { assertFalse(SearchPreferences.available(Language.EN).contains(SearchEngine.YANDEX)) }
    @Test fun russianOffersYandex() { assertTrue(SearchPreferences.available(Language.RU).contains(SearchEngine.YANDEX)) }
    @Test fun removedBingMigratesToVisibleDefault() { Language.entries.forEach { assertEquals(SearchEngine.DUCKDUCKGO, SearchPreferences.resolve("BING", it)) } }
    @Test fun switchingToEnglishDropsYandex() { assertEquals(SearchEngine.DUCKDUCKGO, SearchPreferences.resolve("YANDEX", Language.EN)) }
    @Test fun russianRetainsYandex() { assertEquals(SearchEngine.YANDEX, SearchPreferences.resolve("YANDEX", Language.RU)) }
    @Test fun customSurvivesLanguageChange() { Language.entries.forEach { assertEquals(SearchEngine.CUSTOM, SearchPreferences.resolve("CUSTOM", it)) } }
    @Test fun effectiveSearchUsesVisibleProvider() {
        assertTrue(UrlPolicy.resolve("hello world", Customization(language = Language.EN, searchEngine = SearchEngine.YANDEX)).startsWith(SearchEngine.DUCKDUCKGO.prefix))
        assertTrue(UrlPolicy.resolve("hello world", Customization(language = Language.RU, searchEngine = SearchEngine.YANDEX)).startsWith(SearchEngine.YANDEX.prefix))
    }
}
