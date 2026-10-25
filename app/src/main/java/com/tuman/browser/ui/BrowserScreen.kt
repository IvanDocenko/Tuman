package com.tuman.browser.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.tuman.browser.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.core.graphics.scale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.tuman.browser.data.*
import com.tuman.browser.engine.*
import com.tuman.browser.ui.theme.Midnight
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import org.mozilla.geckoview.GeckoView
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(manager: TumanSessionManager, settings: Customization,
    store: CustomizationDataStore, incomingUrl: String?, onIncomingConsumed: () -> Unit) {
    val state by manager.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var settingsOpen by remember { mutableStateOf(false) }
    var shieldOpen by remember { mutableStateOf(false) }
    var settingsStart by remember { mutableIntStateOf(-1) }
    var tabsOpen by remember { mutableStateOf(false) }
    var erasePrompt by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<GeckoView?>(null) }
    var backdrop by remember { mutableStateOf<Bitmap?>(null) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val prompts = remember(context, settings.language) { NativePromptDelegate(context, settings.language) }
    val scope = rememberCoroutineScope()
    val active = state.active
    val overlayOpen by rememberUpdatedState(settingsOpen || tabsOpen || shieldOpen)
    val nested = remember(manager) { object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!overlayOpen) manager.nestedScroll(consumed.y)
            return Offset.Zero
        }
    } }
    DisposableEffect(lifecycle, manager, prompts) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) manager.setForeground(!overlayOpen)
            if (event == Lifecycle.Event.ON_PAUSE) { manager.setForeground(false); backdrop = null; prompts.close() }
        }
        lifecycle.addObserver(observer)
        manager.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycle.removeObserver(observer); manager.setForeground(false); prompts.close() }
    }
    LaunchedEffect(settingsOpen, tabsOpen, shieldOpen) {
        if (!settingsOpen && !tabsOpen && !shieldOpen) manager.revealChrome()
        manager.setForeground(!settingsOpen && !tabsOpen && !shieldOpen && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    LaunchedEffect(state.ready, incomingUrl) {
        if (state.ready && incomingUrl != null) {
            val url = runCatching { UrlPolicy.resolve(incomingUrl, settings) }.getOrNull()
            if (url != null) manager.newTab(url) else manager.notify(settings.language.text("Unsupported link protocol", "Ссылка использует неподдерживаемый протокол"))
            onIncomingConsumed()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbar.showSnackbar(message)
            manager.consumeMessage()
        }
    }
    // SurfaceView is not part of Compose's drawing tree. Capture the native pixels explicitly.
    LaunchedEffect(view, state.activeId, settings.blur, settingsOpen, tabsOpen, state.chromeVisible, active?.url) {
        backdrop = null
        if (active?.url == "about:blank" || settings.blur == 0f || android.os.Build.VERSION.SDK_INT < 31 || settingsOpen || tabsOpen || !state.chromeVisible) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                val currentView = view
                val currentSession = currentView?.session
                if (currentView != null && currentSession != null && currentView.width > 0) {
                    val captured = currentView.captureBackdrop()
                    if (currentView.session === currentSession) backdrop = captured
                }
                delay(700)
            }
        }
    }
    BackHandler(settingsOpen || tabsOpen || state.fullScreen || active?.canGoBack == true) {
        when {
            settingsOpen -> settingsOpen = false
            tabsOpen -> tabsOpen = false
            else -> manager.goBack()
        }
    }
    val chromeOffset by animateDpAsState(if (state.chromeVisible && !state.fullScreen) 0.dp else 320.dp, animationSpec = tween(if (settings.animations) 260 else 0), label = "chrome-offset")
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onSizeChanged { viewport = it }.nestedScroll(nested)) {
        if (state.ready) AndroidView(
            modifier = Modifier.fillMaxSize().then(if (state.fullScreen) Modifier else Modifier.systemBarsPadding()),
            factory = { GeckoView(it).also { created -> view = created } },
            onReset = null,
            onRelease = { released ->
                released.session?.promptDelegate = null
                released.releaseSession()
                prompts.close()
                if (view === released) view = null
            },
            update = { gecko ->
                val current = manager.session(state.activeId)
                if (gecko.session !== current) {
                    gecko.session?.promptDelegate = null
                    prompts.close()
                    gecko.releaseSession()
                    if (current != null) gecko.setSession(current)
                }
                current?.promptDelegate = prompts
            }
        )
        AnimatedVisibility(visible = active?.url == "about:blank", enter = fadeIn(tween(if (settings.animations) 250 else 0)), exit = fadeOut(tween(if (settings.animations) 140 else 0))) {
            HomeScreen(settings, onNavigate = manager::navigate, onSettings = { shieldOpen = true }, onLanguage = { language ->
                scope.launch {
                    try { store.save(settings.copy(language = language)) }
                    catch (e: Exception) { if (e is CancellationException) throw e; manager.notify(settings.language.text("Could not save language", "Не удалось сохранить язык")) }
                }
            })
        }
        if (state.ready && !state.fullScreen) {
            ToolbarPlacement.entries.forEach { edge ->
                val top = edge == ToolbarPlacement.TOP
                val showTabs = settings.tabPlacement == if (top) TabPlacement.TOP else TabPlacement.BOTTOM
                val showToolbar = settings.toolbarPlacement == edge
                if (showTabs || showToolbar) Column(
                    Modifier.align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
                        .then(if (top) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding().imePadding())
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .offset { IntOffset(0, if (top) -chromeOffset.roundToPx() else chromeOffset.roundToPx()) },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showTabs) GlassPanel(settings, backdrop, viewport, Modifier.fillMaxWidth()) {
                        TabPager(state, manager, onList = { tabsOpen = true })
                    }
                    if (showToolbar) {
                        GlassPanel(settings, backdrop, viewport, Modifier.fillMaxWidth()) {
                            Column {
                                Omnibox(active, manager, onShield = { shieldOpen = true })
                                if ((active?.progress ?: 100) < 100) LinearProgressIndicator(
                                    progress = { (active?.progress ?: 0) / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
                                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    MotionIconButton(onClick = { manager.goBack() }, enabled = active?.canGoBack == true) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back", "Назад")) }
                                    MotionIconButton(onClick = manager::forward, enabled = active?.canGoForward == true) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, tr("Forward", "Вперёд")) }
                                    MotionIconButton(onClick = manager::home) { Icon(Icons.Rounded.Home, tr("Home", "Домой")) }
                                    MotionIconButton(onClick = { tabsOpen = true }) { BadgedBox(badge = { Badge { RevealText(state.tabs.size.toString()) } }) { Icon(Icons.Rounded.Tab, tr("All tabs", "Все вкладки")) } }
                                    MotionIconButton(onClick = {
                                        manager.revealChrome()
                                        scope.launch {
                                            try { store.save(settings.copy(toolbarPlacement = if (settings.toolbarPlacement == ToolbarPlacement.TOP) ToolbarPlacement.BOTTOM else ToolbarPlacement.TOP)) }
                                            catch (e: Exception) { if (e is CancellationException) throw e; manager.notify(settings.language.text("Could not move the address bar", "Не удалось переместить адресную панель")) }
                                        }
                                    }) { Icon(Icons.Rounded.SwapVert, if (settings.toolbarPlacement == ToolbarPlacement.TOP) tr("Move address bar to bottom", "Перенести адресную панель вниз") else tr("Move address bar to top", "Перенести адресную панель вверх")) }
                                    MotionIconButton(onClick = { settingsStart = -1; settingsOpen = true }) { Icon(Icons.Rounded.Tune, tr("Settings", "Настройки")) }
                                }
                            }
                        }
                    }
                }
            }
            if (!state.chromeVisible) SmallFloatingActionButton(onClick = manager::revealChrome,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)) {
                Icon(Icons.Rounded.ExpandLess, tr("Show address bar", "Показать адресную строку"))
            }
        }
        if (!state.ready) CircularProgressIndicator(Modifier.align(Alignment.Center))
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp))
        AnimatedVisibility(visible = settingsOpen, enter = fadeIn(tween(if (settings.animations) 180 else 0)) + slideInVertically(tween(if (settings.animations) 280 else 0)) { it / 12 }, exit = fadeOut(tween(if (settings.animations) 160 else 0))) {
            SettingsScreen(settings, onClose = { settingsOpen = false }, onSave = store::save, onClearData = manager::clearBrowsingData,
                initialPage = settingsStart, onOpenUrl = { url -> manager.newTab(url); settingsOpen = false })
        }
    }
    if (shieldOpen) ProtectionSheet(active, settings, onDismiss = { shieldOpen = false }, onSettings = {
        shieldOpen = false; settingsStart = 1; settingsOpen = true
    })
    if (tabsOpen) ModalBottomSheet(onDismissRequest = { tabsOpen = false }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                RevealText(tr("Tabs", "Вкладки") + " · ${state.tabs.size}", style = MaterialTheme.typography.titleLarge)
                MotionIconButton(onClick = { manager.newTab(); tabsOpen = false }) { Icon(Icons.Rounded.Add, tr("New tab", "Новая вкладка")) }
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(state.tabs, key = { it.id }) { tab ->
                    ListItem(headlineContent = { RevealText(tab.title.ifBlank { tr("New tab", "Новая вкладка") }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { RevealText(if (tab.sleeping) tr("Sleeping · ", "Спит · ") + tab.url else tab.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { RadioButton(selected = tab.id == state.activeId, onClick = { manager.select(tab.id); tabsOpen = false }) },
                        trailingContent = { MotionIconButton(onClick = { manager.close(tab.id) }) { Icon(Icons.Rounded.Close, tr("Close tab", "Закрыть вкладку")) } },
                        modifier = Modifier.fillMaxWidth().then(if (settings.animations) Modifier.animateItem() else Modifier))
                }
            }
            TextButton(onClick = { manager.sleepBackgroundTabs(); tabsOpen = false }) { RevealText(tr("Sleep background tabs", "Усыпить фоновые вкладки")) }
            TextButton(onClick = { erasePrompt = true }) { RevealText(tr("Close all tabs", "Закрыть все вкладки")) }
        }
    }
    if (erasePrompt) AlertDialog(onDismissRequest = { erasePrompt = false }, title = { RevealText(tr("Close all tabs?", "Закрыть все вкладки?")) },
        text = { RevealText(tr("Saved tab history will be replaced with an empty tab.", "Сохранённая история вкладок будет заменена пустой вкладкой.")) },
        confirmButton = { TextButton(onClick = { manager.clearTabs(); erasePrompt = false; tabsOpen = false }) { RevealText(tr("Close all", "Закрыть все")) } },
        dismissButton = { TextButton(onClick = { erasePrompt = false }) { RevealText(tr("Cancel", "Отмена")) } })
}

@Composable
private fun Omnibox(active: TabInfo?, manager: TumanSessionManager, onShield: () -> Unit) {
    var text by remember(active?.id) { mutableStateOf(TextFieldValue(active?.url?.takeUnless { it == "about:blank" } ?: "")) }
    var focused by remember(active?.id) { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val margin by animateDpAsState(if (focused) 4.dp else 10.dp, animationSpec = tween(if (LocalCustomization.current.animations) 220 else 0), label = "omnibox-focus")
    LaunchedEffect(active?.url, focused) { if (!focused) text = TextFieldValue(active?.url?.takeUnless { it == "about:blank" } ?: "") }
    LaunchedEffect(focused) {
        if (focused) {
            // Apply after the initial tap has placed its caret; later taps can position the caret normally.
            withFrameNanos { }
            text = text.copy(selection = TextRange(0, text.text.length))
        }
    }
    fun submit() { manager.navigate(text.text); focus.clearFocus(); keyboard?.hide() }
    TextField(value = text, onValueChange = { text = it }, singleLine = true,
        placeholder = { RevealText(tr("Search or address", "Поиск или адрес")) },
        leadingIcon = {
            MotionIconButton(onClick = onShield) {
                BadgedBox(badge = { if ((active?.blockedItems ?: 0) > 0) Badge { RevealText(active!!.blockedItems.coerceAtMost(999).toString()) } }) {
                    Icon(Icons.Rounded.VerifiedUser, tr("Protection shield", "Щит защиты"), tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        trailingIcon = {
            MotionIconButton(onClick = {
                if (focused) submit() else if ((active?.progress ?: 100) < 100) manager.stop() else manager.reload()
            }) { Icon(if (focused) Icons.Rounded.Search else if ((active?.progress ?: 100) < 100) Icons.Rounded.Close else Icons.Rounded.Refresh,
                if (focused) tr("Open", "Открыть") else if ((active?.progress ?: 100) < 100) tr("Stop", "Остановить") else tr("Reload", "Обновить")) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { submit() }),
        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().padding(horizontal = margin).onFocusChanged { focused = it.isFocused; manager.setOmniboxFocused(focused) })
}

@Composable
private fun TabPager(state: BrowserState, manager: TumanSessionManager, onList: () -> Unit) {
    if (state.tabs.isEmpty()) return
    val index = state.tabs.indexOfFirst { it.id == state.activeId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = index, pageCount = { state.tabs.size })
    val latestTabs by rememberUpdatedState(state.tabs)
    LaunchedEffect(state.activeId, state.tabs.size) {
        if (!pager.isScrollInProgress && pager.currentPage != index) pager.scrollToPage(index)
    }
    LaunchedEffect(pager) {
        var userScroll = false
        snapshotFlow { pager.isScrollInProgress to pager.settledPage }.distinctUntilChanged().collect { (scrolling, page) ->
            if (scrolling) userScroll = true
            else if (userScroll) {
                userScroll = false
                latestTabs.getOrNull(page)?.let { manager.select(it.id) }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MotionIconButton(onClick = onList) { Icon(Icons.Rounded.Layers, tr("Tab list", "Список вкладок")) }
        HorizontalPager(state = pager, key = { state.tabs[it].id }, modifier = Modifier.weight(1f)) { page ->
            val tab = state.tabs[page]
            Column(Modifier.padding(vertical = 10.dp)) {
                RevealText(tab.title.ifBlank { tr("New tab", "Новая вкладка") }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                RevealText("${page + 1} / ${state.tabs.size} · " + tr("swipe to switch", "свайп для переключения"), maxLines = 1, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        MotionIconButton(onClick = { manager.newTab() }) { Icon(Icons.Rounded.Add, tr("New tab", "Новая вкладка")) }
    }
}

private suspend fun GeckoView.captureBackdrop(): Bitmap? = suspendCancellableCoroutine { continuation ->
    capturePixels().accept({ bitmap ->
        if (bitmap == null) {
            if (continuation.isActive) continuation.resume(null)
            return@accept
        }
        if (continuation.isActive) {
            val scaled = bitmap.scale((bitmap.width / 3).coerceAtLeast(1), (bitmap.height / 3).coerceAtLeast(1), true)
            if (scaled !== bitmap) bitmap.recycle()
            continuation.resume(scaled)
        } else bitmap.recycle()
    }, { if (continuation.isActive) continuation.resume(null) })
}
