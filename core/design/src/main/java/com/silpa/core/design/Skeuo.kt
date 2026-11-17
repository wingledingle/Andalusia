package com.silpa.core.design

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.skeuocompose.SkeuoBevelKind
import io.github.skeuocompose.SkeuoPalette
import io.github.skeuocompose.SkeuoPalettes
import io.github.skeuocompose.SkeuoSurface
import io.github.skeuocompose.SkeuoSurfaceStyle
import io.github.skeuocompose.SkeuoTextureKind

/** Silpa's tactile palettes: graphite hardware for tools, the icon's cobalt for primary actions. */
object SilpaSkeuo {
    val Graphite = SkeuoPalette(
        highlight = Color(0xFF4A4C53),
        midTone = Color(0xFF34363C),
        lowTone = Color(0xFF26272C),
        shadow = Color(0xFF0E0F12),
        border = Color(0xFF5B5E66),
        textureTint = Color(0xFF8A8D96),
        content = Color(0xFFE8E9EE),
    )
    val Cobalt = SkeuoPalette(
        highlight = Color(0xFF5A79FF),
        midTone = Color(0xFF2F4FE0),
        lowTone = Color(0xFF1F3FD1),
        shadow = Color(0xFF0F1F78),
        border = Color(0xFF0C1A63),
        textureTint = Color(0xFF8EA4FF),
        content = Color.White,
    )
    val Sky = SkeuoPalette(
        highlight = Color(0xFF9BDDFB),
        midTone = Color(0xFF64C3EE),
        lowTone = Color(0xFF4CB4E6),
        shadow = Color(0xFF1F7FAE),
        border = Color(0xFF14628A),
        textureTint = Color(0xFF0B4566),
        content = Color(0xFF06283A),
    )
    val Paper = SkeuoPalettes.Ivory
    val Carbon = SkeuoPalettes.Carbon
}

/** Raised hardware-style tool key. Selected tools sit pressed-in and glow cobalt. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SkeuoToolKey(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = MinTouchTarget,
    onLongClick: (() -> Unit)? = null,
    caption: String? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    SkeuoSurface(
        modifier = modifier
            .size(width = size, height = if (caption != null) size + 8.dp else size)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { contentDescription = label; this.selected = selected }
            .combinedClickable(
                interactionSource = source, indication = null, enabled = enabled, role = Role.Button,
                onClickLabel = label, onClick = onClick, onLongClick = onLongClick,
            ),
        palette = if (selected) SilpaSkeuo.Cobalt else SilpaSkeuo.Graphite,
        style = SkeuoSurfaceStyle(
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(0.dp),
            raisedElevation = if (selected) 1.dp else 5.dp,
            pressedElevation = 1.dp,
            bevelKind = if (selected) SkeuoBevelKind.Concave else SkeuoBevelKind.Classic,
            textureKind = SkeuoTextureKind.None,
        ),
        pressed = pressed || selected,
    ) {
        if (caption == null) Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            androidx.compose.material3.Text(caption, fontSize = androidx.compose.ui.unit.TextUnit(9.5f, androidx.compose.ui.unit.TextUnitType.Sp), maxLines = 1, lineHeight = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp))
        }
    }
}

/** Machined graphite rail that holds tool keys. */
@Composable
fun SkeuoRail(modifier: Modifier = Modifier, vertical: Boolean = false, content: @Composable () -> Unit) {
    SkeuoSurface(
        modifier = modifier,
        palette = SilpaSkeuo.Carbon,
        style = SkeuoSurfaceStyle(
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(6.dp),
            raisedElevation = 10.dp,
            textureKind = SkeuoTextureKind.None,
            bevelKind = SkeuoBevelKind.Subtle,
        ),
    ) {
        if (vertical) Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
        else Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** Small round hardware button (used for panel toggles). */
@Composable
fun SkeuoRoundKey(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    SkeuoSurface(
        modifier = modifier.size(MinTouchTarget).semantics { contentDescription = label }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        palette = if (selected) SilpaSkeuo.Cobalt else SilpaSkeuo.Graphite,
        style = SkeuoSurfaceStyle(shape = CircleShape, contentPadding = PaddingValues(0.dp), raisedElevation = 6.dp, pressedElevation = 1.dp, textureKind = SkeuoTextureKind.None),
        pressed = pressed || selected,
    ) { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) }
}

@Composable
fun SkeuoPill(modifier: Modifier = Modifier, palette: SkeuoPalette = SilpaSkeuo.Graphite, content: @Composable () -> Unit) {
    SkeuoSurface(
        modifier = modifier,
        palette = palette,
        style = SkeuoSurfaceStyle(shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), raisedElevation = 4.dp, textureKind = SkeuoTextureKind.None),
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

/** Primary action button: SkeuoCompose bevel and depth without the costly per-frame grain texture. */
@Composable
fun SilpaButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, palette: SkeuoPalette = SilpaSkeuo.Cobalt, enabled: Boolean = true) {
    io.github.skeuocompose.SkeuoButton(
        text = text, onClick = onClick, modifier = modifier, enabled = enabled, palette = palette,
        style = SkeuoSurfaceStyle(shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), raisedElevation = 8.dp, pressedElevation = 2.dp, textureKind = SkeuoTextureKind.None),
    )
}

