package com.tuman.browser.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.customizationStore by preferencesDataStore("tuman_customization")

enum class ToolbarPlacement { BOTTOM, TOP }
enum class TabPlacement(val label: String) { BOTTOM("Снизу"), TOP("Сверху"), HIDDEN("Скрыта") }
enum class Protection(val label: String, val description: String) {
    STRICT("Строгая", "ETP Strict, изоляция cookies, DoH и блокировка WebRTC"),
    HARDENED("Усиленная", "Строгая защита и запрет всех сторонних cookies"),
    MAXIMUM("Максимальная", "Усиленная защита и отключённый JavaScript; сайты могут не работать")
}
enum class Language { EN, RU;
    fun text(en: String, ru: String) = if (this == RU) ru else en
}
enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class ThemePalette(val accent: Long, val darkBackground: Long, val darkSurface: Long, val lightBackground: Long) {
    MIDNIGHT(0xFF75E5DA, 0xFF090E19, 0xFF151E2D, 0xFFF3F6FA),
    AURORA(0xFFB9A1FF, 0xFF100D20, 0xFF231B36, 0xFFF6F1FC),
    FOREST(0xFF9FD4AD, 0xFF091611, 0xFF172B22, 0xFFF0F6EE),
    OCEAN(0xFF8ACBFF, 0xFF071521, 0xFF142B3D, 0xFFEDF6FC),
    EMBER(0xFFFFBB91, 0xFF1D100D, 0xFF35211B, 0xFFFFF4EC),
    ROSE(0xFFF4ACC9, 0xFF1C0E18, 0xFF321D2A, 0xFFFFF1F6)
}
enum class CookiePolicy { ISOLATE, BLOCK_THIRD_PARTY, BLOCK_ALL }
enum class SearchEngine(val label: String, val prefix: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BRAVE("Brave Search", "https://search.brave.com/search?q="),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query="),
    GOOGLE("Google", "https://www.google.com/search?q="),
    YANDEX("Yandex", "https://yandex.com/search/?text="),
    CUSTOM("Custom", "")
}
data class Customization(
    val toolbarPlacement: ToolbarPlacement = ToolbarPlacement.BOTTOM,
    val palette: ThemePalette = ThemePalette.MIDNIGHT,
    val blockSocialTrackers: Boolean = true,
    val blockFingerprinters: Boolean = true,
    val blockCryptominers: Boolean = true,
    val blockEmailTrackers: Boolean = true,
    val blockContentTrackers: Boolean = true,
    val cookiePolicy: CookiePolicy = CookiePolicy.ISOLATE,
    val stripTrackingParameters: Boolean = true,
    val blockAutoplay: Boolean = true,
    val suspendBackgroundMedia: Boolean = true,
    val isOnboarded: Boolean = false,
    val accentArgb: Int = 0xFF75E5DA.toInt(),
    val tabPlacement: TabPlacement = TabPlacement.BOTTOM,
    val opacity: Float = 0.82f,
    val blur: Float = 18f,
    val homePage: String = "about:blank",
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val protection: Protection = Protection.STRICT,
    val language: Language = Language.EN,
    val theme: ThemeMode = ThemeMode.DARK,
    val webTheme: ThemeMode = ThemeMode.SYSTEM,
    val animations: Boolean = true,
    val intro: Boolean = true,
    val ambientFog: Boolean = true,
    val showClock: Boolean = true,
    val showShortcuts: Boolean = true,
    val showTagline: Boolean = true,
    val hideToolbar: Boolean = true,
    val uiScale: Float = 1f,
    val webTextScale: Float = 1f,
    val javascript: Boolean = true,
    val safeBrowsing: Boolean = true,
    val allowScreenshots: Boolean = false,
    val allowPopups: Boolean = false,
    val restoreTabs: Boolean = true,
    val desktopMode: Boolean = false,
    val sleepMinutes: Int = 3,
    val customSearchName: String = "My search",
    val customSearchTemplate: String = "https://duckduckgo.com/?q={query}"
)

