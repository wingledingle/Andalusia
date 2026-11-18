package com.silpa.feature.studio

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silpa.core.design.SilpaSkeuo
import com.silpa.core.design.SkeuoRail
import com.silpa.core.design.SkeuoToolKey
import com.silpa.core.design.Spacing
import com.silpa.engine.SelectOp
import compose.icons.TablerIcons
import compose.icons.tablericons.AdjustmentsHorizontal
import compose.icons.tablericons.ArrowBackUp
import compose.icons.tablericons.ArrowForwardUp
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.ArrowsMaximize
import compose.icons.tablericons.Brush
import compose.icons.tablericons.Bucket
import compose.icons.tablericons.ColorPicker
import compose.icons.tablericons.Eraser
import compose.icons.tablericons.HandFinger
import compose.icons.tablericons.LayersUnion
import compose.icons.tablericons.Marquee2
import compose.icons.tablericons.Maximize
import compose.icons.tablericons.Minimize
import compose.icons.tablericons.Palette
import compose.icons.tablericons.Pencil
import compose.icons.tablericons.Minus
import compose.icons.tablericons.VectorBeizer2
import compose.icons.tablericons.Hexagon
import compose.icons.tablericons.Square
import compose.icons.tablericons.Circle
import androidx.compose.material3.Icon
import compose.icons.tablericons.Photo
import compose.icons.tablericons.Wand
import io.github.skeuocompose.SkeuoOrientation
import io.github.skeuocompose.SkeuoPalettes
import io.github.skeuocompose.SkeuoSlider
import kotlin.math.roundToInt

fun Tool.icon() = when (this) {
    Tool.Brush -> TablerIcons.Brush
    Tool.Smudge -> TablerIcons.HandFinger
    Tool.Eraser -> TablerIcons.Eraser
    Tool.Select -> TablerIcons.Marquee2
    Tool.Transform -> TablerIcons.ArrowsMaximize
    Tool.Fill -> TablerIcons.Bucket
    Tool.Eyedropper -> TablerIcons.ColorPicker
}

/**
 * Studio. Canvas first: hardware-style rails float over the canvas and disappear in full screen.
 * Compact widths get top + bottom rails and bottom-sheet panels; expanded widths get a left tool
 * rail and floating side panels.
 */
@Composable
fun StudioScreen(projectId: String, onBack: () -> Unit, vm: StudioViewModel = viewModel()) {
    LaunchedEffect(projectId) { vm.open(projectId) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.saveNow() }
        lifecycle.lifecycle.addObserver(obs); onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.toast) { vm.toast?.let { snack.showSnackbar(it); vm.toast = null } }
    var canvasView by remember { mutableStateOf<CanvasView?>(null) }
    val context = LocalContext.current
    val share: (java.io.File, String) -> Unit = { file, mime ->
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share"))
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF2A2B2F))) {
        val wide = maxWidth >= 840.dp
        val doc = vm.doc
        if (doc != null) {
            AndroidView(
                factory = { ctx -> CanvasView(ctx, vm) { vm.fullscreen = !vm.fullscreen }.also { canvasView = it } },
                modifier = Modifier.fillMaxSize(),
                update = { v ->
                    // Read the state the canvas depends on so changes trigger a redraw.
                    vm.revision; vm.tool; vm.symmetry; vm.guides; vm.selectionVersion; vm.colorBlind; vm.corners; vm.floating; vm.playFrame
                    v.invalidate()
                },
            )
        }

        if (vm.fullscreen) {
            Box(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(Spacing.s)) {
                SkeuoRail { SkeuoToolKey(TablerIcons.Minimize, "Exit full screen", false, { vm.fullscreen = false }) }
            }
        } else if (doc != null) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.s)) {
                TopRail(vm, wide, onBack = { vm.saveNow(); onBack() }, modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth())

                val sideAlign = if (vm.leftHanded) Alignment.CenterEnd else Alignment.CenterStart
                if (wide) {
                    Row(Modifier.align(sideAlign), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        if (vm.leftHanded) { SizeOpacity(vm, 200.dp); ToolRail(vm, vertical = true) } else { ToolRail(vm, vertical = true); SizeOpacity(vm, 200.dp) }
                    }
                } else {
                    SizeOpacity(vm, 104.dp, Modifier.align(sideAlign).padding(bottom = 96.dp))
                }

                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    ContextBar(vm, compact = !wide)
                    if (doc.animation.enabled) Timeline(vm)
                    if (!wide) ToolRail(vm, vertical = false, available = this@BoxWithConstraints.maxWidth - 16.dp)
                }

                val panelMod = if (wide) Modifier.align(if (vm.leftHanded) Alignment.TopStart else Alignment.TopEnd).padding(top = 64.dp).width(380.dp).fillMaxHeight(0.86f)
                else Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.62f)
                AnimatedVisibility(
                    visible = vm.panel != Panel.None,
                    enter = if (wide) slideInHorizontally { it / 2 } + fadeIn() else slideInVertically { it } + fadeIn(),
                    exit = if (wide) slideOutHorizontally { it / 2 } + fadeOut() else slideOutVertically { it } + fadeOut(),
                    modifier = panelMod,
                ) {
                    PanelHost(vm, canvasView, share)
                }
            }
        }

        vm.referenceImage?.let { ReferenceWindow(vm, it) }

        (vm.busy ?: if (doc == null) "Opening…" else null)?.let { msg ->
            Surface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 8.dp) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text(msg)
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 96.dp))
    }
}

