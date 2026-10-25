package com.tuman.browser.data

/** Removed or unavailable providers fall back to the same visible default on upgrade/language change. */
object SearchPreferences {
    fun available(language: Language): List<SearchEngine> = SearchEngine.entries.filter {
        it != SearchEngine.YANDEX || language == Language.RU
    }
    fun resolve(saved: String?, language: Language): SearchEngine =
        available(language).firstOrNull { it.name == saved } ?: SearchEngine.DUCKDUCKGO
}
