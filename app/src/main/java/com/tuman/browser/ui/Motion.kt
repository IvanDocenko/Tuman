package com.tuman.browser.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.tuman.browser.ui.theme.LocalCustomization

/** App motion respects both the user's switch and Android's animation accessibility setting. */
@Composable
fun motionEnabled(): Boolean = LocalCustomization.current.animations && ValueAnimator.areAnimatorsEnabled()

private class EntranceSequence { var next = 0 }
private val LocalEntranceSequence = staticCompositionLocalOf<EntranceSequence?> { null }

@Composable
fun EntranceSequence(content: @Composable () -> Unit) {
    val sequence = remember { EntranceSequence() }
    CompositionLocalProvider(LocalEntranceSequence provides sequence, content = content)
}

fun Modifier.revealOnEnter(order: Int = 0): Modifier = composed {
    val enabled = motionEnabled()
    var visibleOnce by remember { mutableStateOf(false) }
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    val rise = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(visibleOnce, enabled) {
        if (!enabled) progress.snapTo(1f)
        else if (visibleOnce) progress.animateTo(1f, tween(600, (order * 65).coerceAtMost(390), FastOutSlowInEasing))
    }
    this.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        if (bounds.width > 0f && bounds.height > 0f) visibleOnce = true
    }.graphicsLayer {
        alpha = if (enabled) progress.value else 1f
        translationY = if (enabled) (1f - progress.value) * rise else 0f
    }
}

@Composable
fun RevealText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified, textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip,
    style: TextStyle = LocalTextStyle.current) {
    val sequence = LocalEntranceSequence.current
    val order = remember { sequence?.let { it.next++ } ?: 0 }
    // Re-enter when the actual text changes (language, title, count), not on every recomposition.
    key(text) {
        Text(text, modifier.revealOnEnter(order), color = color, fontSize = fontSize, fontWeight = fontWeight,
            letterSpacing = letterSpacing, textAlign = textAlign, maxLines = maxLines, overflow = overflow, style = style)
    }
}

@Composable
fun MotionIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = motionEnabled()
    val scale by animateFloatAsState(if (pressed && motion) .86f else 1f,
        spring(dampingRatio = .55f, stiffness = 600f), label = "icon-press")
    IconButton(onClick, modifier.graphicsLayer { scaleX = scale; scaleY = scale }, enabled,
        interactionSource = interaction, content = content)
}
