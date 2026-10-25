package com.tuman.browser.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tuman.browser.data.Customization
import kotlin.math.roundToInt

fun Modifier.frostedGlass(opacity: Float, accent: Color, surface: Color): Modifier = this
    .background(surface.copy(alpha = opacity.coerceIn(0.45f, 1f)))
    .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .24f), accent.copy(alpha = .08f))), RoundedCornerShape(24.dp))

/** Only the captured background is blurred. Text and controls stay crisp. */
@Composable
fun GlassPanel(
    settings: Customization,
    backdrop: Bitmap?,
    viewport: IntSize,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var position by remember { mutableStateOf(IntOffset.Zero) }
    val image = remember(backdrop) { backdrop?.asImageBitmap() }
    val blurPx = with(LocalDensity.current) { settings.blur.dp.toPx() }
    val effect = remember(blurPx) {
        if (Build.VERSION.SDK_INT >= 31 && blurPx > 0)
            android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
        else null
    }
    Box(modifier.clip(RoundedCornerShape(24.dp)).onGloballyPositioned {
        val offset = it.positionInRoot()
        position = IntOffset(offset.x.roundToInt(), offset.y.roundToInt())
    }) {
        Box(Modifier.matchParentSize().graphicsLayer { renderEffect = effect }.drawWithContent {
            if (image != null && viewport.width > 0 && viewport.height > 0) {
                clipRect {
                    drawImage(image, dstOffset = IntOffset(-position.x, -position.y), dstSize = viewport)
                }
            }
        })
        Box(Modifier.matchParentSize().frostedGlass(settings.opacity, Color(settings.accentArgb), MaterialTheme.colorScheme.surface))
        content()
    }
}
