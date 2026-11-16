package com.silpa.feature.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.silpa.core.design.SilpaColors
import com.silpa.core.design.SilpaToolButton
import com.silpa.core.design.Spacing
import kotlin.math.roundToInt

enum class Tool(val label: String) {
    Brush("Brush"), Smudge("Smudge"), Eraser("Eraser"), Select("Select"),
    Transform("Transform"), Fill("Fill"), Eyedropper("Eyedropper"),
}

@Composable
private fun Tool.painter(): Painter = when (this) {
    Tool.Brush -> rememberVectorPainter(Icons.Outlined.Brush)
    Tool.Smudge -> rememberVectorPainter(Icons.Outlined.Gesture)
    Tool.Eraser -> painterResource(R.drawable.ic_tool_eraser)
    Tool.Select -> rememberVectorPainter(Icons.Outlined.SelectAll)
    Tool.Transform -> rememberVectorPainter(Icons.Outlined.OpenWith)
    Tool.Fill -> rememberVectorPainter(Icons.Outlined.FormatColorFill)
    Tool.Eyedropper -> rememberVectorPainter(Icons.Outlined.Colorize)
}

/**
 * Studio shell. Canvas first: chrome floats over a neutral backdrop and collapses to nothing in
 * full-screen mode. Phones get a bottom dock; expanded widths (>= 840dp) get a left tool rail.
 * The drawing engine plugs into [CanvasArea] in step 2.
 */
@Composable
fun StudioScreen(canvasWidth: Int, canvasHeight: Int, onBack: () -> Unit) {
    var tool by rememberSaveable { mutableStateOf(Tool.Brush) }
    var size by rememberSaveable { mutableFloatStateOf(0.25f) }
    var opacity by rememberSaveable { mutableFloatStateOf(1f) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var color by rememberSaveable { mutableStateOf(0xFF1F3FD1.toInt()) }

    BoxWithConstraints(Modifier.fillMaxSize().background(SilpaColors.CanvasBackdrop)) {
        val wide = maxWidth >= 840.dp
        CanvasArea(canvasWidth, canvasHeight, Modifier.fillMaxSize().padding(if (fullscreen) 0.dp else 56.dp))

        if (fullscreen) {
            Box(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(Spacing.s)) {
                FloatingBar {
                    SilpaToolButton(Icons.Outlined.FullscreenExit, "Exit full screen", false, { fullscreen = false })
                }
            }
            return@BoxWithConstraints
        }

        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.s)) {
            // Top bar: navigation, history, panels.
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FloatingBar {
                    SilpaToolButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back to home", false, onBack)
                    Text(
                        "Untitled · $canvasWidth × $canvasHeight",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = Spacing.m),
                        maxLines = 1,
                    )
                }
                FloatingBar {
                    SilpaToolButton(Icons.AutoMirrored.Outlined.Undo, "Undo", false, {}, enabled = false)
                    SilpaToolButton(Icons.AutoMirrored.Outlined.Redo, "Redo", false, {}, enabled = false)
                    SilpaToolButton(Icons.Outlined.Layers, "Layers", false, {})
                    ColorSwatch(Color(color))
                    SilpaToolButton(Icons.Outlined.Fullscreen, "Full screen", false, { fullscreen = true })
                }
            }

            // Brush size and opacity live on the edge, where the off hand rests.
            Column(
                Modifier.align(if (wide) Alignment.CenterEnd else Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                EdgeSlider(size, { size = it }, "Brush size", "${(1 + size * 499).roundToInt()}")
                EdgeSlider(opacity, { opacity = it }, "Opacity", "${(opacity * 100).roundToInt()}%")
            }

            // Tools: at most 7 on the main bar.
            if (wide) {
                FloatingBar(Modifier.align(Alignment.CenterStart), vertical = true) {
                    Tool.entries.forEach { t -> SilpaToolButton(t.painter(), t.label, t == tool, { tool = t }) }
                }
            } else {
                FloatingBar(Modifier.align(Alignment.BottomCenter)) {
                    Tool.entries.forEach { t -> SilpaToolButton(t.painter(), t.label, t == tool, { tool = t }) }
                }
            }
        }
    }
}

@Composable
private fun CanvasArea(w: Int, h: Int, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .aspectRatio(w.toFloat() / h, matchHeightConstraintsFirst = h >= w)
                .shadow(12.dp)
                .background(SilpaColors.Paper),
        )
    }
}

@Composable
private fun FloatingBar(
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        shadowElevation = 6.dp,
    ) {
        if (vertical) {
            Column(Modifier.padding(Spacing.xs), horizontalAlignment = Alignment.CenterHorizontally) { content() }
        } else {
            Row(Modifier.padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) { content() }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                .semantics { contentDescription = "Current color" },
        )
    }
}

/** Track-less vertical slider: drag anywhere on it. Exposes progress semantics for TalkBack. */
@Composable
private fun EdgeSlider(value: Float, onValueChange: (Float) -> Unit, label: String, readout: String) {
    val current by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    Box(
        Modifier
            .width(40.dp)
            .height(168.dp)
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f))
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, drag ->
                    change.consume()
                    onChange((current - drag / size.height).coerceIn(0f, 1f))
                }
            }
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { v -> onChange(v.coerceIn(0f, 1f)); true }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(value.coerceAtLeast(0.02f))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        )
        Text(
            readout,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = Spacing.s),
        )
        Spacer(Modifier)
    }
}
