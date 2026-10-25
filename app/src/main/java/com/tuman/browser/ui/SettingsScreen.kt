package com.tuman.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.tuman.browser.BuildConfig
import com.tuman.browser.data.*
import com.tuman.browser.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
fun SettingsScreen(initial: Customization, onClose: () -> Unit, onSave: suspend (Customization) -> Unit,
    onClearData: suspend () -> Unit, initialPage: Int = -1, onOpenUrl: (String) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    var committed by remember { mutableStateOf(initial) }
    var page by rememberSaveable { mutableIntStateOf(initialPage) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pending by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var maximumWarning by remember { mutableStateOf(false) }
    var licensesOpen by remember { mutableStateOf(false) }
    var privacyOpen by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val mutex = remember { Mutex() }
    fun change(requested: Customization) {
        val value = requested.copy(searchEngine = SearchPreferences.resolve(requested.searchEngine.name, requested.language))
        draft = value
        pending++
        scope.launch {
            mutex.withLock {
                try { onSave(value); committed = value; error = null }
                catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    draft = committed
                    error = value.language.text("Could not save. Please try again.", "Не удалось сохранить. Попробуйте ещё раз.")
                } finally { pending-- }
            }
        }
    }
    fun back() { if (pending == 0 && !clearing) { if (page < 0) onClose() else { page = -1; tab = 0 } } }
    BackHandler { back() }
    // The outer theme owns font scaling; preview only replaces language/colors here.
    val outerDensity = androidx.compose.ui.platform.LocalDensity.current
    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
        outerDensity.density, outerDensity.fontScale / initial.uiScale)) {
    TumanTheme(draft) {
        val titles = listOf(tr("Personalization", "Персонализация"), tr("Security", "Безопасность"),
            tr("Navigation", "Навигация"), tr("About", "О приложении"))
        val descriptions = listOf(tr("Make this space yours", "Ваше пространство — ваши правила"),
            tr("Protection, permissions & data", "Защита, разрешения и данные"),
            tr("Search, homepage & tabs", "Поиск, домашняя страница и вкладки"), tr("The people & the browser", "О браузере и его создателях"))
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    MotionIconButton(onClick = ::back, enabled = pending == 0 && !clearing) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back", "Назад")) }
                    Column(Modifier.weight(1f)) {
                        RevealText(if (page < 0) tr("Settings", "Настройки") else titles[page], style = MaterialTheme.typography.titleLarge)
                        RevealText(if (pending > 0) tr("Saving…", "Сохранение…") else tr("Changes saved automatically", "Изменения сохраняются автоматически"),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                error?.let { RevealText(it, Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.error) }
                AnimatedContent(page, modifier = Modifier.weight(1f), label = "settings-page", transitionSpec = {
                    (fadeIn(tween(if (draft.animations) 220 else 0)) + slideInHorizontally(tween(if (draft.animations) 280 else 0)) { it / 8 }) togetherWith
                        fadeOut(tween(if (draft.animations) 120 else 0))
                }) { current ->
                    val pageScroll = rememberScrollState()
                    LaunchedEffect(tab) { pageScroll.scrollTo(0) }
                    Column(Modifier.fillMaxSize().verticalScroll(pageScroll).padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (current < 0) {
                            Spacer(Modifier.height(8.dp))
                            RevealText(tr("A little more\nyou.", "Больше\nвашего."), style = MaterialTheme.typography.displayLarge)
                            RevealText(tr("Every detail, in your hands.", "Каждая деталь — в ваших руках."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            val icons = listOf(Icons.Rounded.Palette, Icons.Rounded.Shield, Icons.Rounded.Explore, Icons.Rounded.Info)
                            titles.forEachIndexed { index, title ->
                                CategoryCard(title, descriptions[index], icons[index]) { page = index; tab = 0 }
                            }
                            Group(tr("Language", "Язык")) { LanguagePicker(draft.language) { change(draft.copy(language = it)) } }
                            RevealText("TUMAN / ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        } else {
                            val tabs = when (current) {
                                0 -> listOf(tr("Appearance", "Оформление"), tr("Home", "Главная"), tr("Interface", "Интерфейс"))
                                1 -> listOf(tr("Protection", "Защита"), tr("Trackers", "Трекеры"), tr("Your data", "Ваши данные"))
                                2 -> listOf(tr("Search", "Поиск"), tr("Tabs & pages", "Вкладки и сайты"))
                                else -> listOf(tr("Browser", "О браузере"), tr("Credits", "Авторы"), tr("Official", "Ресурсы"))
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                tabs.forEachIndexed { i, label -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { RevealText(label) }) }
                            }
                            // A separate scroll state per subpage avoids opening it midway down.
                            AnimatedContent(tab, label = "settings-tab", transitionSpec = {
                                fadeIn(tween(if (draft.animations) 200 else 0)) togetherWith fadeOut(tween(if (draft.animations) 100 else 0))
                            }) { selectedTab ->
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            when (current) {
                                0 -> when (selectedTab) {
                                    0 -> {
                                        Group(tr("Collections", "Коллекции")) { PalettePicker(draft.palette) { change(draft.copy(palette = it, accentArgb = it.accent.toInt())) } }
                                        Group(tr("Color & atmosphere", "Цвет и атмосфера")) {
                                            RevealText(tr("Theme", "Тема"), style = MaterialTheme.typography.titleMedium)
                                            ThemePicker(draft.theme) { change(draft.copy(theme = it)) }
                                            RevealText(tr("Accent color", "Цвет акцента"), style = MaterialTheme.typography.titleMedium)
                                            AccentColorPicker(draft.accentArgb) { change(draft.copy(accentArgb = it)) }
                                        }
                                        Group(tr("Glass", "Стекло")) {
                                            SettingSlider(tr("Opacity", "Непрозрачность"), draft.opacity, .45f..1f, "${(draft.opacity * 100).toInt()}%") { change(draft.copy(opacity = it)) }
                                            if (android.os.Build.VERSION.SDK_INT >= 31)
                                                SettingSlider(tr("Background blur", "Размытие фона"), draft.blur, 0f..40f, "${draft.blur.toInt()}") { change(draft.copy(blur = it)) }
                                            else RevealText(tr("Background blur requires Android 12 or later.", "Размытие фона доступно с Android 12."), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    1 -> {
                                        Group(tr("Home atmosphere", "Атмосфера главной")) {
                                            Toggle(tr("Ambient fog", "Туманный фон"), tr("Soft light on your start screen", "Мягкий свет на стартовом экране"), draft.ambientFog) { change(draft.copy(ambientFog = it)) }
                                            Toggle(tr("Clock & date", "Часы и дата"), "", draft.showClock) { change(draft.copy(showClock = it)) }
                                            Toggle(tr("Quick links", "Быстрые ссылки"), "", draft.showShortcuts) { change(draft.copy(showShortcuts = it)) }
                                            Toggle(tr("Welcome message", "Приветствие"), "", draft.showTagline) { change(draft.copy(showTagline = it)) }
                                        }
                                        Group(tr("Language", "Язык")) { LanguagePicker(draft.language) { change(draft.copy(language = it)) } }
                                    }
                                    else -> {
                                        Group(tr("Layout", "Расположение")) {
                                            RevealText(tr("Address bar", "Адресная панель"))
                                            ToolbarPlacementPicker(draft.toolbarPlacement) { change(draft.copy(toolbarPlacement = it)) }
                                            RevealText(tr("Tab strip", "Панель вкладок"))
                                            Choices(TabPlacement.entries, draft.tabPlacement, { placement -> when (placement) {
                                                TabPlacement.TOP -> tr("Top", "Сверху"); TabPlacement.BOTTOM -> tr("Bottom", "Снизу"); TabPlacement.HIDDEN -> tr("Hidden", "Скрыта")
                                            } }) { change(draft.copy(tabPlacement = it)) }
                                            Toggle(tr("Hide toolbar on scroll", "Скрывать панель при прокрутке"), "", draft.hideToolbar) { change(draft.copy(hideToolbar = it)) }
                                            SettingSlider(tr("Interface text size", "Размер текста интерфейса"), draft.uiScale, .85f..1.3f, "${(draft.uiScale * 100).toInt()}%") { change(draft.copy(uiScale = it)) }
                                        }
                                        Group(tr("Motion", "Движение")) {
                                            Toggle(tr("Interface animations", "Анимации интерфейса"), tr("Turn off to stop text effects, transitions and moving fog", "Выключите, чтобы отключить появление текста, переходы и движение тумана"), draft.animations) { change(draft.copy(animations = it)) }
                                            Toggle(tr("Launch intro", "Интро при запуске"), tr("A brief welcome when opening Tuman", "Короткое приветствие при открытии Tuman"), draft.intro) { change(draft.copy(intro = it)) }
                                        }
                                    }
                                }
                                1 -> if (selectedTab == 0) {
                                    Group(tr("Tracking protection", "Защита от отслеживания")) {
                                        Protection.entries.forEach { level ->
                                            SelectRow(protectionTitle(level), protectionDescription(level), draft.protection == level) {
                                                if (level == Protection.MAXIMUM && draft.protection != level) maximumWarning = true
                                                else change(draft.copy(protection = level))
                                            }
                                        }
                                    }
                                    Group(tr("Website controls", "Управление сайтами")) {
                                        Toggle(tr("JavaScript", "JavaScript"), if (draft.protection == Protection.MAXIMUM) tr("Disabled by Maximum protection", "Отключён максимальной защитой") else tr("Reloads open pages when changed", "При изменении открытые страницы перезагрузятся"),
                                            draft.javascript && draft.protection != Protection.MAXIMUM, draft.protection != Protection.MAXIMUM) { change(draft.copy(javascript = it)) }
                                        Toggle(tr("Dangerous site protection", "Защита от опасных сайтов"), tr("Warn about harmful and deceptive websites", "Предупреждать об опасных и мошеннических сайтах"), draft.safeBrowsing || draft.protection == Protection.MAXIMUM, draft.protection != Protection.MAXIMUM) { change(draft.copy(safeBrowsing = it)) }
                                        Toggle(tr("Block media autoplay", "Блокировать автозапуск медиа"), tr("Videos and audio wait for your action", "Видео и звук ждут вашего действия"), draft.blockAutoplay) { change(draft.copy(blockAutoplay = it)) }
                                        Toggle(tr("Pause background media", "Приостанавливать медиа в фоне"), tr("Pause playback when a tab becomes inactive", "Приостанавливать воспроизведение в неактивной вкладке"), draft.suspendBackgroundMedia) { change(draft.copy(suspendBackgroundMedia = it)) }
                                        Toggle(tr("Allow website pop-ups", "Разрешать новые окна сайтов"), tr("Sites can open additional tabs", "Сайты смогут открывать дополнительные вкладки"), draft.allowPopups) { change(draft.copy(allowPopups = it)) }
                                    }
                                    Group(tr("Always protected", "Постоянная защита")) {
                                        RevealText(tr("Secure connections and a private browsing session.", "Защищённые соединения и приватная сессия."))
                                        RevealText(tr("Websites cannot access your camera, microphone or location.", "Сайты не получают доступ к камере, микрофону и геолокации."), style = MaterialTheme.typography.bodySmall)
                                    }
                                } else if (selectedTab == 1) {
                                    val maximum = draft.protection == Protection.MAXIMUM
                                    Group(tr("Your shields", "Ваши щиты")) {
                                        Icon(Icons.Rounded.Shield, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                        RevealText(tr("Ad and analytics shields stay on.", "Защита от рекламных и аналитических трекеров всегда включена."))
                                        if (maximum) RevealText(tr("Maximum protection keeps every shield enabled.", "Максимальная защита включает все щиты."), style = MaterialTheme.typography.bodySmall)
                                        Toggle(tr("Social trackers", "Трекеры соцсетей"), tr("Reduce tracking through embedded social content", "Ограничивать отслеживание через элементы соцсетей"), draft.blockSocialTrackers || maximum, !maximum) { change(draft.copy(blockSocialTrackers = it)) }
                                        Toggle(tr("Fingerprinting trackers", "Сбор цифрового отпечатка"), tr("Block known sources that identify your device", "Блокировать известные источники идентификации устройства"), draft.blockFingerprinters || maximum, !maximum) { change(draft.copy(blockFingerprinters = it)) }
                                        Toggle(tr("Cryptominers", "Скрытый майнинг"), tr("Block known scripts that use your device for mining", "Блокировать известные скрипты скрытого майнинга"), draft.blockCryptominers || maximum, !maximum) { change(draft.copy(blockCryptominers = it)) }
                                        Toggle(tr("Email trackers", "Почтовые трекеры"), tr("Block known tracking content in webmail", "Блокировать известное отслеживание в веб-почте"), draft.blockEmailTrackers || maximum, !maximum) { change(draft.copy(blockEmailTrackers = it)) }
                                        Toggle(tr("Embedded content trackers", "Трекеры во встроенном контенте"), tr("Some embedded videos and widgets may not load", "Некоторые встроенные видео и виджеты могут не загрузиться"), draft.blockContentTrackers || maximum, !maximum) { change(draft.copy(blockContentTrackers = it)) }
                                    }
                                    Group(tr("Cookies & links", "Cookies и ссылки")) {
                                        RevealText(tr("Cookies", "Cookies"), style = MaterialTheme.typography.titleMedium)
                                        CookiePolicy.entries.forEach { policy ->
                                            SelectRow(cookieTitle(policy), "", draft.cookiePolicy == policy) { change(draft.copy(cookiePolicy = policy)) }
                                        }
                                        if (draft.protection != Protection.STRICT) RevealText(tr("Your protection level blocks third-party cookies even when isolation is selected.", "Текущий уровень защиты запрещает сторонние cookies даже при выборе изоляции."), style = MaterialTheme.typography.bodySmall)
                                        RevealText(tr("Blocking all cookies may sign you out or prevent sign-in.", "Запрет всех cookies может завершить сеанс или помешать входу на сайт."), style = MaterialTheme.typography.bodySmall)
                                        Toggle(tr("Clean tracking links", "Очищать ссылки от отслеживания"), tr("Remove common tracking tags when opening links", "Удалять распространённые метки отслеживания при открытии ссылок"), draft.stripTrackingParameters) { change(draft.copy(stripTrackingParameters = it)) }
                                    }
                                } else {
                                    Group(tr("On this device", "На этом устройстве")) {
                                        Toggle(tr("Allow screenshots", "Разрешить скриншоты"), tr("Also shows content in recent apps", "Также показывает содержимое в списке недавних приложений"), draft.allowScreenshots) { change(draft.copy(allowScreenshots = it)) }
                                        Toggle(tr("Restore tabs on launch", "Восстанавливать вкладки"), tr("Off: deletes the saved session; open tabs remain until exit", "Выключено: сохранённая сессия удаляется; открытые вкладки остаются до выхода"), draft.restoreTabs) { change(draft.copy(restoreTabs = it)) }
                                        RevealText(tr("Saved tabs are encrypted on this device.", "Сохранённые вкладки зашифрованы на этом устройстве."), style = MaterialTheme.typography.bodySmall)
                                    }
                                    Group(tr("Fresh start", "Чистый лист")) {
                                        RevealText(tr("Close all tabs and remove cookies, site storage, cache and saved navigation history.", "Закрыть все вкладки и удалить cookies, данные сайтов, кэш и сохранённую историю переходов."))
                                        OutlinedButton(onClick = { confirm = "data" }, enabled = !clearing && pending == 0) { RevealText(if (clearing) tr("Clearing…", "Очистка…") else tr("Clear browsing data", "Очистить данные браузера")) }
                                        if (cleared) RevealText(tr("Browsing data cleared", "Данные браузера очищены"), color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                2 -> if (selectedTab == 0) {
                                    Group(tr("Default browser", "Браузер по умолчанию")) { DefaultBrowserControls() }
                                    Group(tr("Search engine", "Поисковая система")) {
                                        SearchPreferences.available(draft.language).filter { it != SearchEngine.CUSTOM }.forEach { engine ->
                                            SelectRow(engine.label, "", draft.searchEngine == engine) { change(draft.copy(searchEngine = engine)) }
                                        }
                                        SelectRow(draft.customSearchName, tr("Custom search", "Свой поисковик"), draft.searchEngine == SearchEngine.CUSTOM) { change(draft.copy(searchEngine = SearchEngine.CUSTOM)) }
                                    }
                                    Group(tr("Your search engine", "Ваш поисковик")) { CustomSearchEditor(draft) { change(it) } }
                                    Group(tr("Homepage", "Домашняя страница")) {
                                        UrlEditor(draft.homePage) { change(draft.copy(homePage = it)) }
                                    }
                                } else {
                                    Group(tr("Web pages", "Веб-страницы")) {
                                        Toggle(tr("Desktop websites", "Версия для компьютера"), tr("Show the desktop version of websites", "Открывать сайты как на компьютере"), draft.desktopMode) { change(draft.copy(desktopMode = it)) }
                                        RevealText(tr("Website color preference", "Предпочтительная тема сайтов"))
                                        ThemePicker(draft.webTheme) { change(draft.copy(webTheme = it)) }
                                        RevealText(tr("Applies to sites that support color themes.", "Для сайтов, поддерживающих смену темы."), style = MaterialTheme.typography.bodySmall)
                                        SettingSlider(tr("Website text size", "Размер текста сайтов"), draft.webTextScale, .75f..2f, "${(draft.webTextScale * 100).toInt()}%") { change(draft.copy(webTextScale = it)) }
                                    }
                                    Group(tr("Tab memory", "Память вкладок")) {
                                        RevealText(tr("Put inactive tabs to sleep after", "Усыплять неактивные вкладки через"))
                                        Choices(listOf(0, 1, 3, 5, 15), draft.sleepMinutes, { if (it == 0) tr("Never", "Никогда") else "$it " + tr("min", "мин") }) { change(draft.copy(sleepMinutes = it)) }
                                        RevealText(tr("Sleeping tabs reopen when selected. Tuman may also put background tabs to sleep to save memory.", "Спящие вкладки восстанавливаются при выборе. Для экономии памяти Tuman также может усыплять фоновые вкладки."), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                3 -> if (selectedTab == 0) {
                                    Group("TUMAN") {
                                        BrandMark(Modifier.size(80.dp))
                                        RevealText("Tuman ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineLarge)
                                        RevealText(tr("A quieter place for the web.", "Спокойное место для интернета."), style = MaterialTheme.typography.titleMedium)
                                        RevealText(tr("Your own space for discoveries. Thoughtful details, a calmer rhythm and protection that stays close.", "Ваше пространство для открытий. Продуманные детали, спокойный ритм и защита, которая всегда рядом."))
                                        RevealText(tr("No account required. No app analytics.", "Без обязательного аккаунта и аналитики приложения."))
                                    }
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    TextButton(onClick = { context.startActivity(android.content.Intent(context, OnboardingActivity::class.java)) }) { RevealText(tr("Welcome tour", "Знакомство с Tuman")) }
                                    TextButton(onClick = { privacyOpen = true }) { RevealText(tr("Privacy policy", "Политика конфиденциальности")) }
                                    TextButton(onClick = { licensesOpen = true }) { RevealText(tr("Open-source licenses", "Лицензии компонентов")) }
                                    OutlinedButton(onClick = { confirm = "reset" }, enabled = pending == 0) { RevealText(tr("Reset settings", "Сбросить настройки")) }
                                } else if (selectedTab == 1) {
                                    Group(tr("People behind Tuman", "Люди за Tuman")) {
                                        RevealText(tr("Developer", "Разработчик"), style = MaterialTheme.typography.titleMedium)
                                        RevealText("Lunarium Studio", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        HorizontalDivider()
                                        RevealText(tr("Tester", "Тестеровщик"), style = MaterialTheme.typography.titleMedium)
                                        RevealText("Daniil Ignatiev", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    OfficialResourcesScreen(onOpenUrl)
                                }
                            }
                            }
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
        }
        if (maximumWarning) MaximumProtectionDialog(onDismiss = { maximumWarning = false }, onConfirm = { maximumWarning = false; change(draft.copy(protection = Protection.MAXIMUM)) })
        if (privacyOpen) PrivacyPolicyScreen { privacyOpen = false }
        if (licensesOpen) LicenseScreen(onClose = { licensesOpen = false })
        if (confirm != null) AlertDialog(onDismissRequest = { confirm = null },
            title = { RevealText(if (confirm == "data") tr("Clear browsing data?", "Очистить данные браузера?") else tr("Reset settings?", "Сбросить настройки?")) },
            text = { RevealText(if (confirm == "data") tr("All tabs will close. Cookies and saved browsing data will be removed. This cannot be undone.", "Все вкладки закроются. Cookies и сохранённые данные браузера будут удалены. Отменить действие нельзя.") else tr("Restore default appearance, protection and navigation settings. Your language will be kept.", "Вернуть стандартные настройки оформления, защиты и навигации. Выбранный язык сохранится.")) },
            confirmButton = { TextButton(onClick = {
                val action = confirm; confirm = null
                if (action == "reset") change(Customization(isOnboarded = true, language = draft.language))
                else scope.launch {
                    clearing = true; cleared = false
                    try { onClearData(); cleared = true }
                    catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        error = draft.language.text("Could not clear all data. Please retry.", "Не удалось очистить все данные. Повторите попытку.")
                    } finally { clearing = false }
                }
            }) { RevealText(tr("Confirm", "Подтвердить")) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { RevealText(tr("Cancel", "Отмена")) } })
    }
    }
}

@Composable
private fun CategoryCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().revealOnEnter(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) { Icon(icon, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary) }
            Column(Modifier.weight(1f)) { RevealText(title, style = MaterialTheme.typography.titleMedium); RevealText(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.revealOnEnter(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RevealText(title, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun Toggle(title: String, description: String, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Switch) { onChange(!value) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            RevealText(title, style = MaterialTheme.typography.titleSmall)
            if (description.isNotBlank()) RevealText(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(disabledCheckedThumbColor = MaterialTheme.colorScheme.primary.copy(alpha = .75f),
                disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = .18f)))
    }
}

@Composable
private fun SelectRow(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            RevealText(title, style = MaterialTheme.typography.titleSmall)
            if (description.isNotBlank()) RevealText(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected, onClick)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(values: List<T>, selected: T, label: @Composable (T) -> String, onChange: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected = value == selected, onClick = { onChange(value) }, label = { RevealText(label(value)) }) }
    }
}

@Composable
fun LanguagePicker(value: Language, onChange: (Language) -> Unit) {
    Choices(Language.entries, value, { if (it == Language.EN) "English" else "Русский" }, onChange)
}

@Composable
private fun ThemePicker(value: ThemeMode, onChange: (ThemeMode) -> Unit) {
    Choices(ThemeMode.entries, value, { when (it) {
        ThemeMode.DARK -> tr("Dark", "Тёмная"); ThemeMode.LIGHT -> tr("Light", "Светлая"); ThemeMode.SYSTEM -> tr("System", "Системная")
    } }, onChange)
}

@Composable
private fun SettingSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    val liveDisplay = if (display.endsWith("%")) "${(local * 100).toInt()}%" else "${local.toInt()}"
    RevealText("$title · $liveDisplay", style = MaterialTheme.typography.titleSmall)
    Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local) }, valueRange = range)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentColorPicker(argb: Int, onChange: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0xFF75E5DA, 0xFFB7A5FF, 0xFF8BBEFF, 0xFFFFB88C, 0xFFF59FC3, 0xFFBADD85).forEach { hex ->
            val color = Color(hex)
            Surface(onClick = { onChange(hex.toInt()) }, shape = CircleShape, color = color, modifier = Modifier.size(44.dp)) {
                if (argb == hex.toInt()) Icon(Icons.Rounded.Check, tr("Selected", "Выбрано"), Modifier.padding(10.dp), tint = Color(0xFF090E19))
            }
        }
    }
    var custom by remember(argb) { mutableStateOf("%06X".format(argb and 0xFFFFFF)) }
    OutlinedTextField(custom, { custom = it.removePrefix("#").take(6) }, label = { RevealText(tr("Custom HEX color", "Свой цвет HEX")) },
        prefix = { RevealText("#") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    TextButton(onClick = { onChange((0xFF000000 or custom.toLong(16)).toInt()) }, enabled = custom.matches(Regex("[0-9a-fA-F]{6}")) && !custom.equals("%06X".format(argb and 0xFFFFFF), true)) { RevealText(tr("Apply color", "Применить цвет")) }
}

@Composable
private fun CustomSearchEditor(settings: Customization, onSave: (Customization) -> Unit) {
    var name by remember(settings.customSearchName) { mutableStateOf(settings.customSearchName) }
    var template by remember(settings.customSearchTemplate) { mutableStateOf(settings.customSearchTemplate) }
    val valid = name.isNotBlank() && UrlPolicy.isSearchTemplate(template.trim())
    OutlinedTextField(name, { name = it.take(40) }, label = { RevealText(tr("Name", "Название")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(template, { template = it }, label = { RevealText(tr("HTTPS search URL", "HTTPS-адрес поиска")) }, isError = !UrlPolicy.isSearchTemplate(template.trim()),
        supportingText = { RevealText(tr("Use {query} in place of the search text. Example: https://example.com/search?q={query}", "Вместо запроса укажите {query}. Пример: https://example.com/search?q={query}")) }, modifier = Modifier.fillMaxWidth())
    Button(onClick = { onSave(settings.copy(searchEngine = SearchEngine.CUSTOM, customSearchName = name.trim(), customSearchTemplate = template.trim())) }, enabled = valid) { RevealText(tr("Save & use", "Сохранить и использовать")) }
}

@Composable
private fun UrlEditor(value: String, onSave: (String) -> Unit) {
    var custom by remember(value) { mutableStateOf(value != "about:blank") }
    var url by remember(value) { mutableStateOf(value.takeUnless { it == "about:blank" } ?: "") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!custom, { custom = false; onSave("about:blank") }, label = { RevealText(tr("Tuman home", "Главная Tuman")) })
        FilterChip(custom, { custom = true }, label = { RevealText(tr("Custom page", "Свой сайт")) })
    }
    if (custom) {
        val valid = url.startsWith("https://") && UrlPolicy.isHomePage(url.trim())
        OutlinedTextField(url, { url = it }, label = { RevealText(tr("Website address", "Адрес сайта")) },
            isError = url.isNotEmpty() && !valid, placeholder = { RevealText("https://example.com") }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { onSave(url.trim()) }, enabled = valid && url.trim() != value) { RevealText(tr("Apply", "Применить")) }
    }
}

@Composable
fun protectionTitle(value: Protection): String = when (value) {
    Protection.STRICT -> tr("Strict", "Строгая")
    Protection.HARDENED -> tr("Hardened", "Усиленная")
    Protection.MAXIMUM -> tr("Maximum", "Максимальная")
}
@Composable
fun protectionDescription(value: Protection): String = when (value) {
    Protection.STRICT -> tr("Blocks known trackers and isolates third-party cookies", "Блокирует известные трекеры и изолирует сторонние cookies")
    Protection.HARDENED -> tr("Also blocks all third-party cookies", "Дополнительно блокирует все сторонние cookies")
    Protection.MAXIMUM -> tr("Also disables JavaScript; some sites will not work", "Также отключает JavaScript; часть сайтов перестанет работать")
}

@Composable
private fun cookieTitle(policy: CookiePolicy): String = when (policy) {
    CookiePolicy.ISOLATE -> tr("Isolate third-party cookies", "Изолировать сторонние cookies")
    CookiePolicy.BLOCK_THIRD_PARTY -> tr("Block third-party cookies", "Блокировать сторонние cookies")
    CookiePolicy.BLOCK_ALL -> tr("Block all cookies", "Блокировать все cookies")
}

@Composable
private fun PalettePicker(selected: ThemePalette, onSelect: (ThemePalette) -> Unit) {
    ThemePalette.entries.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { palette ->
                OutlinedCard(onClick = { onSelect(palette) }, modifier = Modifier.weight(1f).revealOnEnter(),
                    border = androidx.compose.foundation.BorderStroke(if (palette == selected) 2.dp else 1.dp,
                        if (palette == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .4f))) {
                    Column(Modifier.fillMaxWidth().background(Color(palette.darkBackground)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(18.dp).background(Color(palette.accent), CircleShape))
                            Box(Modifier.size(18.dp).background(Color(palette.darkSurface), CircleShape))
                            Box(Modifier.size(18.dp).background(Color(palette.lightBackground), CircleShape))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            RevealText(when (palette) {
                                ThemePalette.MIDNIGHT -> tr("Midnight", "Полночь")
                                ThemePalette.AURORA -> tr("Aurora", "Сияние")
                                ThemePalette.FOREST -> tr("Forest", "Лес")
                                ThemePalette.OCEAN -> tr("Ocean", "Океан")
                                ThemePalette.EMBER -> tr("Ember", "Закат")
                                ThemePalette.ROSE -> tr("Rose", "Роза")
                            }, color = Color(0xFFEAF2FF), style = MaterialTheme.typography.labelLarge)
                            if (palette == selected) Icon(Icons.Rounded.Check, tr("Selected", "Выбрано"), Modifier.size(18.dp), tint = Color(palette.accent))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolbarPlacementPicker(value: ToolbarPlacement, onChange: (ToolbarPlacement) -> Unit) {
    Choices(ToolbarPlacement.entries, value, {
        if (it == ToolbarPlacement.TOP) tr("Address bar at top", "Адресная панель сверху")
        else tr("Address bar at bottom", "Адресная панель снизу")
    }, onChange)
}
