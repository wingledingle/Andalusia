package com.silpa.feature.studio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.text.Layout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silpa.engine.Abr
import com.silpa.engine.AnimationSettings
import com.silpa.engine.BrushLibrary
import com.silpa.engine.BrushMode
import com.silpa.engine.BrushSettings
import com.silpa.engine.Compositor
import com.silpa.engine.Document
import com.silpa.engine.Edit
import com.silpa.engine.Export
import com.silpa.engine.FilterKind
import com.silpa.engine.Filters
import com.silpa.engine.GifEncoder
import com.silpa.engine.History
import com.silpa.engine.Layer
import com.silpa.engine.LayerProps
import com.silpa.engine.Mp4Encoder
import com.silpa.engine.Ops
import com.silpa.engine.ProjectStore
import com.silpa.engine.Psd
import com.silpa.engine.SelectOp
import com.silpa.engine.Selection
import com.silpa.engine.StrokeEngine
import com.silpa.engine.StrokePoint
import com.silpa.engine.StructureEdit
import com.silpa.engine.Symmetry
import com.silpa.engine.SymmetryKind
import com.silpa.engine.TipShape
import com.silpa.engine.snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class Tool(val label: String) { Brush("Brush"), Smudge("Smudge"), Eraser("Eraser"), Select("Select"), Transform("Transform"), Fill("Fill"), Eyedropper("Eyedropper") }
enum class SelectShape(val label: String) { Lasso("Lasso"), Rect("Rectangle"), Ellipse("Ellipse"), Wand("Magic wand") }
enum class FillMode(val label: String) { Flood("Bucket"), Linear("Linear gradient"), Radial("Radial gradient") }
enum class TransformMode(val label: String) { Uniform("Uniform"), Free("Free"), Distort("Distort") }
enum class Panel { None, Brushes, BrushStudio, Color, Layers, Actions, Bank }

data class Guides(
    val grid: Boolean = false,
    val gridSize: Int = 100,
    val isometric: Boolean = false,
    val perspective: Int = 0,
    val vps: List<PointF> = emptyList(),
    val assist: Boolean = false,
    val editing: Boolean = false,
)

class SelectionEdit(private val sel: Selection, private val before: Triple<Boolean, Map<Long, Bitmap>, Path?>, private val after: Triple<Boolean, Map<Long, Bitmap>, Path?>) : Edit {
    override val label = "Selection"
    override val bytes = (before.second.size + after.second.size) * 65_536L
    override fun undo() = sel.restore(before)
    override fun redo() = sel.restore(after)
}

/**
 * Owns the open document and every studio operation. The engine is single-threaded (main thread);
 * heavy pixel work runs on Dispatchers.Default and is applied back on main.
 */
class StudioViewModel(app: Application) : AndroidViewModel(app) {
    val store = ProjectStore(File(app.filesDir, "projects"))
    private val brushFile = File(app.filesDir, "studio_brushes.bin")
    private val customFile = File(app.filesDir, "custom_brushes.bin")
    private val prefs = app.getSharedPreferences("studio", 0)

    var projectId: String? = null; private set
    var doc by mutableStateOf<Document?>(null); private set
    lateinit var selection: Selection; private set
    lateinit var compositor: Compositor; private set
    private lateinit var strokes: StrokeEngine
    val history = History(Runtime.getRuntime().maxMemory() / 4)

