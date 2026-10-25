package com.tuman.browser.engine

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuman.browser.TumanApplication
import com.tuman.browser.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.mozilla.geckoview.*
import java.util.UUID

// UI receives immutable snapshots; native sessions are confined to the main thread.
data class TabInfo(
    val id: String,
    val url: String = "about:blank",
    val title: String = "",
    val sleeping: Boolean = false,
    val progress: Int = 100,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val blockedItems: Int = 0,
    val blockedHosts: List<String> = emptyList(),
    val connectionSecure: Boolean = false
)
data class BrowserState(
    val ready: Boolean = false,
    val tabs: List<TabInfo> = emptyList(),
    val activeId: String? = null,
    val chromeVisible: Boolean = true,
    val fullScreen: Boolean = false,
    val message: String? = null
) {
    val active: TabInfo? get() = tabs.firstOrNull { it.id == activeId }
}

class TumanSessionManager(application: Application) : AndroidViewModel(application) {
    private class Entry(var info: TabInfo) {
        var session: GeckoSession? = null
        var saved: GeckoSession.SessionState? = null
        var usedAt = SystemClock.elapsedRealtime()
        var sleepPending = false
        var lastY = 0
        var lastCrashAt = 0L
        val blockedResources = linkedSetOf<String>()
    }
    private val entries = linkedMapOf<String, Entry>()
    private val mutableState = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = mutableState.asStateFlow()
    private val persistence = TabStateStore(application)
    private data class WriteRequest(val snapshot: SavedTabs?, val done: CompletableDeferred<Unit>? = null)
    private val writes = Channel<WriteRequest>(Channel.UNLIMITED)
    private var settings = Customization()
    private var foreground = false
    private var omniboxFocused = false
    private var disposed = false
    private val runtime by lazy { TumanGeckoRuntime.get(application) }

    init {
        viewModelScope.launch {
            for (request in writes) {
                try {
                    withContext(Dispatchers.IO) {
                        if (request.snapshot == null) persistence.clear() else persistence.write(request.snapshot)
                    }
                    request.done?.complete(Unit)
                }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    request.done?.completeExceptionally(error)
                    notify(settings.language.text("Could not save tabs: check available storage", "Не удалось сохранить вкладки: проверьте свободное место"))
                }
            }
        }
        viewModelScope.launch {
            val store = (application as TumanApplication).customization
            settings = store.settings.first()
            val restored = try { withContext(Dispatchers.IO) { if (settings.restoreTabs) persistence.read() else { persistence.clear(); SavedTabs(null, emptyList()) } } }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                notify(settings.language.text("Saved tabs are unavailable. Started a new session", "Сохранённые вкладки недоступны. Открыта новая сессия"))
                SavedTabs(null, emptyList())
            }
            restored.tabs.forEach { saved ->
                entries[saved.id] = Entry(TabInfo(saved.id, saved.url, saved.title, sleeping = true)).apply {
                    this.saved = runCatching { GeckoSession.SessionState.fromString(saved.state) }.getOrNull()
                }
            }
            mutableState.value = state.value.copy(ready = true)
            if (entries.isEmpty()) newTab() else select(restored.activeId?.takeIf(entries::containsKey) ?: entries.keys.first())
            store.settings.collect { next ->
                val previous = settings
                settings = next
                applyProtection()
                if (previous.blockAutoplay != next.blockAutoplay) {
                    // Autoplay decisions are cached as site permissions. Clear them before reloading;
                    // all other site permissions in Tuman are denied by the delegate on every request.
                    try {
                        suspendCancellableCoroutine<Unit> { continuation ->
                            runtime.storageController.clearData(StorageController.ClearFlags.PERMISSIONS).accept({
                                if (continuation.isActive) continuation.resume(Unit) { _, _, _ -> }
                            }, { error -> if (continuation.isActive) continuation.resumeWith(Result.failure(error ?: IllegalStateException("Permission reset failed"))) })
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        notify(settings.language.text("Could not update media permissions. Please try again.", "Не удалось обновить разрешения медиа. Попробуйте ещё раз."))
                    }
                }
                if (!next.hideToolbar) revealChrome()
                if (previous.restoreTabs != next.restoreTabs) persist()
                if (previous.protection != next.protection || previous.javascript != next.javascript || previous.desktopMode != next.desktopMode ||
                    ProtectionPolicy.antiTracking(previous) != ProtectionPolicy.antiTracking(next) ||
                    ProtectionPolicy.cookieBehavior(previous) != ProtectionPolicy.cookieBehavior(next) ||
                    previous.stripTrackingParameters != next.stripTrackingParameters || previous.blockAutoplay != next.blockAutoplay) {
                    // Drop stale snapshots so sleeping tabs also pick up the new policy.
                    entries.values.forEach { entry -> entry.saved = null; entry.session?.reload() }
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                val now = SystemClock.elapsedRealtime()
                entries.values.filter { it.info.id != state.value.activeId && settings.sleepMinutes > 0 && now - it.usedAt > settings.sleepMinutes * 60_000L }
                    .forEach(::requestSleep)
            }
        }
    }