@Composable
private fun TopRail(vm: StudioViewModel, wide: Boolean, onBack: () -> Unit, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        SkeuoRail {
            SkeuoToolKey(TablerIcons.ArrowLeft, "Back to gallery", false, onBack)
            SkeuoToolKey(TablerIcons.Wand, "Actions", vm.panel == Panel.Actions, { vm.panel = if (vm.panel == Panel.Actions) Panel.None else Panel.Actions })
        }
        SkeuoRail {
            SkeuoToolKey(TablerIcons.Palette, "Brush library", vm.panel == Panel.Brushes || vm.panel == Panel.BrushStudio, { vm.panel = if (vm.panel == Panel.Brushes) Panel.None else Panel.Brushes })
            if (wide) SkeuoToolKey(TablerIcons.Photo, "Image Bank", vm.panel == Panel.Bank, { vm.panel = if (vm.panel == Panel.Bank) Panel.None else Panel.Bank })
            SkeuoToolKey(TablerIcons.LayersUnion, "Layers", vm.panel == Panel.Layers, { vm.panel = if (vm.panel == Panel.Layers) Panel.None else Panel.Layers })
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable { vm.panel = if (vm.panel == Panel.Color) Panel.None else Panel.Color },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color(vm.color)).border(3.dp, Color(0xFFE8E9EE), CircleShape))
            }
            if (wide) SkeuoToolKey(TablerIcons.Maximize, "Full screen", false, { vm.fullscreen = true })
        }
    }
}

@Composable
fun ToolRail(vm: StudioViewModel, vertical: Boolean, available: Dp = 1000.dp) {
    // Seven keys must fit even a 320dp phone: shrink spacing first, then keys (never below 40dp).
    val n = Tool.entries.size
    val gap = if (available < 400.dp) 3.dp else 6.dp
    val key = ((available - 12.dp - gap * (n - 1)) / n).coerceIn(40.dp, 48.dp)
    SkeuoRail(vertical = vertical, gap = gap, padding = if (available < 400.dp) 4.dp else 6.dp) {
        Tool.entries.forEach { t ->
            SkeuoToolKey(
                icon = t.icon(), label = t.label, selected = vm.tool == t, caption = t.short, size = if (vertical) 48.dp else key,
                onClick = {
                    if (vm.tool == t && t in listOf(Tool.Brush, Tool.Smudge, Tool.Eraser)) vm.panel = if (vm.panel == Panel.Brushes) Panel.None else Panel.Brushes
                    else {
                        if (vm.tool == Tool.Transform && t != Tool.Transform && vm.floating != null) vm.commitTransform()
                        if (vm.shapePending) vm.finishShape()
                        vm.tool = t
                        if (t == Tool.Transform) vm.beginTransform()
                    }
                },
                onLongClick = { if (t in listOf(Tool.Brush, Tool.Smudge, Tool.Eraser)) { vm.tool = t; vm.panel = Panel.BrushStudio } },
            )
        }
    }
}

