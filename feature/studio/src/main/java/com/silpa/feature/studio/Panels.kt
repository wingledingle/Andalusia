package com.silpa.feature.studio

import android.graphics.Bitmap
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.silpa.core.design.SilpaSkeuo
import com.silpa.engine.BrushLibrary
import com.silpa.engine.BrushMode
import com.silpa.engine.BrushSettings
import com.silpa.engine.Compositor
import com.silpa.engine.Document
import com.silpa.engine.Selection
import com.silpa.engine.SilpaBlend
import com.silpa.engine.StrokeEngine
import com.silpa.engine.StrokePoint
import com.silpa.engine.Symmetry
import com.silpa.engine.TipShape
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowsDownUp
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronUp
import compose.icons.tablericons.Copy
import compose.icons.tablericons.Edit
import compose.icons.tablericons.Eye
import compose.icons.tablericons.EyeOff
import compose.icons.tablericons.FileImport
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Photo
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Trash
import io.github.skeuocompose.SkeuoKnob
import io.github.skeuocompose.SkeuoPalettes
import io.github.skeuocompose.SkeuoSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------- Brush previews ----------------

private val previewCache = LruCache<String, Bitmap>(160)

/** Renders a sample S-stroke with a pressure ramp through the real engine. */
fun renderBrushPreview(b: BrushSettings, w: Int = 420, h: Int = 96, color: Int = 0xFFE8E9EE.toInt()): Bitmap {
    val key = "${b.hashCode()}:$w:$h:$color"
    previewCache.get(key)?.let { return it }
    val doc = Document(w, h); val l = doc.newLayer(); doc.layers += l
    val sel = Selection(w, h); val comp = Compositor(doc, sel); val eng = StrokeEngine(doc, sel, comp)
    doc.paperVisible = false
    val pb = b.copy(size = b.size.coerceIn(2f, h * 0.42f), smoothing = 0f, scatter = b.scatter.coerceAtMost(1f), mode = if (b.mode == BrushMode.Smudge) BrushMode.Paint else b.mode)
    if (pb.mode == BrushMode.Erase) {
        val base = pb.copy(mode = BrushMode.Paint, tip = TipShape.Round, hardness = 1f, size = h * 0.5f, sizePressure = false, opacity = 0.35f, grain = 0f)
        eng.begin(l, base, color, Symmetry(), 0f, StrokePoint(10f, h / 2f, 1f, 0f, 0)); (0..60).forEach { eng.add(StrokePoint(10f + it * (w - 20f) / 60, h / 2f, 1f, 0f, it * 8L)) }; eng.end()
    }
    val n = 80
    eng.begin(l, pb, color, Symmetry(), 0f, StrokePoint(16f, h / 2f, 0.1f, 0f, 0))
    for (i in 1..n) {
        val t = i / n.toFloat()
        val x = 16f + t * (w - 32f); val y = h / 2f + sin(t * 2 * PI).toFloat() * h * 0.22f
        val p = if (t < 0.5f) 0.1f + t * 1.8f else 1f - (t - 0.5f) * 1.6f
        eng.add(StrokePoint(x, y, p.coerceIn(0.05f, 1f), 0f, i * 12L))
    }
    eng.end()
    val out = comp.renderFull(paper = false)
    previewCache.put(key, out)
    return out
}

