package com.tuman.browser.data

import java.net.IDN
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object UrlPolicy {
    private fun normalizeHttps(value: String): String? = runCatching {
        val uri = URI(value)
        require(uri.scheme.equals("https", true))
        val authority = requireNotNull(uri.rawAuthority)
        require('@' !in authority && authority.isNotBlank())
        val normalizedAuthority = if (uri.host != null) authority else {
            val pieces = authority.split(':')
            require(pieces.size <= 2)
            val host = IDN.toASCII(pieces[0], IDN.USE_STD3_ASCII_RULES)
            require(host.isNotBlank())
            val port = if (pieces.size == 2) {
                require(pieces[1].toInt() in 1..65535)
                ":${pieces[1]}"
            } else ""
            host + port
        }
        require(uri.port == -1 || uri.port in 1..65535)
        val suffix = value.substring(value.indexOf("://") + 3 + authority.length)
        URI("https://$normalizedAuthority$suffix").toASCIIString()
    }.getOrNull()

    fun isHomePage(value: String): Boolean = value == "about:blank" || normalizeHttps(value) != null

    fun isSearchTemplate(value: String): Boolean {
        if (value.count { it == '{' } != 1 || value.count { it == '}' } != 1 || !value.contains("{query}")) return false
        val sample = value.replace("{query}", "tuman")
        val normalized = normalizeHttps(sample) ?: return false
        val uri = URI(normalized)
        // Placeholders belong in the path or query, never credentials, host or fragment.
        return !value.substringBefore('/', value).contains("{query}") &&
            !value.substringAfter("://").substringBefore('/').substringBefore('?').contains("{query}") &&
            !value.substringAfter('#', "").contains("{query}") && uri.rawFragment == null
    }

    val trackingParameters = setOf("utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id", "fbclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid")

    fun cleanTrackingUrl(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        if (uri.scheme !in listOf("http", "https") || uri.rawQuery == null) return url
        val retained = uri.rawQuery.split('&').filter { parameter ->
            val name = runCatching { java.net.URLDecoder.decode(parameter.substringBefore('='), "UTF-8") }.getOrDefault(parameter.substringBefore('='))
            name.lowercase(java.util.Locale.ROOT) !in trackingParameters
        }
        if (retained.size == uri.rawQuery.split('&').size) return url
        return url.substringBefore('?') + (if (retained.isEmpty()) "" else "?" + retained.joinToString("&")) +
            (uri.rawFragment?.let { "#$it" } ?: "")
    }

    fun resolve(input: String, settings: Customization): String {
        val resolved = resolve(input, SearchPreferences.resolve(settings.searchEngine.name, settings.language), settings.customSearchTemplate)
        return if (settings.stripTrackingParameters) cleanTrackingUrl(resolved) else resolved
    }

    fun resolve(input: String, engine: SearchEngine, customTemplate: String = ""): String {
        val text = input.trim()
        if (text.isEmpty() || text == "about:blank") return "about:blank"
        if (text.startsWith("https://", true) || text.startsWith("http://", true)) {
            return requireNotNull(normalizeHttps("https://" + text.substringAfter("://"))) { "Некорректный адрес HTTPS" }
        }
        if (!text.any(Char::isWhitespace) && text.substringBefore('/').contains('.')) {
            normalizeHttps("https://$text")?.let { return it }
        }
        // Never execute an intent:, javascript:, file: or data: pasted into the omnibox.
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(text)) {
            throw IllegalArgumentException("Допустимы только адреса HTTPS и поисковые запросы")
        }
        val encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
        return if (engine == SearchEngine.CUSTOM) {
            require(isSearchTemplate(customTemplate)) { "Invalid search template" }
            requireNotNull(normalizeHttps(customTemplate.replace("{query}", encoded)))
        } else engine.prefix + encoded
    }

    fun allowsNavigation(uri: String): Boolean = uri == "about:blank" ||
        uri.startsWith("https://", true) || uri.startsWith("http://", true)
}