@Composable
private fun SizeOpacity(vm: StudioViewModel, length: Dp, modifier: Modifier = Modifier) {
    val b = vm.brush
    SkeuoRail(modifier, vertical = true) {
        SkeuoToolKey(TablerIcons.ArrowBackUp, "Undo", false, vm::undo, enabled = vm.canUndo || vm.floating != null)
        SkeuoToolKey(TablerIcons.ArrowForwardUp, "Redo", false, vm::redo, enabled = vm.canRedo)
        Text("${b.size.roundToInt()}px", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE8E9EE))
        com.silpa.core.design.SilpaFader(sizeToSlider(b.size), { v -> vm.updateBrush { it.copy(size = sliderToSize(v)) } }, "Brush size", Modifier.height(length))
        com.silpa.core.design.SilpaFader(b.opacity, { v -> vm.updateBrush { it.copy(opacity = v.coerceIn(0.01f, 1f)) } }, "Brush opacity", Modifier.height(length * 0.8f), accent = Color(0xFFB8C4FF))
        Text("${(b.opacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE8E9EE))
    }
}

/** Size slider is logarithmic so both 1px pixel work and 1000px washes are reachable. */
fun sizeToSlider(s: Float) = (kotlin.math.ln(s.coerceIn(0.5f, 1000f) / 0.5f) / kotlin.math.ln(2000f)).toFloat()
fun sliderToSize(v: Float) = (0.5f * Math.pow(2000.0, v.toDouble())).toFloat()

