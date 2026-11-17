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
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    SkeuoSurface(
        modifier = modifier
            .size(size)
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
            textureKind = SkeuoTextureKind.Matte,
        ),
        pressed = pressed || selected,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
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
            textureKind = SkeuoTextureKind.Grainy,
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
        style = SkeuoSurfaceStyle(shape = CircleShape, contentPadding = PaddingValues(0.dp), raisedElevation = 6.dp, pressedElevation = 1.dp, textureKind = SkeuoTextureKind.Concentric),
        pressed = pressed || selected,
    ) { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) }
}

@Composable
fun SkeuoPill(modifier: Modifier = Modifier, palette: SkeuoPalette = SilpaSkeuo.Graphite, content: @Composable () -> Unit) {
    SkeuoSurface(
        modifier = modifier,
        palette = palette,
        style = SkeuoSurfaceStyle(shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), raisedElevation = 4.dp, textureKind = SkeuoTextureKind.Matte),
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}
