package com.silpa.feature.studio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Layout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.silpa.core.design.SilpaSkeuo
import com.silpa.engine.FilterKind
import com.silpa.engine.Ops
import com.silpa.engine.SymmetryKind
import com.silpa.ml.Bank
import com.silpa.ml.BankCategories
import com.silpa.ml.BankItem
import compose.icons.TablerIcons
import compose.icons.tablericons.PlayerPause
import compose.icons.tablericons.PlayerPlay
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Share
import compose.icons.tablericons.X
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class ActionTab(val label: String) { Add("Add"), Adjust("Adjust"), Comic("Comic"), Guides("Guides"), Animate("Animate"), Canvas("Canvas"), Share("Share"), Prefs("Prefs") }

@Composable
fun ActionsPanel(vm: StudioViewModel, canvasView: CanvasView?, share: (java.io.File, String) -> Unit, close: () -> Unit) {
    var tab by remember { mutableStateOf(ActionTab.Add) }
    PanelSurface("Actions", close) {
        Column(Modifier.fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 8.dp, containerColor = Color.Transparent) {
                ActionTab.entries.forEach { t -> Tab(tab == t, { tab = t }, text = { Text(t.label) }) }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp)) {
                when (tab) {
                    ActionTab.Add -> AddTab(vm)
                    ActionTab.Adjust -> AdjustTab(vm)
                    ActionTab.Comic -> ComicTab(vm)
                    ActionTab.Guides -> GuidesTab(vm)
                    ActionTab.Animate -> AnimateTab(vm, share)
                    ActionTab.Canvas -> CanvasTab(vm, canvasView)
                    ActionTab.Share -> ShareTab(vm, share)
                    ActionTab.Prefs -> PrefsTab(vm)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Header(t: String) = Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))

@Composable
private fun Labeled(label: String, value: Float, range: ClosedFloatingPointRange<Float>, fmt: (Float) -> String = { it.roundToInt().toString() }, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(112.dp))
        Slider(value, onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(fmt(value), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun AddTab(vm: StudioViewModel) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::importImageLayer) }
    var text by remember { mutableStateOf(false) }
    ActionRow("Image Bank references", "Add references as layers, reference windows or stamp brushes") { vm.panel = Panel.Bank }
    ActionRow("Insert a photo", "Adds it as a new layer, ready to move and scale") { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    ActionRow("Add text", "Type, pick a font, then position it") { text = true }
    ActionRow("Reference window", "Float a live snapshot of this canvas while you work") { vm.referenceImage = vm.compositor.renderPreview(1024) }
    ActionRow("Copy canvas to new layer", "Flattened copy of everything visible") { vm.addLayerWith("Canvas copy", full = vm.compositor.renderFull(paper = false)) }
    if (text) TextDialog(vm) { text = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextDialog(vm: StudioViewModel, dismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    var size by remember { mutableFloatStateOf(((vm.doc?.width ?: 1000) / 12f)) }
    var family by remember { mutableStateOf("sans-serif") }
    var bold by remember { mutableStateOf(true) }
    var italic by remember { mutableStateOf(false) }
    var outline by remember { mutableStateOf(false) }
    var align by remember { mutableStateOf(Layout.Alignment.ALIGN_CENTER) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Add text") }, text = {
        Column {
            OutlinedTextField(value, { value = it }, label = { Text("Text") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Labeled("Size", size, 8f..800f) { size = it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("sans-serif" to "Sans", "serif" to "Serif", "monospace" to "Mono", "cursive" to "Script", "sans-serif-condensed" to "Condensed", "casual" to "Casual").forEach { (f, n) -> FilterChip(family == f, { family = f }, { Text(n) }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(bold, { bold = !bold }, { Text("Bold") }); FilterChip(italic, { italic = !italic }, { Text("Italic") }); FilterChip(outline, { outline = !outline }, { Text("Outline") })
                listOf(Layout.Alignment.ALIGN_NORMAL to "Left", Layout.Alignment.ALIGN_CENTER to "Center", Layout.Alignment.ALIGN_OPPOSITE to "Right").forEach { (a, n) -> FilterChip(align == a, { align = a }, { Text(n) }) }
            }
        }
    }, confirmButton = { TextButton({ if (value.isNotBlank()) vm.addText(value, size, family, bold, italic, align, outline); dismiss() }) { Text("Add") } },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}

@Composable
private fun AdjustTab(vm: StudioViewModel) {
    var open by remember { mutableStateOf<FilterKind?>(null) }
    Text("Applies to “${vm.doc?.active?.name}”${if (vm.selection.active) " inside the selection" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FilterKind.entries.forEach { k -> ActionRow(k.label) { open = k } }
    open?.let { FilterDialog(vm, it) { open = null } }
}

@Composable
private fun FilterDialog(vm: StudioViewModel, kind: FilterKind, dismiss: () -> Unit) {
    val values = remember { kind.defaults.toMutableList().let { l -> androidx.compose.runtime.mutableStateListOf<Float>().apply { addAll(l) } } }
    val src = remember { vm.doc?.active?.let { vm.layerThumb(it, 420) } }
    val preview by produceState<ImageBitmap?>(null, values.toList()) {
        val s = src ?: return@produceState
        val scaled = values.mapIndexed { i, v -> if (kind.params.getOrNull(i)?.first in listOf("Radius", "Distance", "Shift", "Cell")) v * s.width / (vm.doc?.width ?: s.width).toFloat() else v }
        value = withContext(Dispatchers.Default) { runCatching { vm.filterPreview(kind, scaled.map { it.coerceAtLeast(if (kind == FilterKind.Pixelate || kind == FilterKind.Halftone) 2f else it) }, s).asImageBitmap() }.getOrNull() }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text(kind.label) }, text = {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                preview?.let { Image(it, "Preview", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
            kind.params.forEachIndexed { i, (name, range) -> Labeled(name, values[i], range, { if (range.endInclusive <= 3f) "%.2f".format(it) else it.roundToInt().toString() }) { values[i] = it } }
        }
    }, confirmButton = { TextButton({ vm.applyFilter(kind, values.toList()); dismiss() }) { Text("Apply") } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComicTab(vm: StudioViewModel) {
    var layout by remember { mutableStateOf("Manga 5") }
    var gutter by remember { mutableFloatStateOf(40f) }
    var border by remember { mutableFloatStateOf(8f) }
    var white by remember { mutableStateOf(true) }
    Header("Panel layout")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Ops.panelLayouts.keys.forEach { k -> FilterChip(layout == k, { layout = k }, { Text(k) }) } }
    Labeled("Gutter", gutter, 0f..160f) { gutter = it }
    Labeled("Border", border, 1f..40f) { border = it }
    Row(verticalAlignment = Alignment.CenterVertically) { Text("White gutters", Modifier.weight(1f)); Switch(white, { white = it }) }
    com.silpa.core.design.SilpaButton(text = "Add panels", onClick = { vm.addPanels(layout, gutter, border, white) }, palette = SilpaSkeuo.Cobalt, modifier = Modifier.fillMaxWidth())
    Header("Effects (uses current color, respects selection)")
    var angle by remember { mutableFloatStateOf(0f) }
    var count by remember { mutableFloatStateOf(180f) }
    Labeled("Angle", angle, 0f..180f) { angle = it }
    Labeled("Density", count, 20f..600f) { count = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ vm.addSpeedLines(angle, count.roundToInt()) }) { Text("Speed lines") }
        TextButton({ vm.addFocusLines(count.roundToInt(), 0.35f) }) { Text("Focus lines") }
    }
    Header("Screentone")
    var cell by remember { mutableFloatStateOf(14f) }
    var dot by remember { mutableFloatStateOf(6f) }
    Labeled("Spacing", cell, 4f..60f) { cell = it }
    Labeled("Dot size", dot, 1f..40f) { dot = it }
    TextButton({ vm.addScreentone(cell, dot, 45f) }) { Text("Add screentone") }
    Header("Speech balloons")
    var words by remember { mutableStateOf("") }
    OutlinedTextField(words, { words = it }, label = { Text("Dialogue") }, modifier = Modifier.fillMaxWidth())
    Row { TextButton({ vm.addBalloon(words, false) }) { Text("Speech balloon") }; TextButton({ vm.addBalloon(words, true) }) { Text("Thought bubble") } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuidesTab(vm: StudioViewModel) {
    val g = vm.guides
    Header("Drawing guide")
    Row(verticalAlignment = Alignment.CenterVertically) { Text("Grid", Modifier.weight(1f)); Switch(g.grid, { vm.guides = g.copy(grid = it) }) }
    if (g.grid) {
        Labeled("Grid size", g.gridSize.toFloat(), 10f..500f) { vm.guides = g.copy(gridSize = it.roundToInt()) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Isometric", Modifier.weight(1f)); Switch(g.isometric, { vm.guides = g.copy(isometric = it) }) }
    }
    Header("Perspective")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(0 to "Off", 1 to "1-point", 2 to "2-point", 3 to "3-point").forEach { (n, l) -> FilterChip(g.perspective == n, { vm.setPerspective(n) }, { Text(l) }) } }
    if (g.perspective > 0) Row(verticalAlignment = Alignment.CenterVertically) { Text("Move vanishing points", Modifier.weight(1f)); Switch(g.editing, { vm.guides = g.copy(editing = it) }) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("Drawing assist"); Text("Snaps strokes to guide lines", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(g.assist, { vm.guides = g.copy(assist = it) })
    }
    Header("Symmetry")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { SymmetryKind.entries.forEach { k -> FilterChip(vm.symmetry.kind == k, { vm.setSymmetryKind(k) }, { Text(k.name) }) } }
    if (vm.symmetry.kind == SymmetryKind.Radial || vm.symmetry.kind == SymmetryKind.Kaleidoscope)
        Labeled("Segments", vm.symmetry.count.toFloat(), 2f..32f) { vm.symmetry = vm.symmetry.copy(count = it.roundToInt()); vm.invalidateView() }
}

@Composable
private fun AnimateTab(vm: StudioViewModel, share: (java.io.File, String) -> Unit) {
    val d = vm.doc ?: return
    val a = d.animation
    var rev by remember { mutableIntStateOf(0) }
    rev
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("Animation mode"); Text("Each layer becomes a frame, with onion skins", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(a.enabled, { vm.setAnimation(a.copy(enabled = it)); rev++ })
    }
    if (a.enabled) {
        Labeled("Frames / sec", a.fps.toFloat(), 1f..30f) { vm.setAnimation(a.copy(fps = it.roundToInt())); rev++ }
        Labeled("Onion skins", a.onionSkins.toFloat(), 0f..5f) { vm.setAnimation(a.copy(onionSkins = it.roundToInt())); rev++ }
        Labeled("Onion opacity", a.onionOpacity, 0.05f..0.9f, { "${(it * 100).roundToInt()}%" }) { vm.setAnimation(a.copy(onionOpacity = it)); rev++ }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Loop", Modifier.weight(1f)); Switch(a.loop, { vm.setAnimation(a.copy(loop = it)); rev++ }) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.silpa.core.design.SilpaButton(text = if (vm.playing) "Stop" else "Play", onClick = vm::togglePlay, palette = SilpaSkeuo.Cobalt)
            com.silpa.core.design.SilpaButton(text = "Add frame", onClick = vm::addLayer, palette = SilpaSkeuo.Graphite)
        }
        Header("Export animation")
        Row { TextButton({ vm.exportForShare(StudioViewModel.Format.GIF) { share(it, "image/gif") } }) { Text("Share GIF") }; TextButton({ vm.exportForShare(StudioViewModel.Format.MP4) { share(it, "video/mp4") } }) { Text("Share MP4") } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CanvasTab(vm: StudioViewModel, canvasView: CanvasView?) {
    val d = vm.doc ?: return
    Text("${d.width} × ${d.height} px · ${d.layers.size} layers", style = MaterialTheme.typography.bodyMedium)
    Header("View")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton({ canvasView?.fit() }) { Text("Fit to screen") }
        TextButton({ canvasView?.resetRotation() }) { Text("Reset rotation") }
        TextButton({ canvasView?.flipView() }) { Text("Flip view") }
    }
    TextButton({ vm.fullscreen = true; vm.panel = Panel.None }) { Text("Full screen (or tap with four fingers)") }
    Header("Background")
    Row(verticalAlignment = Alignment.CenterVertically) { Text("Transparent background", Modifier.weight(1f)); Switch(!d.paperVisible, { vm.setPaper(null, !it) }) }
    TextButton({ vm.setPaper(vm.color, true) }) { Text("Set paper to current color") }
    Header("Color-blindness preview")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Off", "Protanopia", "Deuteranopia", "Tritanopia", "Grayscale").forEachIndexed { i, n -> FilterChip(vm.colorBlind == i, { vm.colorBlind = i }, { Text(n) }) }
    }
}

@Composable
private fun ShareTab(vm: StudioViewModel, share: (java.io.File, String) -> Unit) {
    var pending by remember { mutableStateOf<StudioViewModel.Format?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri -> val f = pending; if (uri != null && f != null) vm.export(f, uri) }
    StudioViewModel.Format.entries.forEach { f ->
        val needsAnim = f == StudioViewModel.Format.GIF || f == StudioViewModel.Format.MP4
        if (needsAnim && vm.doc?.animation?.enabled != true) return@forEach
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { pending = f; save.launch(vm.exportName(f)) }.padding(vertical = 12.dp, horizontal = 8.dp)) {
                Text(f.label, style = MaterialTheme.typography.bodyLarge)
                Text("Save .${f.ext} to your device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton({ vm.exportForShare(f) { share(it, f.mime) } }) { Icon(TablerIcons.Share, "Share ${f.label}") }
        }
    }
}

@Composable
private fun PrefsTab(vm: StudioViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("Stylus-only drawing"); Text("Fingers pan and zoom once a pen is detected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(vm.stylusOnly, vm::setStylusOnlyPref)
    }
    Row(verticalAlignment = Alignment.CenterVertically) { Text("Left-handed layout", Modifier.weight(1f)); Switch(vm.leftHanded, vm::setLeftHandedPref) }
    Header("Gestures")
    Text("Two-finger tap: undo · Three-finger tap: redo · Four-finger tap: full screen · Pinch: zoom & rotate · Touch and hold: eyedropper · Hold at the end of a stroke: snap to a clean shape",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Header("History (${vm.history.size} steps)")
    vm.history.entries.asReversed().take(30).forEachIndexed { i, label ->
        ActionRow(label, if (i == 0) "Latest" else null) { vm.undoTo(vm.history.size - i) }
    }
}

// ---------------- Image Bank inside the studio ----------------

@Composable
fun StudioBankPanel(vm: StudioViewModel, close: () -> Unit) {
    val ctx = LocalContext.current
    val bank = remember { Bank.get(ctx) }
    LaunchedEffect(Unit) { bank.load() }
    val items by bank.items.collectAsState()
    var cat by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<BankItem?>(null) }
    val scope = rememberCoroutineScope()
    PanelSurface("Image Bank", close) {
        Column(Modifier.fillMaxSize()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(cat == null, { cat = null }, { Text("All") }) }
                itemsIndexed(BankCategories.filter { c -> items.any { it.category == c } }) { _, c -> FilterChip(cat == c, { cat = c }, { Text(c) }) }
            }
            val shown = items.filter { cat == null || it.category == cat }
            if (shown.isEmpty()) Text("Nothing here yet. Add images from the Image Bank tab on the home screen.", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyVerticalGrid(GridCells.Adaptive(96.dp), contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { item ->
                    val img by produceState<ImageBitmap?>(null, item.id) { value = withContext(Dispatchers.IO) { decodeThumb(item.file.path, 256)?.asImageBitmap() } }
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.White).clickable { menu = item }) {
                        img?.let { Image(it, item.category, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                }
            }
        }
    }
    menu?.let { item ->
        AlertDialog(onDismissRequest = { menu = null }, title = { Text(item.category) }, text = {
            Column {
                Text(item.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { item.palette.forEach { c -> Swatch(c, 30.dp) { vm.pickColor(c) } } }
            }
        }, confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton({ scope.launch { BitmapFactory.decodeFile(item.file.path)?.let { vm.addLayerWith(item.category.trimEnd('s'), fitted = it, thenTransform = true) } }; menu = null; close() }) { Text("Add as layer") }
                TextButton({ vm.referenceImage = BitmapFactory.decodeFile(item.file.path); menu = null; close() }) { Text("Open as reference") }
                TextButton({ BitmapFactory.decodeFile(item.file.path)?.let { vm.brushFromImage(it, "${item.category} stamp") }; menu = null }) { Text("Make stamp brush") }
                TextButton({ menu = null }) { Text("Close") }
            }
        })
    }
}

fun decodeThumb(path: String, max: Int): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(path, o)
    var s = 1; while (maxOf(o.outWidth, o.outHeight) / s > max * 2) s *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
}

/** Floating, draggable, resizable reference. Tap inside to pick a color from it. */
@Composable
fun ReferenceWindow(vm: StudioViewModel, bmp: Bitmap) {
    val density = LocalDensity.current
    var offset by remember { mutableStateOf(IntOffset(40, 220)) }
    var width by remember { mutableFloatStateOf(220f) }
    val img = remember(bmp) { bmp.asImageBitmap() }
    val ratio = bmp.width.toFloat() / bmp.height
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Surface(
            modifier = Modifier.offset { offset }.width(width.dp).shadow(16.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Column {
                Row(Modifier.fillMaxWidth().pointerDrag { dx, dy -> offset = IntOffset(offset.x + dx.roundToInt(), offset.y + dy.roundToInt()) }.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Reference", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    IconButton({ vm.referenceImage = null }, Modifier.size(40.dp)) { Icon(TablerIcons.X, "Close reference") }
                }
                Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White)) {
                    Image(img, "Reference image", Modifier.fillMaxSize().pointerTapPick { fx, fy ->
                        val x = (fx * bmp.width).toInt().coerceIn(0, bmp.width - 1); val y = (fy * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                        vm.pickColor(bmp.getPixel(x, y))
                    }, contentScale = ContentScale.Fit)
                    Box(Modifier.align(Alignment.BottomEnd).size(28.dp).clip(RoundedCornerShape(topStart = 12.dp)).background(Color(0xAA1F3FD1))
                        .pointerDrag { dx, _ -> width = (width + dx / density.density).coerceIn(140f, 600f) })
                }
            }
        }
    }
}

private fun Modifier.pointerDrag(f: (Float, Float) -> Unit) = this.then(Modifier.pointerInputDrag(f))
private fun Modifier.pointerInputDrag(f: (Float, Float) -> Unit): Modifier = Modifier.pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); f(d.x, d.y) } }
private fun Modifier.pointerTapPick(f: (Float, Float) -> Unit): Modifier = Modifier.pointerInput(Unit) { detectTapGestures { o -> f(o.x / size.width, o.y / size.height) } }

// ---------------- Animation timeline ----------------

@Composable
fun Timeline(vm: StudioViewModel) {
    val d = vm.doc ?: return
    val rev = vm.revision
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f), shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(vm::togglePlay) { Icon(if (vm.playing) TablerIcons.PlayerPause else TablerIcons.PlayerPlay, if (vm.playing) "Pause" else "Play") }
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(d.layers, key = { _, l -> l.id }) { i, l ->
                    val thumb = remember(rev, l.id) { vm.layerThumb(l, 96).asImageBitmap() }
                    val sel = i == d.activeIndex
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(56.dp, 44.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                            .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color(0x33000000), RoundedCornerShape(8.dp))
                            .clickable { vm.selectLayer(i) }) { Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                        Text("${i + 1}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            IconButton(vm::addLayer) { Icon(TablerIcons.Plus, "Add frame") }
        }
    }
}