@Composable
private fun ContextBar(vm: StudioViewModel, compact: Boolean) {
    val show = vm.tool in listOf(Tool.Select, Tool.Transform, Tool.Fill, Tool.Brush, Tool.Eraser)
    if (!show) return
    Surface(
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f), shadowElevation = 8.dp,
        modifier = Modifier.widthIn(max = 720.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                when (vm.tool) {
                    Tool.Brush, Tool.Eraser -> {
                        DrawMode.entries.forEach { m ->
                            if (compact) FilterChip(vm.drawMode == m, { vm.selectDrawMode(m) }, { Icon(m.icon(), m.label, Modifier.size(20.dp)) })
                            else FilterChip(vm.drawMode == m, { vm.selectDrawMode(m) }, { Text(m.label) }, leadingIcon = { Icon(m.icon(), null, Modifier.size(16.dp)) })
                        }
                        if (vm.drawMode in listOf(DrawMode.Polygon, DrawMode.Rect, DrawMode.Ellipse)) {
                            Divider(); Text("Fill", style = MaterialTheme.typography.labelLarge); Switch(vm.shapeFill, { vm.shapeFill = it })
                        }
                        if (vm.shapePending) {
                            Divider()
                            if (vm.drawMode == DrawMode.Polygon) TextButton(vm::closePolygon, enabled = vm.shapePoints.size >= 3) { Text("Close") }
                            TextButton(vm::cancelShape) { Text("Cancel") }
                            TextButton(vm::finishShape) { Text("Done") }
                        }
                    }
                    Tool.Select -> {
                        SelectShape.entries.forEach { s -> FilterChip(vm.selectShape == s, { vm.selectShape = s }, { Text(s.label) }) }
                        Divider()
                        SelectOp.entries.forEach { o -> FilterChip(vm.selectOp == o, { vm.selectOp = o }, { Text(o.name) }) }
                        Divider()
                        TextButton(vm::selectAll) { Text("All") }
                        TextButton(vm::invertSelection) { Text("Invert") }
                        TextButton(vm::deselect) { Text("Deselect") }
                        TextButton({ vm.copyToNewLayer(false) }) { Text("Copy → layer") }
                        TextButton({ vm.copyToNewLayer(true) }) { Text("Cut → layer") }
                        TextButton(vm::fillSelected) { Text("Fill") }
                        TextButton(vm::clearSelected) { Text("Clear") }
                    }
                    Tool.Transform -> {
                        TransformMode.entries.forEach { m -> FilterChip(vm.transformMode == m, { vm.transformMode = m }, { Text(m.label) }) }
                        Divider()
                        TextButton({ vm.flip(true) }) { Text("Flip H") }
                        TextButton({ vm.flip(false) }) { Text("Flip V") }
                        TextButton(vm::rotate90) { Text("Rotate 90°") }
                        TextButton(vm::fitTransformToCanvas) { Text("Fit") }
                        TextButton(vm::resetTransform) { Text("Reset") }
                        TextButton({ vm.cancelTransform(); vm.tool = Tool.Brush }) { Text("Cancel") }
                        TextButton({ vm.commitTransform(); vm.tool = Tool.Brush }) { Text("Done") }
                    }
                    Tool.Fill -> {
                        FillMode.entries.forEach { m -> FilterChip(vm.fillMode == m, { vm.fillMode = m }, { Text(m.label) }) }
                        Divider()
                        Text("All layers", style = MaterialTheme.typography.labelLarge); Switch(vm.fillAllLayers, { vm.fillAllLayers = it })
                    }
                    else -> Unit
                }
            }
            if ((vm.tool == Tool.Brush || vm.tool == Tool.Eraser) && vm.drawMode != DrawMode.Freehand) {
                Text(vm.drawMode.label + " · " + when (vm.drawMode) {
                    DrawMode.Line -> "Drag to draw a straight line"
                    DrawMode.Curve -> if (vm.shapePending) "Now drag to bend it — release to finish" else "Drag to draw a line, then drag again to bend it"
                    DrawMode.Polygon -> if (vm.shapePending) "Tap to add corners · tap the first corner to close" else "Tap to place corners"
                    DrawMode.Rect -> "Drag to draw a rectangle"
                    DrawMode.Ellipse -> "Drag to draw an ellipse"
                    else -> ""
                }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
            }
            if ((vm.tool == Tool.Fill && vm.fillMode == FillMode.Flood) || (vm.tool == Tool.Select && vm.selectShape == SelectShape.Wand)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tolerance ${vm.fillTolerance.roundToInt()}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(110.dp))
                    Slider(vm.fillTolerance, { vm.fillTolerance = it }, valueRange = 0f..255f, modifier = Modifier.widthIn(max = 360.dp))
                }
            }
        }
    }
}

fun DrawMode.icon() = when (this) {
    DrawMode.Freehand -> TablerIcons.Pencil
    DrawMode.Line -> TablerIcons.Minus
    DrawMode.Curve -> TablerIcons.VectorBeizer2
    DrawMode.Polygon -> TablerIcons.Hexagon
    DrawMode.Rect -> TablerIcons.Square
    DrawMode.Ellipse -> TablerIcons.Circle
}

@Composable
private fun Divider() = Box(Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outlineVariant))

@Composable
fun PanelSurface(title: String, onClose: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 16.dp, tonalElevation = 2.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                actions()
                TextButton(onClose) { Text("Done") }
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
private fun PanelHost(vm: StudioViewModel, canvasView: CanvasView?, share: (java.io.File, String) -> Unit) {
    val close = { vm.panel = Panel.None }
    when (vm.panel) {
        Panel.Brushes -> BrushesPanel(vm, close)
        Panel.BrushStudio -> BrushStudioPanel(vm, close)
        Panel.Color -> ColorPanel(vm, close)
        Panel.Layers -> LayersPanel(vm, close)
        Panel.Actions -> ActionsPanel(vm, canvasView, share, close)
        Panel.Bank -> StudioBankPanel(vm, close)
        Panel.None -> Box(Modifier.heightIn(min = 1.dp))
    }
}
