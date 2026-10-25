package com.tuman.browser.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuman.browser.R
import com.tuman.browser.data.*
import com.tuman.browser.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Icon(painterResource(R.drawable.ic_tuman_foreground), null, modifier, tint = MaterialTheme.colorScheme.primary)
}

@Composable
fun HomeScreen(settings: Customization, onNavigate: (String) -> Unit, onSettings: () -> Unit, onLanguage: (Language) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var languageOpen by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val focus = LocalFocusManager.current
    LaunchedEffect(settings.showClock) {
        if (settings.showClock) while (true) { now = LocalDateTime.now(); delay(15_000) }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (settings.ambientFog) FogParticles(Modifier.fillMaxSize(), settings.animations)
        EntranceSequence {
        Column(Modifier.fillMaxSize().safeDrawingPadding()
            .padding(top = 12.dp + (if (settings.tabPlacement == TabPlacement.TOP) 76.dp else 0.dp) + (if (settings.toolbarPlacement == com.tuman.browser.data.ToolbarPlacement.TOP) 124.dp else 0.dp), bottom = 16.dp + (if (settings.tabPlacement == TabPlacement.BOTTOM) 72.dp else 0.dp) + (if (settings.toolbarPlacement == com.tuman.browser.data.ToolbarPlacement.BOTTOM) 124.dp else 0.dp))
            .verticalScroll(rememberScrollState()).padding(horizontal = 26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                RevealText("T U M A N", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Box {
                    TextButton(onClick = { languageOpen = true }) {
                        Icon(Icons.Rounded.Language, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); RevealText(if (settings.language == Language.EN) "EN" else "RU")
                    }
                    DropdownMenu(expanded = languageOpen, onDismissRequest = { languageOpen = false }) {
                        Language.entries.forEach { lang -> DropdownMenuItem(text = { RevealText(if (lang == Language.EN) "English" else "Русский") },
                            onClick = { languageOpen = false; onLanguage(lang) }) }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (settings.showClock) Column {
                RevealText(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 54.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
                RevealText(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", if (settings.language == Language.RU) Locale.forLanguageTag("ru") else Locale.ENGLISH)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else BrandMark(Modifier.size(96.dp))
            if (settings.showTagline) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RevealText(tr("Room to\nexplore.", "Пространство\nдля открытий."), style = MaterialTheme.typography.headlineLarge)
                RevealText(tr("A quieter corner of the internet.", "Ваш тихий уголок интернета."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().revealOnEnter(3),
                placeholder = { RevealText(tr("Search or enter address", "Поиск или адрес")) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { MotionIconButton(onClick = { focus.clearFocus(); onNavigate(search) }, enabled = search.isNotBlank()) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, tr("Go", "Перейти")) } },
                shape = MaterialTheme.shapes.large, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); onNavigate(search) }))
            if (settings.showShortcuts) {
                RevealText(tr("QUICK LINKS", "БЫСТРЫЕ ССЫЛКИ"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Wikipedia" to "https://www.wikipedia.org", "YouTube" to "https://www.youtube.com", "GitHub" to "https://github.com").forEach { (name, url) ->
                        FilledTonalButton(onClick = { onNavigate(url) }, modifier = Modifier.weight(1f).revealOnEnter(4), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 14.dp), shape = MaterialTheme.shapes.medium) {
                            RevealText(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
            AssistChip(onClick = onSettings, modifier = Modifier.revealOnEnter(5), label = { RevealText(protectionTitle(settings.protection) + tr(" protection", " защита")) },
                leadingIcon = { Icon(Icons.Rounded.Shield, null, Modifier.size(18.dp)) })
            Spacer(Modifier.height(12.dp))
        }
        }
    }
}

@Composable
fun LaunchIntro(settings: Customization, onFinished: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (revealed) 1f else .86f, tween(if (settings.animations) 650 else 0), label = "intro-mark")
    val alpha by animateFloatAsState(if (revealed) 1f else 0f, tween(if (settings.animations) 420 else 0), label = "intro-fade")
    LaunchedEffect(Unit) { revealed = true; delay(if (settings.animations) 1050 else 250); onFinished() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            if (settings.ambientFog) FogParticles(Modifier.fillMaxSize(), settings.animations)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }) {
                BrandMark(Modifier.size(148.dp))
                RevealText("tuman", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(18.dp))
                RevealText(tr("Find your space.", "Найдите своё пространство."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