    fun session(id: String?): GeckoSession? = entries[id]?.session

    fun newTab(url: String = settings.homePage) {
        if (!state.value.ready) return
        if (entries.size >= 100) { notify(settings.language.text("100 tab limit. Close unused tabs", "Максимум 100 вкладок. Закройте ненужные")); return }
        val id = UUID.randomUUID().toString()
        entries[id] = Entry(TabInfo(id, if (settings.stripTrackingParameters) UrlPolicy.cleanTrackingUrl(url) else url))
        select(id)
    }

    fun select(id: String) {
        val next = entries[id] ?: return
        entries[state.value.activeId]?.let {
            it.usedAt = SystemClock.elapsedRealtime()
            it.session?.setActive(false)
            it.session?.setFocused(false)
        }
        mutableState.value = state.value.copy(activeId = id, chromeVisible = true, fullScreen = false)
        next.sleepPending = false
        next.usedAt = SystemClock.elapsedRealtime()
        val live = next.session ?: open(next)
        live.setActive(foreground)
        live.setFocused(foreground)
        publish()
        persist()
        // Keep at most three warm tabs; the active session can never be evicted.
        entries.values.filter { it.session != null && it.info.id != id }
            .sortedByDescending { it.usedAt }.drop(2).forEach(::requestSleep)
    }

    fun close(id: String) {
        val item = entries.remove(id) ?: return
        item.session?.close()
        if (entries.isEmpty()) newTab()
        else if (state.value.activeId == id) select(entries.keys.last())
        publish()
        persist()
    }

    fun clearTabs() {
        entries.values.forEach { it.session?.close() }
        entries.clear()
        mutableState.value = state.value.copy(activeId = null)
        newTab("about:blank")
        persist()
    }

    suspend fun clearBrowsingData() {
        entries.values.forEach { it.session?.close() }
        entries.clear()
        mutableState.value = state.value.copy(activeId = null, tabs = emptyList(), ready = false)
        val diskCleared = CompletableDeferred<Unit>()
        writes.send(WriteRequest(null, diskCleared))
        try {
            diskCleared.await()
            suspendCancellableCoroutine<Unit> { continuation ->
                runtime.storageController.clearData(StorageController.ClearFlags.ALL).accept({
                    if (continuation.isActive) continuation.resume(Unit) { _, _, _ -> }
                }, { failure -> if (continuation.isActive) continuation.resumeWith(Result.failure(failure ?: IllegalStateException("Clear data failed"))) })
            }
        } finally {
            mutableState.value = state.value.copy(ready = true)
            newTab("about:blank")
        }
    }

    fun navigate(text: String) {
        val url = try { UrlPolicy.resolve(text, settings) }
        catch (error: IllegalArgumentException) { notify(settings.language.text("Enter an HTTPS address or a search query", "Введите HTTPS-адрес или поисковый запрос")); return }
        val entry = entries[state.value.activeId] ?: return
        entry.info = entry.info.copy(url = url, title = "", progress = 0)
        entry.saved = null
        entry.session?.loadUri(url)
        revealChrome()
        publish()
        persist()
    }
    fun goBack(): Boolean {
        if (state.value.fullScreen) { session(state.value.activeId)?.exitFullScreen(); return true }
        if (state.value.active?.canGoBack == true) { session(state.value.activeId)?.goBack(); return true }
        return false
    }
    fun forward() { session(state.value.activeId)?.goForward() }
    fun reload() {
        val entry = entries[state.value.activeId] ?: return
        if (entry.session == null) select(entry.info.id) else entry.session?.reload()
    }
    fun stop() { session(state.value.activeId)?.stop() }
    fun home() { navigate(settings.homePage) }
    fun revealChrome() { mutableState.value = state.value.copy(chromeVisible = true) }
    fun setOmniboxFocused(value: Boolean) { omniboxFocused = value; if (value) revealChrome() }
    fun nestedScroll(deltaY: Float) {
        if (omniboxFocused || !settings.hideToolbar) return
        if (deltaY < -12) mutableState.value = state.value.copy(chromeVisible = false)
        else if (deltaY > 8) revealChrome()
    }
    fun setForeground(value: Boolean) {
        foreground = value
        if (value && state.value.ready && state.value.activeId != null && session(state.value.activeId) == null) {
            select(requireNotNull(state.value.activeId))
        }
        session(state.value.activeId)?.setActive(value)
        session(state.value.activeId)?.setFocused(value)
        if (!value) persist()
    }
    fun sleepBackgroundTabs() {
        entries.values.filter { it.info.id != state.value.activeId }.forEach(::requestSleep)
    }
    fun notify(message: String) { mutableState.value = state.value.copy(message = message) }
    fun consumeMessage() { mutableState.value = state.value.copy(message = null) }

