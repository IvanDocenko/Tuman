package com.tuman.browser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tuman.browser.data.OfficialResources
import com.tuman.browser.data.UrlPolicy
import com.tuman.browser.ui.theme.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OfficialResourcesScreen(onOpenUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BrandMark(Modifier.size(96.dp).revealOnEnter())
        RevealText(tr("Stay close.", "Оставайтесь рядом."), style = MaterialTheme.typography.headlineLarge)
        RevealText(tr("Tuman beyond the browser.", "Tuman за пределами браузера."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        val titles = listOf(tr("Studio website", "Сайт студии"), "GitHub", "Telegram")
        val descriptions = listOf(tr("Meet the studio and its projects", "Студия и её проекты"), tr("Code, releases and development", "Код, релизы и разработка"), tr("News and updates", "Новости и обновления"))
        val links = listOf(OfficialResources.STUDIO, OfficialResources.GITHUB, OfficialResources.TELEGRAM)
        val icons = listOf(Icons.Rounded.Language, Icons.Rounded.Code, Icons.AutoMirrored.Rounded.Send)
        titles.forEachIndexed { index, title ->
            val url = links[index]
            val available = url.startsWith("https://") && UrlPolicy.isHomePage(url)
            OutlinedCard(onClick = { if (available) onOpenUrl(url) }, enabled = available,
                modifier = Modifier.fillMaxWidth().revealOnEnter(index)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(icons[index], null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        RevealText(title, style = MaterialTheme.typography.titleMedium)
                        RevealText(if (available) descriptions[index] else if (index == 0) tr("Link coming later", "Ссылка появится позже") else tr("Link coming in update 2.1.4", "Ссылка появится в обновлении 2.1.4"), style = MaterialTheme.typography.bodySmall)
                    }
                    if (available) Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun LicenseScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val entries = listOf("NOTICE.txt", "Apache-2.0.txt", "Mozilla-notices.txt", "Dependency-notices.txt")
    val titles = listOf(tr("Acknowledgements", "Благодарности"), "Apache License 2.0", "Mozilla & third-party licenses", tr("Dependency notices", "Уведомления компонентов"))
    var selected by remember { mutableIntStateOf(-1) }
    var paragraphs by remember { mutableStateOf<List<String>?>(null) }
    var failure by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        paragraphs = null; failure = false
        if (selected >= 0) {
            try { paragraphs = withContext(Dispatchers.IO) { context.assets.open("licenses/${entries[selected]}").bufferedReader().use { it.readText() }.chunked(3000) } }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; failure = true }
        }
    }
    fun back() { if (selected >= 0) selected = -1 else onClose() }
    Dialog(onDismissRequest = ::back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MotionIconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back", "Назад")) }
                    RevealText(tr("Licenses", "Лицензии"), style = MaterialTheme.typography.titleLarge)
                }
                if (selected < 0) titles.forEachIndexed { index, title ->
                    TextButton(onClick = { selected = index }) { RevealText(title) }
                } else if (failure) RevealText(tr("Could not open this notice.", "Не удалось открыть уведомление."))
                else paragraphs?.let { chunks ->
                    LazyColumn(Modifier.weight(1f)) {
                        item { RevealText(titles[selected], modifier = Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.titleMedium) }
                        items(chunks) { RevealText(it, style = MaterialTheme.typography.bodySmall) }
                    }
                } ?: CircularProgressIndicator()
            }
        }
    }
}