/**
 * Hardware-style vertical fader (brush size / opacity). Drawn with a few gradients in a single
 * Canvas so dragging it costs almost nothing. Drag anywhere along it; tap to jump.
 */
@Composable
fun SilpaFader(value: Float, onValueChange: (Float) -> Unit, label: String, modifier: Modifier = Modifier, accent: Color = Color(0xFF4CB4E6)) {
    val current = androidx.compose.runtime.rememberUpdatedState(value)
    val change = androidx.compose.runtime.rememberUpdatedState(onValueChange)
    androidx.compose.foundation.Canvas(
        modifier
            .size(width = MinTouchTarget, height = 160.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(value, 0f..1f)
                setProgress(label) { v -> change.value(v.coerceIn(0f, 1f)); true }
            }
            .then(androidx.compose.ui.Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val knob = 30.dp.toPx()
                    fun at(y: Float) = (1f - (y - knob / 2) / (size.height - knob)).coerceIn(0f, 1f)
                    change.value(at(down.position.y))
                    do {
                        val e = awaitPointerEvent()
                        e.changes.forEach { c -> if (c.pressed) { change.value(at(c.position.y)); c.consume() } }
                    } while (e.changes.any { it.pressed })
                }
            }),
    ) {
        val knobH = 30.dp.toPx(); val groove = 8.dp.toPx(); val cx = size.width / 2
        val travel = size.height - knobH
        val ky = (1f - current.value) * travel
        drawRoundRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF0B0C0E), Color(0xFF26272C))), topLeft = androidx.compose.ui.geometry.Offset(cx - groove / 2, knobH / 2), size = androidx.compose.ui.geometry.Size(groove, travel), cornerRadius = androidx.compose.ui.geometry.CornerRadius(groove / 2))
        drawRoundRect(accent.copy(alpha = 0.85f), topLeft = androidx.compose.ui.geometry.Offset(cx - groove / 2 + 1.5f, ky + knobH / 2), size = androidx.compose.ui.geometry.Size(groove - 3f, travel - ky), cornerRadius = androidx.compose.ui.geometry.CornerRadius(groove / 2))
        val kw = size.width * 0.78f
        drawRoundRect(Color(0x66000000), topLeft = androidx.compose.ui.geometry.Offset(cx - kw / 2, ky + 3f), size = androidx.compose.ui.geometry.Size(kw, knobH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
        drawRoundRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFF2F3F6), Color(0xFFB9BCC5)), startY = ky, endY = ky + knobH), topLeft = androidx.compose.ui.geometry.Offset(cx - kw / 2, ky), size = androidx.compose.ui.geometry.Size(kw, knobH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
        for (i in -1..1) drawLine(Color(0x55000000), androidx.compose.ui.geometry.Offset(cx - kw * 0.28f, ky + knobH / 2 + i * 5.dp.toPx()), androidx.compose.ui.geometry.Offset(cx + kw * 0.28f, ky + knobH / 2 + i * 5.dp.toPx()), strokeWidth = 1.5f)
    }
}