@Composable
private fun BrushPreview(b: BrushSettings, modifier: Modifier = Modifier) {
    val img by produceState<ImageBitmap?>(null, b) { value = withContext(Dispatchers.Default) { runCatching { renderBrushPreview(b).asImageBitmap() }.getOrNull() } }
    Box(modifier.height(56.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}

@Composable
fun BrushesPanel(vm: StudioViewModel, close: () -> Unit) {
    val sets = (if (vm.customBrushes.isNotEmpty()) listOf("My Brushes") else emptyList()) + BrushLibrary.sets
    var set by remember { mutableStateOf(vm.brush.set.takeIf { it in sets } ?: sets.first()) }
    val abr = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importAbr) }
    PanelSurface("Brush library", close, actions = {
        IconButton({ abr.launch(arrayOf("*/*")) }) { Icon(TablerIcons.FileImport, "Import Photoshop brushes (.abr)") }
        IconButton({ vm.panel = Panel.BrushStudio }) { Icon(TablerIcons.Edit, "Edit brush") }
    }) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.width(128.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(sets) { s ->
                    val sel = s == set
                    Text(
                        s, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge, color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).clickable { set = s }.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
            val list = if (set == "My Brushes") vm.customBrushes.toList() else BrushLibrary.all.filter { it.set == set }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list, key = { it.id }) { b ->
                    val active = vm.brush.id == b.id
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(if (active) 2.dp else 0.dp, if (active) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable { vm.useBrushForCurrentTool(b) }.padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(b.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            if (b.custom) IconButton({ vm.deleteCustomBrush(b) }, Modifier.size(32.dp)) { Icon(TablerIcons.Trash, "Delete brush", Modifier.size(18.dp)) }
                        }
                        BrushPreview(b)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: (Float) -> String = { "${(it * 100).roundToInt()}%" }, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(118.dp))
        Slider(value.coerceIn(range.start, range.endInclusive), onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(format(value), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(52.dp), maxLines = 1)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        SkeuoSwitch(checked = checked, onCheckedChange = onChange, trackPalette = SkeuoPalettes.RetroBlack, knobPalette = if (checked) SilpaSkeuo.Cobalt else SkeuoPalettes.Metallic, trackWidth = 64.dp, trackHeight = 32.dp, knobWidth = 34.dp, knobHeight = 28.dp)
    }
}

@Composable
private fun Section(title: String) = Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrushStudioPanel(vm: StudioViewModel, close: () -> Unit) {
    val b = vm.brush
    var name by remember(b.id) { mutableStateOf(b.name) }
    val up: ((BrushSettings) -> BrushSettings) -> Unit = { vm.updateBrush(it) }
    PanelSurface("Brush Studio · ${b.name}", { vm.persistBrushes(); close() }, actions = {
        TextButton({ BrushLibrary.byId(b.id)?.let { def -> vm.updateBrush { def } } }) { Text("Reset") }
    }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF1A1B1E), modifier = Modifier.fillMaxWidth()) { BrushPreview(b, Modifier.padding(8.dp)) }
            Section("Shape")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TipShape.entries.filter { it != TipShape.Image || b.tip == TipShape.Image }.forEach { t -> FilterChip(b.tip == t, { up { it.copy(tip = t) } }, { Text(t.name) }) }
            }
            LabeledSlider("Hardness", b.hardness, 0f..1f) { v -> up { it.copy(hardness = v) } }
            LabeledSlider("Roundness", b.roundness, 0.05f..1f) { v -> up { it.copy(roundness = v) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tip angle ${b.angle.roundToInt()}°", style = MaterialTheme.typography.bodyMedium)
                    SwitchRow("Follow stroke", b.followDirection) { v -> up { it.copy(followDirection = v) } }
                }
                SkeuoKnob(value = b.angle, onValueChange = { v -> up { it.copy(angle = v) } }, valueRange = 0f..180f, knobSize = 72.dp, palette = SkeuoPalettes.Steel, accentColor = Color(0xFF4CB4E6))
            }
            Section("Stroke")
            LabeledSlider("Size", sizeToSlider(b.size), 0f..1f, { "${sliderToSize(it).roundToInt()}px" }) { v -> up { it.copy(size = sliderToSize(v)) } }
            LabeledSlider("Spacing", b.spacing, 0.02f..3f) { v -> up { it.copy(spacing = v) } }
            LabeledSlider("StreamLine", b.smoothing, 0f..0.95f) { v -> up { it.copy(smoothing = v) } }
            LabeledSlider("Count", b.count.toFloat(), 1f..8f, { "${it.roundToInt()}" }) { v -> up { it.copy(count = v.roundToInt()) } }
            LabeledSlider("Scatter", b.scatter, 0f..3f) { v -> up { it.copy(scatter = v) } }
            Section("Dynamics")
            SwitchRow("Pressure → size", b.sizePressure) { v -> up { it.copy(sizePressure = v) } }
            LabeledSlider("Minimum size", b.minSize, 0f..1f) { v -> up { it.copy(minSize = v) } }
            SwitchRow("Pressure → opacity", b.opacityPressure) { v -> up { it.copy(opacityPressure = v) } }
            LabeledSlider("Pressure curve", b.pressureGamma, 0.3f..3f, { "%.1f".format(it) }) { v -> up { it.copy(pressureGamma = v) } }
            LabeledSlider("Tilt → size", b.tiltSize, 0f..1f) { v -> up { it.copy(tiltSize = v) } }
            LabeledSlider("Speed thinning", b.velocityThin, 0f..1f) { v -> up { it.copy(velocityThin = v) } }
            Section("Jitter")
            LabeledSlider("Size", b.sizeJitter, 0f..1f) { v -> up { it.copy(sizeJitter = v) } }
            LabeledSlider("Angle", b.angleJitter, 0f..1f) { v -> up { it.copy(angleJitter = v) } }
            LabeledSlider("Opacity", b.opacityJitter, 0f..1f) { v -> up { it.copy(opacityJitter = v) } }
            LabeledSlider("Hue", b.hueJitter, 0f..1f) { v -> up { it.copy(hueJitter = v) } }
            Section("Texture & paint")
            LabeledSlider("Grain", b.grain, 0f..1f) { v -> up { it.copy(grain = v) } }
            LabeledSlider("Grain scale", b.grainScale, 0.25f..4f, { "%.1fx".format(it) }) { v -> up { it.copy(grainScale = v) } }
            LabeledSlider("Flow", b.flow, 0.01f..1f) { v -> up { it.copy(flow = v) } }
            LabeledSlider("Wet mix", b.wetMix, 0f..1f) { v -> up { it.copy(wetMix = v) } }
            if (b.mode == BrushMode.Smudge) LabeledSlider("Smudge strength", b.smudgeStrength, 0f..1f) { v -> up { it.copy(smudgeStrength = v) } }
            SwitchRow("Glow (additive)", b.glow) { v -> up { it.copy(glow = v) } }
            Section("Save")
            OutlinedTextField(name, { name = it }, label = { Text("Brush name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            io.github.skeuocompose.SkeuoButton(text = "Save as new brush", onClick = { vm.saveCustomBrush(b.copy(name = name)) }, palette = SilpaSkeuo.Cobalt, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------- Color ----------------

private val palettes = linkedMapOf(
    "Silpa" to listOf(0xFF1F3FD1, 0xFF4CB4E6, 0xFFF28B6B, 0xFFFFE0CC, 0xFF6B3E2E, 0xFF14121A, 0xFFFFFFFF, 0xFF8A5CF6, 0xFFE34D7A, 0xFF35C28A),
    "Skin tones" to listOf(0xFFFFE3D3, 0xFFF9CDB0, 0xFFF1B48E, 0xFFE09A6E, 0xFFC97E51, 0xFFA8643A, 0xFF8A4E2C, 0xFF6B3A1F, 0xFF4E2914, 0xFF2F180B),
    "Manga grays" to listOf(0xFFFFFFFF, 0xFFE6E6E6, 0xFFCCCCCC, 0xFFB3B3B3, 0xFF999999, 0xFF808080, 0xFF666666, 0xFF4D4D4D, 0xFF262626, 0xFF000000),
    "Anime hair" to listOf(0xFF1E2A78, 0xFF3D5AFE, 0xFF8E24AA, 0xFFFF80AB, 0xFFFFD54F, 0xFFFF7043, 0xFFB0BEC5, 0xFF4E342E, 0xFF212121, 0xFF00BFA5),
    "Pastel" to listOf(0xFFFFD1DC, 0xFFFFE5B4, 0xFFFFFACD, 0xFFD5F5E3, 0xFFC1E1FF, 0xFFE0C3FC, 0xFFFFC8A2, 0xFFB5EAD7, 0xFFE2F0CB, 0xFFC7CEEA),
    "Sunset" to listOf(0xFF2B1B3D, 0xFF5B2A6E, 0xFF9C3D7A, 0xFFD9566B, 0xFFF48C5A, 0xFFFDBF6F, 0xFFFFE8A3, 0xFF3A6EA5, 0xFF1E3D59, 0xFF0B1D2E),
).mapValues { e -> e.value.map { it.toInt() } }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPanel(vm: StudioViewModel, close: () -> Unit) {
    val hsv = remember(vm.color) { FloatArray(3).also { android.graphics.Color.colorToHSV(vm.color, it) } }
    var hue by remember(vm.color) { mutableStateOf(hsv[0]) }
    var sat by remember(vm.color) { mutableStateOf(hsv[1]) }
    var value by remember(vm.color) { mutableStateOf(hsv[2]) }
    fun commit(h: Float = hue, s: Float = sat, v: Float = value, remember: Boolean = false) { vm.pickColor(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)), remember) }
    var hex by remember(vm.color) { mutableStateOf("%06X".format(vm.color and 0xFFFFFF)) }

    PanelSurface("Color", close) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(vm.color)).border(2.dp, Color.White, CircleShape))
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(hex, { t -> hex = t.uppercase().filter { it in "0123456789ABCDEF" }.take(6); if (hex.length == 6) vm.pickColor(0xFF000000.toInt() or hex.toInt(16)) },
                    label = { Text("Hex") }, singleLine = true, modifier = Modifier.width(140.dp))
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            // Saturation/value square
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(16.dp))
                    .pointerInput(hue) {
                        detectDragGestures(onDragEnd = { commit(remember = true) }) { c, _ -> sat = (c.position.x / size.width).coerceIn(0f, 1f); value = 1f - (c.position.y / size.height).coerceIn(0f, 1f); commit() }
                    }
                    .pointerInput(hue) { detectTapGestures { o -> sat = (o.x / size.width).coerceIn(0f, 1f); value = 1f - (o.y / size.height).coerceIn(0f, 1f); commit(remember = true) } },
            ) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                drawCircle(Color.White, 12.dp.toPx(), Offset(sat * size.width, (1 - value) * size.height), style = Stroke(3.dp.toPx()))
            }
            Spacer(Modifier.height(10.dp))
            Canvas(
                Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))
                    .pointerInput(Unit) { detectDragGestures(onDragEnd = { commit(remember = true) }) { c, _ -> hue = (c.position.x / size.width * 360f).coerceIn(0f, 359.9f); commit() } }
                    .pointerInput(Unit) { detectTapGestures { o -> hue = (o.x / size.width * 360f).coerceIn(0f, 359.9f); commit(remember = true) } },
            ) {
                drawRect(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }))
                drawCircle(Color.White, 12.dp.toPx(), Offset(hue / 360f * size.width, size.height / 2), style = Stroke(3.dp.toPx()))
            }
            Section("Harmony")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(180f, 30f, -30f, 120f, 240f, 150f, 210f).forEach { d ->
                    val c = android.graphics.Color.HSVToColor(floatArrayOf((hue + d + 360f) % 360f, sat, value))
                    Swatch(c) { vm.pickColor(c) }
                }
            }
            if (vm.recentColors.isNotEmpty()) {
                Section("Recent")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { vm.recentColors.forEach { c -> Swatch(c) { vm.pickColor(c) } } }
            }
            palettes.forEach { (name, cols) ->
                Section(name)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { cols.forEach { c -> Swatch(c) { vm.pickColor(c) } } }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun Swatch(c: Int, size: androidx.compose.ui.unit.Dp = 36.dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(c)).border(1.dp, Color(0x55FFFFFF), CircleShape).clickable(onClick = onClick))
}

