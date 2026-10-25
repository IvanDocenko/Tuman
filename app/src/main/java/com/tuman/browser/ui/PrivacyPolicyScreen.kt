package com.tuman.browser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tuman.browser.data.Language
import com.tuman.browser.ui.theme.LocalCustomization
import com.tuman.browser.ui.theme.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PrivacyPolicyScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val language = LocalCustomization.current.language
    var paragraphs by remember { mutableStateOf<List<String>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(language) {
        try {
            paragraphs = withContext(Dispatchers.IO) {
                context.assets.open("privacy/${if (language == Language.RU) "ru" else "en"}.txt")
                    .bufferedReader().use { it.readText() }.split("\n\n").filter { it.isNotBlank() }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            failed = true
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MotionIconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back", "Назад")) }
                    RevealText(tr("Privacy policy", "Политика конфиденциальности"), style = MaterialTheme.typography.titleLarge)
                }
                if (failed) RevealText(tr("Could not open the policy.", "Не удалось открыть политику."))
                else paragraphs?.let { parts ->
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        itemsIndexed(parts) { _, text ->
                            RevealText(text.removePrefix("# ").trim(), style = if (text.startsWith("# ")) {
                                MaterialTheme.typography.titleLarge
                            } else MaterialTheme.typography.bodyLarge)
                        }
                    }
                } ?: CircularProgressIndicator()
            }
        }
    }
}