    var revision by mutableIntStateOf(0); private set
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    var busy by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)

    var tool by mutableStateOf(Tool.Brush)
    val toolBrush = mutableStateMapOf(
        Tool.Brush to BrushLibrary.byId("Inking/Studio Pen")!!,
        Tool.Smudge to BrushLibrary.byId("Smudge/Finger")!!,
        Tool.Eraser to BrushLibrary.byId("Erasers/Hard Eraser")!!,
    )
    val customBrushes = mutableStateListOf<BrushSettings>()
    var color by mutableIntStateOf(prefs.getInt("color", 0xFF1F3FD1.toInt()))
    val recentColors = mutableStateListOf<Int>()
    var symmetry by mutableStateOf(Symmetry())
    var guides by mutableStateOf(Guides())
    var selectShape by mutableStateOf(SelectShape.Lasso)
    var selectOp by mutableStateOf(SelectOp.Replace)
    var fillMode by mutableStateOf(FillMode.Flood)
    var fillTolerance by mutableFloatStateOf(24f)
    var fillAllLayers by mutableStateOf(true)
    var transformMode by mutableStateOf(TransformMode.Uniform)
    var floating: Ops.Floating? by mutableStateOf(null); private set
    var corners by mutableStateOf(FloatArray(8)); private set
    var stylusOnly by mutableStateOf(prefs.getBoolean("stylusOnly", false))
    var stylusSeen = false
    var colorBlind by mutableIntStateOf(0)
    var fullscreen by mutableStateOf(false)
    var panel by mutableStateOf(Panel.None)
    var selectionVersion by mutableIntStateOf(0); private set
    var referenceImage by mutableStateOf<Bitmap?>(null)
    var leftHanded by mutableStateOf(prefs.getBoolean("leftHanded", false))

    // Animation playback
    var playing by mutableStateOf(false); private set
    var playFrame: Bitmap? by mutableStateOf(null); private set
    private var playJob: Job? = null

    var invalidateView: () -> Unit = {}
    var refitView: () -> Unit = {}

    private val dirty = HashMap<String, MutableSet<Long>>()
    private var saveJob: Job? = null
    private val ioOne = Dispatchers.IO.limitedParallelism(1)
    private var timelapseCount = 0
    private var lastTimelapse = 0L

    val brush: BrushSettings get() = toolBrush[if (tool == Tool.Smudge || tool == Tool.Eraser) tool else Tool.Brush]!!

    init {
        history.onChanged = { canUndo = history.canUndo; canRedo = history.canRedo }
        runCatching { ObjectInputStream(brushFile.inputStream()).use { @Suppress("UNCHECKED_CAST") (it.readObject() as Map<Tool, BrushSettings>).forEach { (k, v) -> toolBrush[k] = v } } }
        runCatching { ObjectInputStream(customFile.inputStream()).use { @Suppress("UNCHECKED_CAST") customBrushes.addAll(it.readObject() as List<BrushSettings>) } }
        prefs.getString("recent", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.let { recentColors.addAll(it) }
    }

    // ---------------- Project lifecycle ----------------

    fun open(id: String) {
        if (projectId == id && doc != null) return
        projectId = id
        viewModelScope.launch {
            busy = "Opening…"
            val d = runCatching { withContext(Dispatchers.IO) { store.load(id) } }.getOrElse { busy = null; toast = "Couldn't open this artwork: ${it.message}"; return@launch }
            attach(d)
            timelapseCount = withContext(Dispatchers.IO) { store.timelapseDir(id).list()?.size ?: 0 }
            busy = null
        }
    }

    private fun attach(d: Document) {
        selection = Selection(d.width, d.height).also { s -> s.onChanged = { selectionVersion++; compositor.invalidateAll(); invalidateView() } }
        compositor = Compositor(d, selection)
        strokes = StrokeEngine(d, selection, compositor)
        d.onTileChanged = { l, k -> dirty.getOrPut(l.id) { HashSet() } += k; compositor.invalidate(k); scheduleSave() }
        d.onStructureChanged = { compositor.invalidateAll(); revision++; scheduleSave(); invalidateView() }
        history.clear()
        symmetry = Symmetry(cx = d.width / 2f, cy = d.height / 2f)
        guides = Guides(vps = defaultVps(d, 0))
        doc = d
        revision++
        refitView()
    }

    private fun defaultVps(d: Document, n: Int): List<PointF> = when (n) {
        1 -> listOf(PointF(d.width / 2f, d.height * 0.45f))
        2 -> listOf(PointF(-d.width * 0.3f, d.height * 0.45f), PointF(d.width * 1.3f, d.height * 0.45f))
        3 -> listOf(PointF(-d.width * 0.3f, d.height * 0.35f), PointF(d.width * 1.3f, d.height * 0.35f), PointF(d.width / 2f, d.height * 2.2f))
        else -> emptyList()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(1500); saveNow() }
    }

    fun saveNow() {
        val d = doc ?: return; val id = projectId ?: return
        if (strokes.isActive) return
        val job = store.prepareSave(d, id, dirty.mapValues { it.value.toSet() }, emptySet(), compositor.renderPreview(512))
        dirty.clear()
        viewModelScope.launch(ioOne) { runCatching { store.write(job) } }
    }

    override fun onCleared() { saveNow(); persistBrushes(); super.onCleared() }

    private fun commit(e: Edit?) {
        if (e == null) return
        history.push(e); revision++; invalidateView(); captureTimelapse()
    }

    private fun captureTimelapse() {
        val id = projectId ?: return
        val now = System.currentTimeMillis()
        if (now - lastTimelapse < 350) return
        lastTimelapse = now
        val frame = compositor.renderPreview(720)
        val n = timelapseCount++
        viewModelScope.launch(ioOne) {
            runCatching { FileOutputStream(File(store.timelapseDir(id), "%06d.jpg".format(n))).use { Export.image(frame, Bitmap.CompressFormat.JPEG, 82, it) } }
            frame.recycle()
        }
    }

    fun undo() { if (floating != null) { cancelTransform(); return }; if (history.undo()) { revision++; invalidateView() } }
    fun redo() { if (history.redo()) { revision++; invalidateView() } }
    fun undoTo(n: Int) { history.undoTo(n); revision++; invalidateView() }

    // ---------------- Brushes & color ----------------

    fun setBrush(b: BrushSettings) {
        val t = when (b.mode) { BrushMode.Smudge -> Tool.Smudge; BrushMode.Erase -> Tool.Eraser; else -> if (tool == Tool.Smudge || tool == Tool.Eraser) tool else Tool.Brush }
        val keep = toolBrush[t]
        toolBrush[t] = if (keep != null && keep.id == b.id) b else b
        if (tool !in listOf(Tool.Brush, Tool.Smudge, Tool.Eraser)) tool = t
        persistBrushes()
    }

    /** Apply a brush to the current tool (smudge/erase tools can use any brush's tip, like Procreate). */
    fun useBrushForCurrentTool(b: BrushSettings) {
        val t = if (tool == Tool.Smudge || tool == Tool.Eraser) tool else Tool.Brush
        toolBrush[t] = when (t) {
            Tool.Smudge -> b.copy(mode = BrushMode.Smudge)
            Tool.Eraser -> b.copy(mode = BrushMode.Erase)
            else -> if (b.mode == BrushMode.Paint) b else b.copy(mode = BrushMode.Paint)
        }
        if (tool !in listOf(Tool.Brush, Tool.Smudge, Tool.Eraser)) tool = Tool.Brush
        persistBrushes()
    }

    fun updateBrush(f: (BrushSettings) -> BrushSettings) {
        val t = if (tool == Tool.Smudge || tool == Tool.Eraser) tool else Tool.Brush
        toolBrush[t] = f(toolBrush[t]!!)
    }

    fun persistBrushes() {
        runCatching { ObjectOutputStream(brushFile.outputStream()).use { it.writeObject(HashMap(toolBrush)) } }
        runCatching { ObjectOutputStream(customFile.outputStream()).use { it.writeObject(ArrayList(customBrushes)) } }
    }

    fun saveCustomBrush(b: BrushSettings) {
        val c = b.copy(id = "Custom/${System.currentTimeMillis()}", set = "My Brushes", custom = true)
        customBrushes += c; useBrushForCurrentTool(c); persistBrushes(); toast = "Saved “${c.name}” to My Brushes"
    }

    fun deleteCustomBrush(b: BrushSettings) { customBrushes.remove(b); persistBrushes() }

    fun importAbr(uri: Uri) = viewModelScope.launch {
        busy = "Importing brushes…"
        val ctx = getApplication<Application>()
        val res = runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { Abr.read(it) } } }
        val tips = res.getOrNull().orEmpty()
        if (tips.isEmpty()) { busy = null; toast = "No readable brushes found in that file (only sampled .abr brushes are supported)"; return@launch }
        val dir = File(ctx.filesDir, "brush_tips").apply { mkdirs() }
        tips.forEachIndexed { i, (name, bmp) ->
            val f = File(dir, "abr_${System.currentTimeMillis()}_$i.png")
            withContext(Dispatchers.IO) { FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            customBrushes += BrushSettings(id = "Custom/abr/${f.name}", name = name, set = "My Brushes", tip = TipShape.Image, tipImagePath = f.path, size = 60f, spacing = 0.15f, custom = true)
        }
        persistBrushes(); busy = null; toast = "Imported ${tips.size} brushes into My Brushes"
    }

    /** Make a stamp brush from any image (e.g. from the Image Bank). */
    fun brushFromImage(bmp: Bitmap, name: String) {
        val dir = File(getApplication<Application>().filesDir, "brush_tips").apply { mkdirs() }
        val f = File(dir, "stamp_${System.currentTimeMillis()}.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val b = BrushSettings(id = "Custom/${f.name}", name = name, set = "My Brushes", tip = TipShape.Image, tipImagePath = f.path, size = 120f, spacing = 1.2f, sizePressure = false, custom = true)
        customBrushes += b; useBrushForCurrentTool(b); persistBrushes(); toast = "Created stamp brush “$name”"
    }

    fun pickColor(c: Int, remember: Boolean = true) {
        color = c or 0xFF000000.toInt()
        prefs.edit().putInt("color", color).apply()
        if (remember) rememberColor(color)
    }

    private fun rememberColor(c: Int) {
        recentColors.remove(c); recentColors.add(0, c); while (recentColors.size > 24) recentColors.removeAt(recentColors.lastIndex)
        prefs.edit().putString("recent", recentColors.joinToString(",")).apply()
    }

    fun setStylusOnlyPref(v: Boolean) { stylusOnly = v; prefs.edit().putBoolean("stylusOnly", v).apply() }
    fun setLeftHandedPref(v: Boolean) { leftHanded = v; prefs.edit().putBoolean("leftHanded", v).apply() }

    // ---------------- Painting ----------------

    private var strokeStart: PointF? = null
    private var lockedDir: PointF? = null

    /** Drawing assist: snap to perspective / grid directions once the stroke's direction is clear. */
    private fun constrain(p: StrokePoint): StrokePoint {
        val g = guides; val s = strokeStart ?: return p
        if (!g.assist || (g.perspective == 0 && !g.grid)) return p
        var dir = lockedDir
        if (dir == null) {
            val dx = p.x - s.x; val dy = p.y - s.y
            if (hypot(dx, dy) < 14f) return StrokePoint(s.x, s.y, p.pressure, p.tilt, p.time)
            val cands = mutableListOf<PointF>()
            g.vps.take(g.perspective).forEach { vp -> val vx = vp.x - s.x; val vy = vp.y - s.y; val l = hypot(vx, vy); if (l > 1) cands += PointF(vx / l, vy / l) }
            if (g.perspective in 1..2 || g.grid) { cands += PointF(1f, 0f); cands += PointF(0f, 1f) }
            if (g.grid && g.isometric) { cands += PointF(cos(Math.toRadians(30.0)).toFloat(), sin(Math.toRadians(30.0)).toFloat()); cands += PointF(cos(Math.toRadians(150.0)).toFloat(), sin(Math.toRadians(150.0)).toFloat()) }
            val l = hypot(dx, dy); val ux = dx / l; val uy = dy / l
            dir = cands.maxByOrNull { abs(it.x * ux + it.y * uy) } ?: return p
            lockedDir = dir
        }
        val t = (p.x - s.x) * dir.x + (p.y - s.y) * dir.y
        return StrokePoint(s.x + dir.x * t, s.y + dir.y * t, p.pressure, p.tilt, p.time)
    }

    fun strokeBegin(p: StrokePoint, eraserTip: Boolean): Boolean {
        val d = doc ?: return false
        val l = d.active
        if (l.locked) { toast = "“${l.name}” is locked"; return false }
        if (!l.visible) { toast = "“${l.name}” is hidden — show it to draw"; return false }
        val b = when { eraserTip -> toolBrush[Tool.Eraser]!!; else -> brush }
        strokeStart = PointF(p.x, p.y); lockedDir = null
        strokes.begin(l, b, color, symmetry, b.smoothing, p)
        invalidateView(); return true
    }

    fun strokeMove(p: StrokePoint) { if (strokes.isActive) { strokes.add(constrain(p)); invalidateView() } }
    fun strokeHold(): Boolean = strokes.isActive && strokes.snapToShape().also { if (it) invalidateView() }
    fun strokeEnd() {
        if (!strokes.isActive) return
        val e = strokes.end()
        if (e != null && brush.mode == BrushMode.Paint) rememberColor(color)
        commit(e); strokeStart = null
    }
    fun strokeCancel() { strokes.cancel(); strokeStart = null; invalidateView() }
    val strokeActive get() = ::strokes.isInitialized && strokes.isActive

    fun eyedrop(x: Float, y: Float, remember: Boolean = false): Int {
        val c = compositor.pixelAt(x.toInt(), y.toInt())
        if (Color.alpha(c) > 0) pickColor(c, remember)
        return c
    }

    fun fill(x: Float, y: Float) {
        val d = doc ?: return; val l = d.active
        if (l.locked) { toast = "“${l.name}” is locked"; return }
        val src = if (fillAllLayers) compositor.renderFull(paper = false) else l.toBitmap()
        val tol = fillTolerance.toInt(); val c = color
        viewModelScope.launch {
            busy = "Filling…"
            val mask = withContext(Dispatchers.Default) {
                val px = IntArray(d.width * d.height); src.getPixels(px, 0, d.width, 0, 0, d.width, d.height); src.recycle()
                Ops.floodMask(px, d.width, d.height, x.toInt(), y.toInt(), tol, 1)
            }
            commit(Ops.fillMask("Fill", l, d, selection, mask, c)); rememberColor(c); busy = null
        }
    }

    fun gradient(x0: Float, y0: Float, x1: Float, y1: Float) {
        val d = doc ?: return; val l = d.active
        if (l.locked) return
        commit(Ops.gradient(l, d, selection, x0, y0, x1, y1, color, color and 0x00FFFFFF, fillMode == FillMode.Radial))
    }

    // ---------------- Selection ----------------

    private fun selectionChange(f: () -> Unit) {
        val before = selection.snapshot(); f(); commit(SelectionEdit(selection, before, selection.snapshot()))
    }

    fun selectPath(path: Path) = selectionChange { selection.fromPath(path, selectOp) }
    fun selectAll() = selectionChange { selection.selectAll() }
    fun deselect() = selectionChange { selection.clear() }
    fun invertSelection() = selectionChange { selection.invert() }

    fun magicWand(x: Float, y: Float) {
        val d = doc ?: return
        val src = if (fillAllLayers) compositor.renderFull(paper = false) else d.active.toBitmap()
        val tol = fillTolerance.toInt()
        viewModelScope.launch {
            busy = "Selecting…"
            val mask = withContext(Dispatchers.Default) {
                val px = IntArray(d.width * d.height); src.getPixels(px, 0, d.width, 0, 0, d.width, d.height)
                Ops.floodMask(px, d.width, d.height, x.toInt(), y.toInt(), tol, 0)
            }
            selectionChange { selection.fromMask(mask, selectOp) }; busy = null
        }
    }

    fun selectLayerContents(i: Int) {
        val d = doc ?: return; val l = d.layers.getOrNull(i) ?: return
        val full = l.toBitmap(); val px = IntArray(d.width * d.height); full.getPixels(px, 0, d.width, 0, 0, d.width, d.height)
        selectionChange { selection.fromMask(BooleanArray(px.size) { px[it] ushr 24 > 8 }, SelectOp.Replace) }
    }

    fun clearSelected() {
        val d = doc ?: return; val l = d.active
        val p = Paint().apply { blendMode = BlendMode.CLEAR }
        commit(Ops.editTiles("Clear", l, d, selection, allTiles = false) { c, _ -> c.drawPaint(p) })
    }

    fun fillSelected() {
        val d = doc ?: return; val l = d.active
        val p = Paint().apply { color = this@StudioViewModel.color; if (l.alphaLock) blendMode = BlendMode.SRC_ATOP }
        commit(Ops.editTiles("Fill selection", l, d, selection, allTiles = true) { c, _ -> c.drawPaint(p) })
    }

    fun copyToNewLayer(cut: Boolean) {
        val d = doc ?: return; val src = d.active
        val before = d.snapshot()
        val full = src.toBitmap()
        val nl = d.newLayer("${src.name} ${if (cut) "cut" else "copy"}")
        Ops.replaceContent("Copy", nl, full, selection, d)
        val parts = mutableListOf<Edit>()
        if (cut) Ops.editTiles("Cut", src, d, selection, false) { c, _ -> c.drawPaint(Paint().apply { blendMode = BlendMode.CLEAR }) }?.let { parts += it }
        d.layers.add(d.activeIndex + 1, nl); d.activeIndex++; d.structureChanged()
        parts += StructureEdit("New layer", d, before, d.snapshot())
        commit(com.silpa.engine.CompoundEdit(if (cut) "Cut to layer" else "Copy to layer", parts))
    }

    // ---------------- Transform ----------------

    fun beginTransform(): Boolean {
        val d = doc ?: return false
        if (floating != null) return true
        val l = d.active
        if (l.locked) { toast = "“${l.name}” is locked"; return false }
        val f = Ops.lift(l, selection, d) ?: run { toast = "Nothing to transform on this layer"; return false }
        floating = f
        compositor.liftedLayer = l; compositor.liftedTiles = f.remainder; compositor.invalidateAll()
        val r = f.origin
        corners = floatArrayOf(r.left, r.top, r.right, r.top, r.right, r.bottom, r.left, r.bottom)
        invalidateView(); return true
    }

    fun transformMatrix(): Matrix {
        val f = floating ?: return Matrix()
        val w = f.bitmap.width.toFloat(); val h = f.bitmap.height.toFloat()
        return Matrix().apply { setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, corners, 0, 4) }
    }

    fun transformBy(m: Matrix) { val c = corners.copyOf(); m.mapPoints(c); corners = c; invalidateView() }

    fun moveCorner(i: Int, x: Float, y: Float) {
        val c = corners.copyOf()
        when (transformMode) {
            TransformMode.Distort -> { c[i * 2] = x; c[i * 2 + 1] = y }
            TransformMode.Free -> {
                val o = (i + 2) % 4; val ox = c[o * 2]; val oy = c[o * 2 + 1]
                val px = c[i * 2]; val py = c[i * 2 + 1]
                val sx = if (abs(px - ox) > 1) (x - ox) / (px - ox) else 1f; val sy = if (abs(py - oy) > 1) (y - oy) / (py - oy) else 1f
                Matrix().apply { setScale(sx, sy, ox, oy) }.mapPoints(c)
            }
            TransformMode.Uniform -> {
                val o = (i + 2) % 4; val ox = c[o * 2]; val oy = c[o * 2 + 1]
                val s = hypot(x - ox, y - oy) / hypot(c[i * 2] - ox, c[i * 2 + 1] - oy).coerceAtLeast(1f)
                Matrix().apply { setScale(s, s, ox, oy) }.mapPoints(c)
            }
        }
        corners = c; invalidateView()
    }

    fun transformCenter(): PointF = PointF((corners[0] + corners[2] + corners[4] + corners[6]) / 4, (corners[1] + corners[3] + corners[5] + corners[7]) / 4)
    fun flip(horizontal: Boolean) { val c = transformCenter(); transformBy(Matrix().apply { if (horizontal) setScale(-1f, 1f, c.x, c.y) else setScale(1f, -1f, c.x, c.y) }) }
    fun rotate90() { val c = transformCenter(); transformBy(Matrix().apply { setRotate(90f, c.x, c.y) }) }
    fun resetTransform() { floating?.origin?.let { r -> corners = floatArrayOf(r.left, r.top, r.right, r.top, r.right, r.bottom, r.left, r.bottom); invalidateView() } }
    fun fitTransformToCanvas() {
        val d = doc ?: return; val f = floating ?: return
        val s = minOf(d.width.toFloat() / f.bitmap.width, d.height.toFloat() / f.bitmap.height)
        val w = f.bitmap.width * s; val h = f.bitmap.height * s
        val l = (d.width - w) / 2; val t = (d.height - h) / 2
        corners = floatArrayOf(l, t, l + w, t, l + w, t + h, l, t + h); invalidateView()
    }

    fun commitTransform() {
        val d = doc ?: return; val f = floating ?: return
        val e = Ops.commitTransform(d.active, f, transformMatrix(), d)
        compositor.liftedLayer = null; compositor.liftedTiles = emptyMap(); compositor.invalidateAll()
        floating = null
        if (selection.active) { val before = selection.snapshot(); selection.clear(); commit(com.silpa.engine.CompoundEdit("Transform", listOf(e, SelectionEdit(selection, before, selection.snapshot())))) }
        else commit(e)
    }

    fun cancelTransform() {
        compositor.liftedLayer = null; compositor.liftedTiles = emptyMap(); compositor.invalidateAll()
        floating = null; invalidateView()
    }

    // ---------------- Layers ----------------

    private fun structure(label: String, f: (Document) -> Unit) {
        val d = doc ?: return
        val before = d.snapshot(); f(d); d.structureChanged()
        commit(StructureEdit(label, d, before, d.snapshot()))
    }

    fun selectLayer(i: Int) { val d = doc ?: return; d.activeIndex = i; if (d.animation.enabled) { d.currentFrame = i; compositor.invalidateAll() }; revision++; invalidateView() }
    fun addLayer() = structure("New layer") { d -> d.layers.add(d.activeIndex + 1, d.newLayer()); d.activeIndex++; if (d.animation.enabled) d.currentFrame = d.activeIndex }
    fun duplicateLayer(i: Int) = structure("Duplicate layer") { d -> d.layers.add(i + 1, d.attach(d.layers[i].duplicate())); d.activeIndex = i + 1 }
    fun deleteLayer(i: Int) {
        val d = doc ?: return
        if (d.layers.size <= 1) { toast = "A drawing needs at least one layer"; return }
        structure("Delete layer") { it.layers.removeAt(i); it.activeIndex = (i - 1).coerceAtLeast(0); it.currentFrame = it.activeIndex }
    }
    fun moveLayer(from: Int, to: Int) {
        val d = doc ?: return
        if (to !in d.layers.indices || from == to) return
        structure("Move layer") { val l = it.layers.removeAt(from); it.layers.add(to, l); it.activeIndex = to }
    }
    fun setLayerProps(i: Int, p: LayerProps) = structure("Layer settings") { it.layers[i].props = p }
    fun mergeDown(i: Int) { val d = doc ?: return; commit(Ops.mergeDown(d, i)) }
    fun clearLayer(i: Int) {
        val d = doc ?: return; val l = d.layers[i]
        val before = HashMap<Long, Bitmap?>(l.tiles); l.tiles.keys.toList().forEach { l.set(it, null) }
        commit(com.silpa.engine.TileEdit("Clear layer", l, before, before.keys.associateWith { null }))
    }
    fun fillLayer(i: Int) {
        val d = doc ?: return; val l = d.layers[i]
        val p = Paint().apply { color = this@StudioViewModel.color; if (l.alphaLock) blendMode = BlendMode.SRC_ATOP }
        commit(Ops.editTiles("Fill layer", l, d, selection, allTiles = true) { c, _ -> c.drawPaint(p) })
    }
    fun flatten() {
        val d = doc ?: return
        val full = compositor.renderFull(paper = false)
        structure("Flatten") { doc ->
            val l = doc.newLayer("Flattened"); l.tiles.putAll(com.silpa.engine.splitIntoTiles(full, doc.width, doc.height))
            doc.layers.clear(); doc.layers += l; doc.activeIndex = 0
        }
        full.recycle()
    }

    fun layerThumb(l: Layer, size: Int): Bitmap {
        val d = doc!!
        val s = size.toFloat() / maxOf(d.width, d.height)
        val out = Bitmap.createBitmap(maxOf(1, (d.width * s).toInt()), maxOf(1, (d.height * s).toInt()), Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out); c.scale(s, s)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        for ((k, t) in l.tiles) c.drawBitmap(t, (com.silpa.engine.keyX(k) * com.silpa.engine.TILE).toFloat(), (com.silpa.engine.keyY(k) * com.silpa.engine.TILE).toFloat(), p)
        return out
    }

    /** Adds a new layer above the active one holding [full] (doc-sized) or a fitted image. */
    fun addLayerWith(name: String, full: Bitmap? = null, fitted: Bitmap? = null, respectSelection: Boolean = false, thenTransform: Boolean = false) {
        structure("Add $name") { d ->
            val l = d.newLayer(name)
            when {
                full != null && respectSelection && selection.active -> Ops.replaceContent(name, l, full, selection, d)
                full != null -> l.tiles.putAll(com.silpa.engine.splitIntoTiles(full, d.width, d.height))
                fitted != null -> Ops.placeBitmap(l, fitted, d)
            }
            d.layers.add(d.activeIndex + 1, l); d.activeIndex++
        }
        if (thenTransform) { tool = Tool.Transform; beginTransform() }
    }

    fun importImageLayer(uri: Uri) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val bmp = withContext(Dispatchers.IO) { decodeSampled(ctx, uri, 4096) } ?: run { toast = "Couldn't read that image"; return@launch }
        addLayerWith("Imported image", fitted = bmp, thenTransform = true)
    }

    // ---------------- Filters ----------------

    fun filterPreview(kind: FilterKind, v: List<Float>, src: Bitmap): Bitmap = Filters.apply(kind, v, src)

    fun applyFilter(kind: FilterKind, v: List<Float>) {
        val d = doc ?: return; val l = d.active
        if (l.locked) { toast = "“${l.name}” is locked"; return }
        val full = l.toBitmap()
        viewModelScope.launch {
            busy = "Applying ${kind.label}…"
            val out = withContext(Dispatchers.Default) { Filters.apply(kind, v, full) }
            commit(Ops.replaceContent(kind.label, l, out, selection, d)); busy = null
        }
    }

    // ---------------- Text & comics ----------------

    fun addText(text: String, size: Float, family: String, bold: Boolean, italic: Boolean, align: Layout.Alignment, outline: Boolean) {
        val d = doc ?: return
        val bmp = Ops.renderText(text, size, color, family, bold, italic, align, (d.width * 0.9f).toInt(), if (outline) size / 10f else 0f)
        val full = Bitmap.createBitmap(d.width, d.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(full).drawBitmap(bmp, (d.width - bmp.width) / 2f, (d.height - bmp.height) / 2f, null)
        addLayerWith("Text: ${text.take(16)}", full = full, thenTransform = true)
    }

    fun addPanels(layout: String, gutter: Float, border: Float, whiteGutters: Boolean) {
        val d = doc ?: return
        val m = minOf(d.width, d.height) * 0.05f
        addLayerWith("Panels", full = Ops.panels(d.width, d.height, Ops.panelLayouts[layout]!!, m, gutter, border, whiteGutters))
    }

    fun addSpeedLines(angle: Float, count: Int) { val d = doc ?: return; addLayerWith("Speed lines", full = Ops.speedLines(d.width, d.height, angle, count, color), respectSelection = true) }
    fun addFocusLines(count: Int, inner: Float) { val d = doc ?: return; addLayerWith("Focus lines", full = Ops.focusLines(d.width, d.height, d.width / 2f, d.height / 2f, count, inner, color), respectSelection = true) }
    fun addScreentone(cell: Float, dot: Float, angle: Float) { val d = doc ?: return; addLayerWith("Screentone", full = Ops.screentone(d.width, d.height, cell, dot, angle, color), respectSelection = true) }
    fun addBalloon(text: String, thought: Boolean) {
        val d = doc ?: return
        val w = d.width * 0.42f; val h = w * 0.62f
        val r = RectF((d.width - w) / 2, d.height * 0.2f, (d.width + w) / 2, d.height * 0.2f + h)
        addLayerWith("Balloon", full = Ops.balloon(d.width, d.height, r, r.centerX() - w * 0.2f, r.bottom + h * 0.5f, text, h * 0.16f, thought), thenTransform = true)
    }

    // ---------------- Guides / symmetry / canvas ----------------

    fun setPerspective(n: Int) { val d = doc ?: return; guides = guides.copy(perspective = n, vps = defaultVps(d, n), assist = n > 0 || guides.assist); invalidateView() }
    fun moveVp(i: Int, x: Float, y: Float) { guides = guides.copy(vps = guides.vps.toMutableList().also { it[i] = PointF(x, y) }); invalidateView() }
    fun setSymmetryKind(k: SymmetryKind) { symmetry = symmetry.copy(kind = k); invalidateView() }
    fun setPaper(color: Int?, visible: Boolean) { val d = doc ?: return; if (color != null) d.paperColor = color; d.paperVisible = visible; d.structureChanged() }

    // ---------------- Animation ----------------

    fun setAnimation(a: AnimationSettings) { val d = doc ?: return; d.animation = a; d.currentFrame = d.activeIndex; d.structureChanged() }

    fun togglePlay() {
        if (playing) { playJob?.cancel(); playing = false; playFrame = null; invalidateView(); return }
        val d = doc ?: return
        val frames = renderFrames(d, 1280)
        playing = true
        playJob = viewModelScope.launch {
            var i = 0
            while (isActive) {
                playFrame = frames[i]; invalidateView()
                delay(1000L / d.animation.fps.coerceIn(1, 60))
                i++
                if (i >= frames.size) { if (d.animation.loop) i = 0 else break }
            }
            playing = false; playFrame = null; invalidateView()
        }
    }

    private fun renderFrames(d: Document, maxSide: Int): List<Bitmap> {
        val saved = d.animation; val cur = d.currentFrame
        d.animation = saved.copy(enabled = true, onionSkins = 0)
        val out = d.layers.indices.map { i -> d.currentFrame = i; compositor.invalidateAll(); compositor.renderPreview(maxSide) }
        d.animation = saved; d.currentFrame = cur; compositor.invalidateAll()
        return out
    }

    // ---------------- Export ----------------

    enum class Format(val label: String, val ext: String, val mime: String) {
        PNG("PNG", "png", "image/png"), JPG("JPEG", "jpg", "image/jpeg"), WEBP("WebP", "webp", "image/webp"),
        PSD("Photoshop PSD", "psd", "image/vnd.adobe.photoshop"), PDF("PDF", "pdf", "application/pdf"),
        SILPA("Silpa project", "silpa", "application/zip"), GIF("Animated GIF", "gif", "image/gif"),
        MP4("Animation MP4", "mp4", "video/mp4"), TIMELAPSE("Timelapse MP4", "mp4", "video/mp4"),
    }

    fun exportName(f: Format) = (doc?.name ?: "Silpa").replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "Silpa" } + (if (f == Format.TIMELAPSE) " timelapse" else "") + "." + f.ext

    fun export(f: Format, uri: Uri, then: (() -> Unit)? = null) {
        val ctx = getApplication<Application>()
        exportTo(f, { ctx.contentResolver.openOutputStream(uri)!! }, { file -> ctx.contentResolver.openOutputStream(uri)!!.use { o -> file.inputStream().use { it.copyTo(o) } } }, then)
    }

    /** Exports to a cache file for the share sheet. */
    fun exportForShare(f: Format, done: (File) -> Unit) {
        val dir = File(getApplication<Application>().cacheDir, "share").apply { mkdirs() }
        val file = File(dir, exportName(f))
        exportTo(f, { FileOutputStream(file) }, { tmp -> tmp.copyTo(file, overwrite = true) }) { done(file) }
    }

    private fun exportTo(f: Format, open: () -> java.io.OutputStream, copyFile: (File) -> Unit, then: (() -> Unit)?) {
        val d = doc ?: return; val id = projectId ?: return
        saveNow()
        val ctx = getApplication<Application>()
        val flat = if (f in listOf(Format.PNG, Format.JPG, Format.WEBP, Format.PDF, Format.PSD)) compositor.renderFull(paper = f != Format.PNG || d.paperVisible) else null
        val frames = if (f == Format.GIF || f == Format.MP4) renderFrames(d, if (f == Format.GIF) 720 else 1280) else null
        viewModelScope.launch {
            busy = "Exporting ${f.label}…"
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    when (f) {
                        Format.PNG -> open().use { Export.image(flat!!, Bitmap.CompressFormat.PNG, 100, it) }
                        Format.JPG -> open().use { Export.image(flat!!, Bitmap.CompressFormat.JPEG, 95, it) }
                        Format.WEBP -> open().use { Export.image(flat!!, Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it) }
                        Format.PDF -> open().use { Export.pdf(flat!!, it) }
                        Format.PSD -> open().use { Psd.write(d, flat!!, it) }
                        Format.SILPA -> open().use { store.exportPackage(id, it) }
                        Format.GIF -> open().use { o ->
                            val fr = frames!!
                            GifEncoder(o, fr[0].width, fr[0].height, 1000 / d.animation.fps, d.animation.loop).apply { buildPalette(fr); fr.forEach { addFrame(it) }; finish() }
                        }
                        Format.MP4, Format.TIMELAPSE -> {
                            val tmp = File(ctx.cacheDir, "export.mp4")
                            if (f == Format.MP4) {
                                val fr = frames!!
                                val enc = Mp4Encoder(tmp, fr[0].width, fr[0].height, d.animation.fps)
                                repeat(if (d.animation.loop) 3 else 1) { fr.forEach { enc.add(it) } }; enc.finish()
                            } else {
                                val files = store.timelapseDir(id).listFiles()?.sortedBy { it.name }.orEmpty()
                                require(files.isNotEmpty()) { "No timelapse recorded yet — draw something first" }
                                val first = BitmapFactory.decodeFile(files.first().path)
                                val enc = Mp4Encoder(tmp, first.width.coerceAtLeast(320), first.height.coerceAtLeast(320), 30)
                                val step = maxOf(1, files.size / 900)
                                files.filterIndexed { i, _ -> i % step == 0 }.forEach { fl -> BitmapFactory.decodeFile(fl.path)?.let { enc.add(it); it.recycle() } }
                                val last = flat ?: compositor.renderPreview(1280)
                                repeat(45) { enc.add(last) }
                                enc.finish()
                            }
                            copyFile(tmp)
                        }
                    }
                }
            }
            busy = null
            toast = r.fold({ "Exported ${f.label}" }, { "Export failed: ${it.message}" })
            if (r.isSuccess) then?.invoke()
        }
    }

    companion object {
        fun decodeSampled(ctx: android.content.Context, uri: Uri, max: Int): Bitmap? {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1; while (maxOf(o.outWidth, o.outHeight) / s > max) s *= 2
            return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s; inMutable = true }) }
        }
    }
}

private fun Float.coerceIn(min: Int, max: Int) = coerceIn(min.toFloat(), max.toFloat())