    private fun open(entry: Entry): GeckoSession {
        val session = GeckoSession(GeckoSessionSettings.Builder()
            .usePrivateMode(true).useTrackingProtection(true)
            .allowJavascript(ProtectionPolicy.javascriptAllowed(settings)).build())
        entry.session = session
        entry.info = entry.info.copy(sleeping = false)
        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission
            ): GeckoResult<Int> = GeckoResult.fromValue(
                if (!settings.blockAutoplay && perm.permission in listOf(
                    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
                    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE))
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
        }
        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                if (entry.session !== session || !event.isBlocking) return
                val resource = event.uri
                // Count distinct blocked resources, not callback repetitions. Keep URLs in memory only.
                if (entry.blockedResources.size < 1000 && entry.blockedResources.add(resource)) {
                    val host = runCatching { java.net.URI(resource).host }.getOrNull()
                    entry.info = entry.info.copy(blockedItems = entry.blockedResources.size,
                        blockedHosts = (entry.info.blockedHosts + listOfNotNull(host)).distinct().take(20))
                    publish()
                }
            }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                if (url != null) entry.info = entry.info.copy(url = url)
                publish()
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                entry.info = entry.info.copy(canGoBack = canGoBack); publish()
            }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                entry.info = entry.info.copy(canGoForward = canGoForward); publish()
            }
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                val allowed = UrlPolicy.allowsNavigation(request.uri)
                if (!allowed) notify(settings.language.text("External protocol blocked", "Внешний протокол заблокирован"))
                return GeckoResult.fromValue(if (allowed) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
            }
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                // Explicitly create the destination ourselves; no untracked native windows.
                if (settings.allowPopups && UrlPolicy.allowsNavigation(uri)) newTab(uri)
                else if (!settings.allowPopups) notify(settings.language.text("Website pop-up blocked", "Новое окно сайта заблокировано"))
                return null
            }
            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                entry.info = entry.info.copy(progress = 100)
                notify(settings.language.text("Page could not load. Check the address, HTTPS and encrypted DNS availability", "Страница не загружена. Проверьте адрес, HTTPS и доступность защищённого DNS"))
                publish()
                // No certificate bypass and no HTTP fallback.
                return null
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                entry.blockedResources.clear()
                entry.info = entry.info.copy(progress = 5, blockedItems = 0, blockedHosts = emptyList(), connectionSecure = false); publish()
            }
            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
                entry.info = entry.info.copy(connectionSecure = securityInfo.isSecure); publish()
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                entry.info = entry.info.copy(progress = progress); publish()
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                entry.info = entry.info.copy(progress = 100); publish(); persist()
            }
            override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
                if (entry.session !== session) return
                entry.saved = sessionState
                persist()
                if (entry.sleepPending && entry.info.id != state.value.activeId) {
                    // Do not close from within Gecko's callback stack.
                    viewModelScope.launch { yield(); finishSleep(entry, session) }
                }
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                entry.info = entry.info.copy(title = title?.takeIf { it.isNotBlank() } ?: "")
                publish()
            }
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                if (entry.info.id == state.value.activeId) mutableState.value = state.value.copy(fullScreen = fullScreen)
            }
            override fun onCrash(session: GeckoSession) {
                if (entry.session !== session) return
                entry.session = null
                session.close()
                entry.info = entry.info.copy(sleeping = true, progress = 100)
                val now = SystemClock.elapsedRealtime()
                val repeated = entry.lastCrashAt > 0 && now - entry.lastCrashAt < 60_000
                entry.lastCrashAt = now
                if (!repeated && foreground && entry.info.id == state.value.activeId) select(entry.info.id) else publish()
                notify(if (repeated) settings.language.text("Page crashed again. Tap reload to retry", "Страница повторно завершилась с ошибкой. Нажмите обновить для повтора")
                    else settings.language.text("Page process stopped. Tab restored", "Процесс страницы завершился. Вкладка восстановлена"))
            }
            override fun onKill(session: GeckoSession) {
                if (entry.session !== session) return
                entry.session = null
                entry.sleepPending = false
                entry.info = entry.info.copy(sleeping = true, progress = 100)
                if (foreground && entry.info.id == state.value.activeId) select(entry.info.id) else publish()
            }
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                viewModelScope.launch(Dispatchers.IO) { runCatching { response.body?.close() } }
                notify(settings.language.text("This response is a file and cannot be displayed as a web page", "Этот ответ является файлом и не может быть показан как веб-страница"))
            }
        }
        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                val delta = scrollY - entry.lastY
                entry.lastY = scrollY
                if (entry.info.id == state.value.activeId) {
                    if (scrollY < 16 || delta < -10) revealChrome()
                    else if (delta > 16 && !omniboxFocused && settings.hideToolbar) mutableState.value = state.value.copy(chromeVisible = false)
                }
            }
        }
        session.open(runtime)
        applyProtection()
        entry.saved?.let(session::restoreState) ?: session.loadUri(entry.info.url)
        return session
    }

    private fun applyProtection() {
        if (entries.values.none { it.session != null }) return
        val cookies = ProtectionPolicy.cookieBehavior(settings)
        runtime.settings.contentBlocking.apply {
            setCookieBehavior(cookies).setCookieBehaviorPrivateMode(cookies)
            setAntiTracking(ProtectionPolicy.antiTracking(settings))
            setStrictSocialTrackingProtection(settings.blockSocialTrackers || settings.protection == Protection.MAXIMUM)
            setQueryParameterStrippingStripList(*UrlPolicy.trackingParameters.toTypedArray())
            setQueryParameterStrippingEnabled(settings.stripTrackingParameters)
            setQueryParameterStrippingPrivateBrowsingEnabled(settings.stripTrackingParameters)
        }
        runtime.settings.contentBlocking.setSafeBrowsing(if (settings.safeBrowsing || settings.protection == Protection.MAXIMUM) ContentBlocking.SafeBrowsing.DEFAULT else ContentBlocking.SafeBrowsing.NONE)
        runtime.settings.setAutomaticFontSizeAdjustment(false)
        runtime.settings.setFontSizeFactor(settings.webTextScale)
        runtime.settings.setPreferredColorScheme(when (settings.webTheme) {
            ThemeMode.DARK -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
            ThemeMode.LIGHT -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            ThemeMode.SYSTEM -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
        })
        entries.values.forEach { entry -> entry.session?.settings?.apply {
            setAllowJavascript(ProtectionPolicy.javascriptAllowed(settings))
            setSuspendMediaWhenInactive(settings.suspendBackgroundMedia)
            setUserAgentMode(if (settings.desktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            setViewportMode(if (settings.desktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
        } }
    }
    private fun requestSleep(entry: Entry) {
        val session = entry.session ?: return
        if (entry.info.id == state.value.activeId || entry.sleepPending) return
        entry.sleepPending = true
        session.setActive(false)
        session.flushSessionState()
        viewModelScope.launch {
            delay(5_000)
            if (entry.session === session && entry.sleepPending) {
                // Without a fresh acknowledgement keep the session alive instead of losing state.
                entry.sleepPending = false
            }
        }
    }
    private fun finishSleep(entry: Entry, session: GeckoSession) {
        if (!entry.sleepPending || entry.session !== session || entry.info.id == state.value.activeId) return
        entry.sleepPending = false
        session.close()
        entry.session = null
        entry.info = entry.info.copy(sleeping = true)
        publish()
    }
    private fun publish() {
        if (!disposed) mutableState.value = state.value.copy(tabs = entries.values.map { it.info })
    }
    private fun persist() {
        if (!state.value.ready || disposed) return
        writes.trySend(WriteRequest(if (settings.restoreTabs) SavedTabs(state.value.activeId, entries.values.map {
            SavedTab(it.info.id, it.info.url, it.info.title, it.saved?.toString())
        }) else null))
    }
    override fun onCleared() {
        disposed = true
        entries.values.forEach { it.session?.close() }
        entries.clear()
        writes.close()
    }
}