class CustomizationDataStore(context: Context) {
    private val store = context.applicationContext.customizationStore
    private object Keys {
        val toolbarPlacement = stringPreferencesKey("toolbarPlacement")
        val palette = stringPreferencesKey("palette")
        val blockSocialTrackers = booleanPreferencesKey("blockSocialTrackers")
        val blockFingerprinters = booleanPreferencesKey("blockFingerprinters")
        val blockCryptominers = booleanPreferencesKey("blockCryptominers")
        val blockEmailTrackers = booleanPreferencesKey("blockEmailTrackers")
        val blockContentTrackers = booleanPreferencesKey("blockContentTrackers")
        val cookiePolicy = stringPreferencesKey("cookiePolicy")
        val stripTrackingParameters = booleanPreferencesKey("stripTrackingParameters")
        val blockAutoplay = booleanPreferencesKey("blockAutoplay")
        val suspendBackgroundMedia = booleanPreferencesKey("suspendBackgroundMedia")
        val language = stringPreferencesKey("language")
        val theme = stringPreferencesKey("theme")
        val webTheme = stringPreferencesKey("webTheme")
        val animations = booleanPreferencesKey("animations")
        val intro = booleanPreferencesKey("intro")
        val ambientFog = booleanPreferencesKey("ambientFog")
        val showClock = booleanPreferencesKey("showClock")
        val showShortcuts = booleanPreferencesKey("showShortcuts")
        val showTagline = booleanPreferencesKey("showTagline")
        val hideToolbar = booleanPreferencesKey("hideToolbar")
        val uiScale = floatPreferencesKey("uiScale")
        val webTextScale = floatPreferencesKey("webTextScale")
        val javascript = booleanPreferencesKey("javascript")
        val safeBrowsing = booleanPreferencesKey("safeBrowsing")
        val allowScreenshots = booleanPreferencesKey("allowScreenshots")
        val allowPopups = booleanPreferencesKey("allowPopups")
        val restoreTabs = booleanPreferencesKey("restoreTabs")
        val desktopMode = booleanPreferencesKey("desktopMode")
        val sleepMinutes = intPreferencesKey("sleepMinutes")
        val customSearchName = stringPreferencesKey("customSearchName")
        val customSearchTemplate = stringPreferencesKey("customSearchTemplate")
        val onboarded = booleanPreferencesKey("isOnboarded")
        val accent = intPreferencesKey("accentArgb")
        val placement = stringPreferencesKey("tabPlacement")
        val opacity = floatPreferencesKey("opacity")
        val blur = floatPreferencesKey("blur")
        val home = stringPreferencesKey("homePage")
        val search = stringPreferencesKey("searchEngine")
        val protection = stringPreferencesKey("protection")
    }
    val settings: Flow<Customization> = store.data.map { p ->
        Customization(
            toolbarPlacement = ToolbarPlacement.entries.firstOrNull { it.name == p[Keys.toolbarPlacement] } ?: ToolbarPlacement.BOTTOM,
            palette = ThemePalette.entries.firstOrNull { it.name == p[Keys.palette] } ?: ThemePalette.MIDNIGHT,
            blockSocialTrackers = p[Keys.blockSocialTrackers] ?: true,
            blockFingerprinters = p[Keys.blockFingerprinters] ?: true,
            blockCryptominers = p[Keys.blockCryptominers] ?: true,
            blockEmailTrackers = p[Keys.blockEmailTrackers] ?: true,
            blockContentTrackers = p[Keys.blockContentTrackers] ?: true,
            cookiePolicy = CookiePolicy.entries.firstOrNull { it.name == p[Keys.cookiePolicy] } ?: CookiePolicy.ISOLATE,
            stripTrackingParameters = p[Keys.stripTrackingParameters] ?: true,
            blockAutoplay = p[Keys.blockAutoplay] ?: true,
            suspendBackgroundMedia = p[Keys.suspendBackgroundMedia] ?: true,
            isOnboarded = p[Keys.onboarded] ?: false,
            accentArgb = p[Keys.accent] ?: 0xFF75E5DA.toInt(),
            tabPlacement = TabPlacement.entries.firstOrNull { it.name == p[Keys.placement] } ?: TabPlacement.BOTTOM,
            opacity = (p[Keys.opacity] ?: 0.82f).coerceIn(0.45f, 1f),
            blur = (p[Keys.blur] ?: 18f).coerceIn(0f, 40f),
            homePage = p[Keys.home]?.takeIf(UrlPolicy::isHomePage) ?: "about:blank",
            searchEngine = SearchPreferences.resolve(p[Keys.search], Language.entries.firstOrNull { it.name == p[Keys.language] } ?: Language.EN),
            protection = Protection.entries.firstOrNull { it.name == p[Keys.protection] } ?: Protection.STRICT,
            language = Language.entries.firstOrNull { it.name == p[Keys.language] } ?: Language.EN,
            theme = ThemeMode.entries.firstOrNull { it.name == p[Keys.theme] } ?: ThemeMode.DARK,
            webTheme = ThemeMode.entries.firstOrNull { it.name == p[Keys.webTheme] } ?: ThemeMode.SYSTEM,
            animations = p[Keys.animations] ?: true,
            intro = p[Keys.intro] ?: true,
            ambientFog = p[Keys.ambientFog] ?: true,
            showClock = p[Keys.showClock] ?: true,
            showShortcuts = p[Keys.showShortcuts] ?: true,
            showTagline = p[Keys.showTagline] ?: true,
            hideToolbar = p[Keys.hideToolbar] ?: true,
            uiScale = p[Keys.uiScale] ?: 1f,
            webTextScale = p[Keys.webTextScale] ?: 1f,
            javascript = p[Keys.javascript] ?: true,
            safeBrowsing = p[Keys.safeBrowsing] ?: true,
            allowScreenshots = p[Keys.allowScreenshots] ?: false,
            allowPopups = p[Keys.allowPopups] ?: false,
            restoreTabs = p[Keys.restoreTabs] ?: true,
            desktopMode = p[Keys.desktopMode] ?: false,
            sleepMinutes = p[Keys.sleepMinutes] ?: 3,
            customSearchName = p[Keys.customSearchName] ?: "My search",
            customSearchTemplate = p[Keys.customSearchTemplate] ?: "https://duckduckgo.com/?q={query}"
        )
    }
    suspend fun save(value: Customization) {
        require(UrlPolicy.isHomePage(value.homePage))
        require(value.uiScale in .85f..1.3f && value.webTextScale in .75f..2f)
        require(value.sleepMinutes in listOf(0, 1, 3, 5, 15))
        require(value.customSearchName.isNotBlank() && value.customSearchName.length <= 40)
        require(UrlPolicy.isSearchTemplate(value.customSearchTemplate))
        store.edit { p ->
            p[Keys.toolbarPlacement] = value.toolbarPlacement.name
            p[Keys.palette] = value.palette.name
            p[Keys.blockSocialTrackers] = value.blockSocialTrackers
            p[Keys.blockFingerprinters] = value.blockFingerprinters
            p[Keys.blockCryptominers] = value.blockCryptominers
            p[Keys.blockEmailTrackers] = value.blockEmailTrackers
            p[Keys.blockContentTrackers] = value.blockContentTrackers
            p[Keys.cookiePolicy] = value.cookiePolicy.name
            p[Keys.stripTrackingParameters] = value.stripTrackingParameters
            p[Keys.blockAutoplay] = value.blockAutoplay
            p[Keys.suspendBackgroundMedia] = value.suspendBackgroundMedia
            p[Keys.onboarded] = value.isOnboarded
            p[Keys.accent] = value.accentArgb or 0xFF000000.toInt()
            p[Keys.placement] = value.tabPlacement.name
            p[Keys.opacity] = value.opacity.coerceIn(0.45f, 1f)
            p[Keys.blur] = value.blur.coerceIn(0f, 40f)
            p[Keys.home] = value.homePage
            p[Keys.search] = SearchPreferences.resolve(value.searchEngine.name, value.language).name
            p[Keys.protection] = value.protection.name
            p[Keys.language] = value.language.name
            p[Keys.theme] = value.theme.name
            p[Keys.webTheme] = value.webTheme.name
            p[Keys.animations] = value.animations
            p[Keys.intro] = value.intro
            p[Keys.ambientFog] = value.ambientFog
            p[Keys.showClock] = value.showClock
            p[Keys.showShortcuts] = value.showShortcuts
            p[Keys.showTagline] = value.showTagline
            p[Keys.hideToolbar] = value.hideToolbar
            p[Keys.uiScale] = value.uiScale
            p[Keys.webTextScale] = value.webTextScale
            p[Keys.javascript] = value.javascript
            p[Keys.safeBrowsing] = value.safeBrowsing
            p[Keys.allowScreenshots] = value.allowScreenshots
            p[Keys.allowPopups] = value.allowPopups
            p[Keys.restoreTabs] = value.restoreTabs
            p[Keys.desktopMode] = value.desktopMode
            p[Keys.sleepMinutes] = value.sleepMinutes
            p[Keys.customSearchName] = value.customSearchName
            p[Keys.customSearchTemplate] = value.customSearchTemplate
        }
    }
}