// ---------------- Layers ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayersPanel(vm: StudioViewModel, close: () -> Unit) {
    val doc = vm.doc ?: return
    val anim = doc.animation.enabled
    var expanded by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Int?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::importImageLayer) }
    val rev = vm.revision
    PanelSurface(if (anim) "Frames" else "Layers", close, actions = {
        IconButton({ pick.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(TablerIcons.Photo, "Insert photo as layer") }
        IconButton(vm::addLayer) { Icon(TablerIcons.Plus, if (anim) "Add frame" else "Add layer") }
    }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val order = doc.layers.indices.reversed().toList()
            itemsIndexed(order, key = { _, i -> doc.layers[i].id }) { _, i ->
                val l = doc.layers[i]
                val active = i == doc.activeIndex
                val thumb = remember(rev, l.id) { vm.layerThumb(l, 120).asImageBitmap() }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { if (active) expanded = if (expanded == l.id) null else l.id else vm.selectLayer(i) },
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)) {
                            Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (anim) "Frame ${i + 1} · ${l.name}" else l.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                            Text(buildString {
                                append(l.blend.label); append(" · "); append((l.opacity * 100).roundToInt()); append('%')
                                if (l.clipping) append(" · clipped"); if (l.alphaLock) append(" · α-lock"); if (l.reference) append(" · reference"); if (l.locked) append(" · locked")
                            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (l.locked) Icon(TablerIcons.Lock, "Locked", Modifier.size(18.dp))
                        IconButton({ vm.setLayerProps(i, l.props.copy(visible = !l.visible)) }) { Icon(if (l.visible) TablerIcons.Eye else TablerIcons.EyeOff, if (l.visible) "Hide layer" else "Show layer") }
                    }
                    if (expanded == l.id && active) {
                        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp)) {
                            LabeledSlider("Opacity", l.opacity, 0f..1f) { v -> l.opacity = v; vm.compositor.invalidateAll(); vm.invalidateView() }
                            Text("Blend mode", style = MaterialTheme.typography.labelMedium)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SilpaBlend.entries.forEach { m -> FilterChip(l.blend == m, { vm.setLayerProps(i, l.props.copy(blend = m)) }, { Text(m.label) }) }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(l.alphaLock, { vm.setLayerProps(i, l.props.copy(alphaLock = !l.alphaLock)) }, { Text("Alpha lock") })
                                FilterChip(l.clipping, { vm.setLayerProps(i, l.props.copy(clipping = !l.clipping)) }, { Text("Clipping mask") })
                                FilterChip(l.reference, { vm.setLayerProps(i, l.props.copy(reference = !l.reference)) }, { Text("Reference") })
                                FilterChip(l.locked, { vm.setLayerProps(i, l.props.copy(locked = !l.locked)) }, { Text("Lock") })
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TextButton({ renaming = i }) { Text("Rename") }
                                TextButton({ vm.duplicateLayer(i) }) { Text("Duplicate") }
                                TextButton({ vm.mergeDown(i) }, enabled = i > 0) { Text("Merge down") }
                                TextButton({ vm.moveLayer(i, i + 1) }, enabled = i < doc.layers.lastIndex) { Text("Move up") }
                                TextButton({ vm.moveLayer(i, i - 1) }, enabled = i > 0) { Text("Move down") }
                                TextButton({ vm.selectLayerContents(i); vm.tool = Tool.Select }) { Text("Select") }
                                TextButton({ vm.fillLayer(i) }) { Text("Fill") }
                                TextButton({ vm.clearLayer(i) }) { Text("Clear") }
                                TextButton({ vm.deleteLayer(i) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(doc.paperColor)).clickable { vm.setPaper(vm.color, true) })
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Paper", style = MaterialTheme.typography.labelLarge)
                        Text("Tap swatch to use current color", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton({ vm.setPaper(null, !doc.paperVisible) }) { Icon(if (doc.paperVisible) TablerIcons.Eye else TablerIcons.EyeOff, "Toggle paper / transparent background") }
                }
            }
            item { TextButton(vm::flatten, Modifier.fillMaxWidth()) { Text("Flatten all layers") } }
        }
    }
    renaming?.let { i ->
        var name by remember { mutableStateOf(doc.layers[i].name) }
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Rename layer") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton({ vm.setLayerProps(i, doc.layers[i].props.copy(name = name.ifBlank { "Layer" })); renaming = null }) { Text("Rename") } },
            dismissButton = { TextButton({ renaming = null }) { Text("Cancel") } })
    }
}
