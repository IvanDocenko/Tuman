package com.tuman.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tuman.browser.data.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin
import com.tuman.browser.ui.theme.*

@Composable
fun OnboardingScreens(initial: Customization, onComplete: suspend (Customization) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var language by rememberSaveable { mutableStateOf(initial.language) }
    var level by rememberSaveable { mutableStateOf(initial.protection) }
    var toolbarPlacement by rememberSaveable { mutableStateOf(initial.toolbarPlacement) }
    var placement by rememberSaveable { mutableStateOf(initial.tabPlacement) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var maximumWarning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selected = initial.copy(language = language, protection = level, tabPlacement = placement, toolbarPlacement = toolbarPlacement)
    BackHandler(page > 0 && !busy) { page-- }
    TumanTheme(selected) {
    val animate = motionEnabled()
    if (maximumWarning) MaximumProtectionDialog(onDismiss = { maximumWarning = false }, onConfirm = { level = Protection.MAXIMUM; maximumWarning = false })
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FogParticles(Modifier.fillMaxSize(), selected.animations)
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 26.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                RevealText("T U M A N", color = MaterialTheme.colorScheme.primary)
                LanguagePicker(language) { language = it }
            }
            AnimatedContent(page, modifier = Modifier.weight(1f), label = "welcome", transitionSpec = {
                (fadeIn(tween(if (animate) 480 else 0)) + slideInHorizontally(tween(if (animate) 480 else 0)) { it / 6 }) togetherWith fadeOut(tween(if (animate) 220 else 0))
            }) { current ->
                EntranceSequence {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    BrandMark(Modifier.size(if (current == 0) 128.dp else 80.dp).revealOnEnter())
                    RevealText(when (current) {
                        0 -> tr("Your space.\nYour pace.", "Ваше пространство.\nВаш ритм.")
                        1 -> tr("Feel at ease.", "Спокойствие\nвнутри.")
                        else -> tr("Make it yours.", "Сделайте\nего своим.")
                    }, style = MaterialTheme.typography.headlineLarge)
                    when (current) {
                        0 -> {
                            RevealText(tr("Meet a calmer way to browse. Beautifully simple, thoughtfully private.", "Откройте спокойный интернет. Красивый интерфейс и продуманная приватность."), style = MaterialTheme.typography.titleLarge)
                            RevealText(tr("Less tracking. More space for you.", "Меньше слежки. Больше пространства для вас."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            RevealText("TUMAN ${com.tuman.browser.BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                        1 -> {
                            Protection.entries.forEach { protection ->
                                OutlinedCard(onClick = { if (protection == Protection.MAXIMUM && level != protection) maximumWarning = true else level = protection }, modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.outlinedCardColors(containerColor = if (level == protection) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else MaterialTheme.colorScheme.surface)) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        RadioButton(level == protection, null)
                                        Column(Modifier.padding(start = 10.dp)) { RevealText(protectionTitle(protection)); RevealText(protectionDescription(protection), style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                            }
                            RevealText(tr("Privacy is not anonymity: websites can still see your IP address.", "Приватность не означает анонимность: сайты по-прежнему видят ваш IP-адрес."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> {
                            RevealText(tr("Address bar", "Адресная панель"), style = MaterialTheme.typography.titleMedium)
                            ToolbarPlacementPicker(toolbarPlacement) { toolbarPlacement = it }
                            RevealText(tr("Tab strip", "Панель вкладок"), style = MaterialTheme.typography.titleMedium)
                            TabPlacement.entries.forEach { item ->
                                val title = when (item) { TabPlacement.TOP -> tr("Tabs at the top", "Вкладки сверху"); TabPlacement.BOTTOM -> tr("Tabs within reach", "Вкладки под рукой"); TabPlacement.HIDDEN -> tr("Keep it minimal", "Минимализм") }
                                OutlinedCard(onClick = { placement = item }, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        RadioButton(placement == item, null); RevealText(title, Modifier.padding(start = 12.dp))
                                    }
                                }
                            }
                            RevealText(tr("Change colors, layout, search and more whenever you like in Settings.", "Цвет, расположение, поиск и другие детали всегда можно изменить в настройках."))
                        }
                    }
                }
                }
            }
            if (error) RevealText(tr("Could not save. Please try again.", "Не удалось сохранить. Попробуйте снова."), color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i -> Box(Modifier.height(4.dp).weight(1f).background(if (page == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .3f), CircleShape)) }
            }
            Spacer(Modifier.height(18.dp))
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).revealOnEnter(4), onClick = {
                if (page < 2) page++ else scope.launch {
                    busy = true
                    try { onComplete(selected) }
                    catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = true }
                    finally { busy = false }
                }
            }) { RevealText(if (busy) tr("Getting ready…", "Подготовка…") else if (page == 2) tr("Enter Tuman", "Открыть Tuman") else tr("Continue", "Продолжить")) }
            Spacer(Modifier.height(8.dp))
        }
    }
    }
}

@Composable
fun FogParticles(modifier: Modifier = Modifier, animated: Boolean = true) {
    val moving = animated && motionEnabled()
    val phase = if (moving) {
        val transition = rememberInfiniteTransition(label = "ambient-fog")
        val value by transition.animateFloat(0f, 6.283185f,
            infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "fog-drift")
        value
    } else 0f
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Canvas(modifier) {
        // Wide overlapping wisps, not particles. A very slow drift remains behind the content.
        repeat(6) { i ->
            val x = size.width * (.12f + (i % 3) * .40f + sin(phase + i * 1.7f) * .16f)
            val y = size.height * (.18f + (i / 3) * .48f + sin(phase + i * .8f) * .045f)
            val radius = size.width * (.62f + .10f * sin(phase + i))
            val color = if (i % 2 == 0) accent else secondary
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .065f), color.copy(alpha = .025f), Color.Transparent), Offset(x, y), radius), radius, Offset(x, y))
        }
    }
}
