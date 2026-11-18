package com.silpa.feature.studio

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.input.motionprediction.MotionEventPredictor
import com.silpa.engine.Compositor
import com.silpa.engine.StrokePoint
import com.silpa.engine.SymmetryKind
import com.silpa.engine.TILE
import com.silpa.engine.keyX
import com.silpa.engine.keyY
import com.silpa.engine.tileKey
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The drawing surface. Renders cached composite tiles through a pan/zoom/rotate matrix and turns
 * touch + stylus input into tool actions. Events are requested unbuffered and the tail of each
 * stroke is extended with Android's motion predictor to cut perceived latency.
 */
@SuppressLint("ViewConstructor")
class CanvasView(context: Context, private val vm: StudioViewModel, private val onToggleFullscreen: () -> Unit) : View(context) {
    private val viewM = Matrix()
    private val inv = Matrix()
    private var fitted = false
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    /** Null where the platform can't provide prediction (e.g. no display context); drawing still works. */
    private val predictor: MotionEventPredictor? by lazy { runCatching { MotionEventPredictor.newInstance(this) }.getOrNull() }
    private val handler = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density

    private enum class Mode { None, Draw, ShapeDraw, Gesture, Eyedrop, Lasso, Shape, Wand, Fill, Gradient, TransformMove, TransformCorner, TransformRotate, GuideDrag, Pan }
    private var mode = Mode.None
    private var maxPointers = 0
    private var downTime = 0L
    private var downX = 0f; private var downY = 0f
    private var travelled = 0f
    private var lastX = 0f; private var lastY = 0f
    private var lastMoveTime = 0L
    private var activeCorner = -1
    private var guideIndex = -1

    // gesture state
    private var gCx = 0f; private var gCy = 0f; private var gSpan = 0f; private var gAngle = 0f

    // overlays
    private val lassoPath = Path()
    private var dragStart: PointF? = null
    private var dragEnd: PointF? = null
    private var hover: PointF? = null
    private var loupe: PointF? = null
    private var loupePrev = 0
    private var predicted: FloatArray? = null

    init {
        vm.invalidateView = { postInvalidateOnAnimation() }
        vm.refitView = { fitted = false; if (width > 0) fit(); postInvalidateOnAnimation() }
        isFocusable = true
        contentDescription = "Drawing canvas"
    }

    fun fit() {
        val d = vm.doc ?: return
        if (width == 0 || height == 0) return
        val s = min(width / d.width.toFloat(), height / d.height.toFloat()) * 0.86f
        viewM.reset(); viewM.setScale(s, s)
        viewM.postTranslate((width - d.width * s) / 2f, (height - d.height * s) / 2f)
        fitted = true; invalidate()
    }

    fun flipView() { val c = PointF(width / 2f, height / 2f); viewM.postScale(-1f, 1f, c.x, c.y); invalidate() }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { if (!fitted) fit() }

    private fun scale(): Float { val v = FloatArray(9); viewM.getValues(v); return sqrt(v[0] * v[0] + v[3] * v[3]) }

    private fun toDoc(x: Float, y: Float): PointF { viewM.invert(inv); val p = floatArrayOf(x, y); inv.mapPoints(p); return PointF(p[0], p[1]) }
    private fun toView(x: Float, y: Float): PointF { val p = floatArrayOf(x, y); viewM.mapPoints(p); return PointF(p[0], p[1]) }

    // ---------------- Rendering ----------------

    private val checker: BitmapShader by lazy {
        val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        Canvas(b).apply { drawColor(0xFFFFFFFF.toInt()); val p = Paint().apply { color = 0xFFDADCE2.toInt() }; drawRect(0f, 0f, 16f, 16f, p); drawRect(16f, 16f, 32f, 32f, p) }
        BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2B2F.toInt(); setShadowLayer(18f, 0f, 6f, 0x88000000.toInt()) }
    private val overlay = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * resources.displayMetrics.density }
    private val dim = Paint().apply { color = 0x66000000 }
    private val dstOut = Paint().apply { blendMode = BlendMode.DST_OUT }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0xFF2A2B2F.toInt())
        val d = vm.doc ?: return
        if (!fitted) fit()
        val docRect = RectF(0f, 0f, d.width.toFloat(), d.height.toFloat())

        vm.playFrame?.let { f ->
            canvas.save(); canvas.concat(viewM)
            canvas.drawRect(docRect, shadowPaint)
            canvas.drawBitmap(f, null, docRect, tilePaint)
            canvas.restore(); return
        }

        canvas.save(); canvas.concat(viewM)
        canvas.drawRect(docRect, shadowPaint)
        if (!d.paperVisible) canvas.drawRect(docRect, Paint().apply { shader = checker })
        viewM.invert(inv)
        val vis = RectF(0f, 0f, width.toFloat(), height.toFloat()); inv.mapRect(vis)
        val x0 = max(0, floor(vis.left / TILE).toInt()); val x1 = min(d.tilesX - 1, floor(vis.right / TILE).toInt())
        val y0 = max(0, floor(vis.top / TILE).toInt()); val y1 = min(d.tilesY - 1, floor(vis.bottom / TILE).toInt())
        val s = scale()
        tilePaint.isFilterBitmap = s < 2.5f
        tilePaint.colorFilter = Compositor.colorBlindFilter(vm.colorBlind)
        val comp = vm.compositor
        for (ty in y0..y1) for (tx in x0..x1) {
            canvas.drawBitmap(comp.tile(tileKey(tx, ty)), (tx * TILE).toFloat(), (ty * TILE).toFloat(), tilePaint)
        }
        vm.floating?.let { f -> canvas.drawBitmap(f.bitmap, vm.transformMatrix(), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)) }

        // Selection: dim unselected areas while the select tool is active.
        val sel = vm.selection
        if (sel.active && vm.tool == Tool.Select) {
            val sc = canvas.saveLayer(docRect, null)
            canvas.drawRect(docRect, dim)
            for ((k, m) in sel.tiles) canvas.drawBitmap(m, (keyX(k) * TILE).toFloat(), (keyY(k) * TILE).toFloat(), dstOut)
            canvas.restoreToCount(sc)
        }
        canvas.restore()

        drawGuides(canvas, d.width, d.height)
        if (sel.active) sel.outline?.let { o -> marching(canvas, o) }
        if (mode == Mode.Lasso) marching(canvas, lassoPath)
        if (mode == Mode.Shape) dragRect()?.let { r -> marching(canvas, Path().apply { if (vm.selectShape == SelectShape.Ellipse) addOval(r, Path.Direction.CW) else addRect(r, Path.Direction.CW) }) }
        if (mode == Mode.Gradient) { val a = dragStart; val b = dragEnd; if (a != null && b != null) { val va = toView(a.x, a.y); val vb = toView(b.x, b.y); overlay.color = Color.WHITE; canvas.drawLine(va.x, va.y, vb.x, vb.y, overlay); canvas.drawCircle(va.x, va.y, 8 * density, overlay); canvas.drawCircle(vb.x, vb.y, 8 * density, overlay) } }
        if (vm.floating != null) drawTransformBox(canvas)
        if (vm.shapePending && vm.shapePoints.isNotEmpty()) {
            val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            vm.shapePoints.forEachIndexed { i, q ->
                val v = toView(q.x, q.y)
                canvas.drawCircle(v.x, v.y, (if (i == 0 && vm.drawMode == DrawMode.Polygon) 10 else 7) * density, hp)
                overlay.color = 0xFF1F3FD1.toInt(); canvas.drawCircle(v.x, v.y, (if (i == 0 && vm.drawMode == DrawMode.Polygon) 10 else 7) * density, overlay)
            }
        }
        predicted?.let { p -> if (mode == Mode.Draw && p.size == 4) {
            val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = vm.color; strokeWidth = max(1f, vm.brush.size * 0.6f * s); strokeCap = Paint.Cap.ROUND; alpha = 140 }
            canvas.drawLine(p[0], p[1], p[2], p[3], pp)
        } }
        hover?.let { h -> overlay.color = 0xCCFFFFFF.toInt(); canvas.drawCircle(h.x, h.y, max(3f, vm.brush.size * s / 2), overlay); overlay.color = 0x66000000; canvas.drawCircle(h.x, h.y, max(3f, vm.brush.size * s / 2) + 1.5f, overlay) }
        loupe?.let { l ->
            val r = 46 * density; val cy = l.y - 70 * density
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = vm.color; canvas.drawArc(l.x - r, cy - r, l.x + r, cy + r, 180f, 180f, true, p)
            p.color = loupePrev; canvas.drawArc(l.x - r, cy - r, l.x + r, cy + r, 0f, 180f, true, p)
            overlay.color = Color.WHITE; overlay.strokeWidth = 4 * density; canvas.drawCircle(l.x, cy, r, overlay); overlay.strokeWidth = 1.5f * density
        }
    }

    private fun marching(canvas: Canvas, docPath: Path) {
        val p = Path(docPath); p.transform(viewM)
        overlay.pathEffect = null; overlay.color = 0xCC000000.toInt(); canvas.drawPath(p, overlay)
        overlay.pathEffect = DashPathEffect(floatArrayOf(8 * density, 6 * density), 0f); overlay.color = Color.WHITE; canvas.drawPath(p, overlay)
        overlay.pathEffect = null
    }

    private fun drawGuides(canvas: Canvas, w: Int, h: Int) {
        val g = vm.guides
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * density; color = 0x804CB4E6.toInt() }
        if (g.grid) {
            canvas.save(); canvas.concat(viewM); canvas.clipRect(0f, 0f, w.toFloat(), h.toFloat())
            line.strokeWidth = 1f / scale() * density
            val step = g.gridSize.toFloat()
            if (g.isometric) {
                val t = Math.tan(Math.toRadians(30.0)).toFloat()
                var x = -h / t
                while (x < w + h / t) { canvas.drawLine(x, 0f, x + h / t, h.toFloat(), line); canvas.drawLine(x, h.toFloat(), x + h / t, 0f, line); x += step * 2 }
                var vx = 0f; while (vx <= w) { canvas.drawLine(vx, 0f, vx, h.toFloat(), line); vx += step * 2 * sqrt(3f) / 2 }
            } else {
                var x = 0f; while (x <= w) { canvas.drawLine(x, 0f, x, h.toFloat(), line); x += step }
                var y = 0f; while (y <= h) { canvas.drawLine(0f, y, w.toFloat(), y, line); y += step }
            }
            canvas.restore()
        }
        if (g.perspective > 0) {
            line.strokeWidth = 1f * density
            val reach = hypot(width.toFloat(), height.toFloat()) * 2
            g.vps.take(g.perspective).forEachIndexed { i, vp ->
                val v = toView(vp.x, vp.y)
                line.color = listOf(0x80F28B6B.toInt(), 0x804CB4E6.toInt(), 0x80B8C4FF.toInt())[i % 3]
                for (k in 0 until 36) { val a = k * PI / 18; canvas.drawLine(v.x, v.y, v.x + (cos(a) * reach).toFloat(), v.y + (sin(a) * reach).toFloat(), line) }
                val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = line.color or 0xFF000000.toInt() }
                canvas.drawCircle(v.x, v.y, (if (g.editing) 14 else 6) * density, hp)
            }
            if (g.perspective >= 2) { val a = toView(g.vps[0].x, g.vps[0].y); val b = toView(g.vps[1].x, g.vps[1].y); line.color = 0xC0FFFFFF.toInt(); canvas.drawLine(a.x - (b.x - a.x) * 4, a.y - (b.y - a.y) * 4, b.x + (b.x - a.x) * 4, b.y + (b.y - a.y) * 4, line) }
        }
        val sym = vm.symmetry
        if (sym.kind != SymmetryKind.None) {
            val c = toView(sym.cx, sym.cy)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * density; color = 0xC01F3FD1.toInt(); pathEffect = DashPathEffect(floatArrayOf(10 * density, 6 * density), 0f) }
            val reach = hypot(width.toFloat(), height.toFloat())
            val v = FloatArray(9); viewM.getValues(v); val rot = atan2(v[3], v[0])
            val axes = when (sym.kind) {
                SymmetryKind.Vertical -> listOf(PI / 2)
                SymmetryKind.Horizontal -> listOf(0.0)
                SymmetryKind.Quadrant -> listOf(0.0, PI / 2)
                else -> (0 until sym.count).map { PI * it / sym.count + PI / 2 }
            }
            for (a in axes) { val aa = a + rot; canvas.drawLine(c.x - (cos(aa) * reach).toFloat(), c.y - (sin(aa) * reach).toFloat(), c.x + (cos(aa) * reach).toFloat(), c.y + (sin(aa) * reach).toFloat(), p) }
        }
    }

    private fun viewCorners(): FloatArray { val c = vm.corners.copyOf(); viewM.mapPoints(c); return c }

    private fun rotateHandle(c: FloatArray): PointF {
        val mx = (c[0] + c[2]) / 2; val my = (c[1] + c[3]) / 2
        val cx = (c[0] + c[2] + c[4] + c[6]) / 4; val cy = (c[1] + c[3] + c[5] + c[7]) / 4
        val dx = mx - cx; val dy = my - cy; val l = max(1f, hypot(dx, dy))
        return PointF(mx + dx / l * 36 * density, my + dy / l * 36 * density)
    }

    private fun drawTransformBox(canvas: Canvas) {
        val c = viewCorners()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * density; color = 0xFF4CB4E6.toInt() }
        val path = Path().apply { moveTo(c[0], c[1]); lineTo(c[2], c[3]); lineTo(c[4], c[5]); lineTo(c[6], c[7]); close() }
        canvas.drawPath(path, p)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val r = rotateHandle(c)
        canvas.drawLine((c[0] + c[2]) / 2, (c[1] + c[3]) / 2, r.x, r.y, p)
        canvas.drawCircle(r.x, r.y, 9 * density, fill); canvas.drawCircle(r.x, r.y, 9 * density, p)
        for (i in 0 until 4) { canvas.drawCircle(c[i * 2], c[i * 2 + 1], 10 * density, fill); canvas.drawCircle(c[i * 2], c[i * 2 + 1], 10 * density, p) }
    }

    private fun dragRect(): RectF? {
        val a = dragStart ?: return null; val b = dragEnd ?: return null
        return RectF(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
    }

    // ---------------- Input ----------------

    private fun point(e: MotionEvent, i: Int, h: Int = -1): StrokePoint {
        val x = if (h < 0) e.getX(i) else e.getHistoricalX(i, h)
        val y = if (h < 0) e.getY(i) else e.getHistoricalY(i, h)
        val finger = e.getToolType(i) == MotionEvent.TOOL_TYPE_FINGER
        val pr = if (finger) 0.75f else (if (h < 0) e.getPressure(i) else e.getHistoricalPressure(i, h)).coerceIn(0.01f, 1f)
        val tilt = if (h < 0) e.getAxisValue(MotionEvent.AXIS_TILT, i) else e.getHistoricalAxisValue(MotionEvent.AXIS_TILT, i, h)
        val t = if (h < 0) e.eventTime else e.getHistoricalEventTime(h)
        val d = toDoc(x, y)
        return StrokePoint(d.x, d.y, pr, tilt, t)
    }

    private val longPress = Runnable {
        if (mode == Mode.Draw && travelled < 10 * density) {
            vm.strokeCancel(); mode = Mode.Eyedrop
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            loupePrev = vm.color; sample(lastX, lastY)
        }
    }

    private val holdCheck = object : Runnable {
        override fun run() {
            if (mode != Mode.Draw) return
            if (System.currentTimeMillis() - lastMoveTime >= 550 && travelled > 24 * density) {
                if (vm.strokeHold()) performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else handler.postDelayed(this, 120)
        }
    }

    private fun sample(x: Float, y: Float) { val d = toDoc(x, y); vm.eyedrop(d.x, d.y); loupe = PointF(x, y); invalidate() }

    override fun onHoverEvent(e: MotionEvent): Boolean {
        if (e.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS && vm.tool in listOf(Tool.Brush, Tool.Smudge, Tool.Eraser)) {
            hover = if (e.actionMasked == MotionEvent.ACTION_HOVER_EXIT) null else PointF(e.x, e.y); invalidate(); return true
        }
        return super.onHoverEvent(e)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (vm.doc == null || vm.playing) return true
        predictor?.record(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(e)
                hover = null
                maxPointers = 1; downTime = e.eventTime; downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; travelled = 0f
                lastMoveTime = System.currentTimeMillis()
                val type = e.getToolType(0)
                val stylus = type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER
                if (stylus) vm.stylusSeen = true
                if (!stylus && vm.stylusOnly && vm.stylusSeen) { mode = Mode.Pan; return true }
                startPrimary(e, type == MotionEvent.TOOL_TYPE_ERASER || (stylus && e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0), !stylus)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = max(maxPointers, e.pointerCount)
                if (mode == Mode.TransformMove || mode == Mode.TransformCorner || mode == Mode.TransformRotate) { mode = Mode.Gesture; beginGesture(e); return true }
                if (mode != Mode.Gesture) {
                    if (mode == Mode.Draw) vm.strokeCancel()
                    if (mode == Mode.ShapeDraw && !vm.shapePending) vm.cancelShape()
                    handler.removeCallbacks(longPress); handler.removeCallbacks(holdCheck)
                    lassoPath.reset(); dragStart = null; dragEnd = null; loupe = null
                    mode = Mode.Gesture
                }
                beginGesture(e)
            }
            MotionEvent.ACTION_POINTER_UP -> { if (mode == Mode.Gesture) beginGesture(e, skip = e.actionIndex) }
            MotionEvent.ACTION_MOVE -> {
                travelled += hypot(e.x - lastX, e.y - lastY)
                if (hypot(e.x - lastX, e.y - lastY) > 1.5f) lastMoveTime = System.currentTimeMillis()
                when (mode) {
                    Mode.Draw -> {
                        for (h in 0 until e.historySize) vm.strokeMove(point(e, 0, h))
                        vm.strokeMove(point(e, 0))
                        predicted = predictor?.predict()?.let { pe -> floatArrayOf(e.x, e.y, pe.x, pe.y).also { pe.recycle() } }
                    }
                    Mode.ShapeDraw -> { val d = toDoc(e.x, e.y); vm.shapeMove(d.x, d.y) }
                    Mode.Gesture -> gesture(e)
                    Mode.Pan -> { viewM.postTranslate(e.x - lastX, e.y - lastY); invalidate() }
                    Mode.Eyedrop -> sample(e.x, e.y)
                    Mode.Lasso -> { val d = toDoc(e.x, e.y); lassoPath.lineTo(d.x, d.y); invalidate() }
                    Mode.Shape, Mode.Gradient -> { dragEnd = toDoc(e.x, e.y); invalidate() }
                    Mode.TransformMove -> { val a = toDoc(lastX, lastY); val b = toDoc(e.x, e.y); vm.transformBy(Matrix().apply { setTranslate(b.x - a.x, b.y - a.y) }) }
                    Mode.TransformCorner -> { val d = toDoc(e.x, e.y); vm.moveCorner(activeCorner, d.x, d.y) }
                    Mode.TransformRotate -> {
                        val c = vm.transformCenter(); val a = toDoc(lastX, lastY); val b = toDoc(e.x, e.y)
                        val ang = Math.toDegrees((atan2(b.y - c.y, b.x - c.x) - atan2(a.y - c.y, a.x - c.x)).toDouble()).toFloat()
                        vm.transformBy(Matrix().apply { setRotate(ang, c.x, c.y) })
                    }
                    Mode.GuideDrag -> { val d = toDoc(e.x, e.y); vm.moveVp(guideIndex, d.x, d.y) }
                    else -> Unit
                }
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_UP -> finish(e, cancelled = false)
            MotionEvent.ACTION_CANCEL -> finish(e, cancelled = true)
        }
        return true
    }

    private fun startPrimary(e: MotionEvent, eraserTip: Boolean, finger: Boolean) {
        val d = toDoc(e.x, e.y)
        val g = vm.guides
        if (g.editing && g.perspective > 0) {
            val idx = g.vps.take(g.perspective).indexOfFirst { val v = toView(it.x, it.y); hypot(v.x - e.x, v.y - e.y) < 32 * density }
            if (idx >= 0) { guideIndex = idx; mode = Mode.GuideDrag; return }
        }
        when (vm.tool) {
            Tool.Brush, Tool.Eraser -> if (vm.drawMode != DrawMode.Freehand) { mode = Mode.ShapeDraw; vm.shapeDown(d.x, d.y) } else {
                if (vm.strokeBegin(point(e, 0), eraserTip)) {
                    mode = Mode.Draw
                    if (finger) handler.postDelayed(longPress, 450)
                    handler.postDelayed(holdCheck, 600)
                } else mode = Mode.None
            }
            Tool.Smudge -> {
                if (vm.strokeBegin(point(e, 0), false)) mode = Mode.Draw else mode = Mode.None
            }
            Tool.Eyedropper -> { mode = Mode.Eyedrop; loupePrev = vm.color; sample(e.x, e.y) }
            Tool.Fill -> { if (vm.fillMode == FillMode.Flood) mode = Mode.Fill else { mode = Mode.Gradient; dragStart = d; dragEnd = d } }
            Tool.Select -> when (vm.selectShape) {
                SelectShape.Lasso -> { mode = Mode.Lasso; lassoPath.reset(); lassoPath.moveTo(d.x, d.y) }
                SelectShape.Rect, SelectShape.Ellipse -> { mode = Mode.Shape; dragStart = d; dragEnd = d }
                SelectShape.Wand -> mode = Mode.Wand
            }
            Tool.Transform -> {
                if (vm.floating == null && !vm.beginTransform()) { mode = Mode.None; return }
                val c = viewCorners()
                val r = rotateHandle(c)
                activeCorner = (0 until 4).firstOrNull { hypot(c[it * 2] - e.x, c[it * 2 + 1] - e.y) < 28 * density } ?: -1
                mode = when {
                    activeCorner >= 0 -> Mode.TransformCorner
                    hypot(r.x - e.x, r.y - e.y) < 28 * density -> Mode.TransformRotate
                    else -> Mode.TransformMove
                }
            }
        }
    }

    private fun finish(e: MotionEvent, cancelled: Boolean) {
        handler.removeCallbacks(longPress); handler.removeCallbacks(holdCheck)
        predicted = null
        val quick = e.eventTime - downTime < 280 && travelled < 24 * density
        when (mode) {
            Mode.Draw -> if (cancelled) vm.strokeCancel() else { if (e.historySize > 0 || e.pointerCount > 0) vm.strokeMove(point(e, 0)); vm.strokeEnd() }
            Mode.ShapeDraw -> if (!cancelled) { val d = toDoc(e.x, e.y); vm.shapeUp(d.x, d.y, 28 * density / scale()) }
            Mode.Gesture -> if (quick && !cancelled) when {
                maxPointers == 2 -> { vm.undo(); performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
                maxPointers == 3 -> { vm.redo(); performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
                maxPointers >= 4 -> onToggleFullscreen()
            }
            Mode.Eyedrop -> { vm.pickColor(vm.color); loupe = null }
            Mode.Fill -> if (!cancelled) { val d = toDoc(e.x, e.y); vm.fill(d.x, d.y) }
            Mode.Gradient -> { val a = dragStart; val b = dragEnd; if (!cancelled && a != null && b != null && hypot(a.x - b.x, a.y - b.y) > 2) vm.gradient(a.x, a.y, b.x, b.y) }
            Mode.Lasso -> if (!cancelled) { lassoPath.close(); if (travelled > 20 * density) vm.selectPath(Path(lassoPath)) else if (quick) vm.deselect() }
            Mode.Shape -> if (!cancelled) { val r = dragRect(); if (r != null && r.width() > 2 && r.height() > 2) vm.selectPath(Path().apply { if (vm.selectShape == SelectShape.Ellipse) addOval(r, Path.Direction.CW) else addRect(r, Path.Direction.CW) }) else if (quick) vm.deselect() }
            Mode.Wand -> if (!cancelled) { val d = toDoc(e.x, e.y); vm.magicWand(d.x, d.y) }
            else -> Unit
        }
        lassoPath.reset(); dragStart = null; dragEnd = null
        mode = Mode.None; maxPointers = 0
        invalidate()
    }

    private fun centroid(e: MotionEvent, skip: Int): Triple<Float, Float, Int> {
        var x = 0f; var y = 0f; var n = 0
        for (i in 0 until e.pointerCount) if (i != skip) { x += e.getX(i); y += e.getY(i); n++ }
        return Triple(x / max(1, n), y / max(1, n), n)
    }

    private fun beginGesture(e: MotionEvent, skip: Int = -1) {
        val (cx, cy, n) = centroid(e, skip)
        gCx = cx; gCy = cy
        var span = 0f
        for (i in 0 until e.pointerCount) if (i != skip) span += hypot(e.getX(i) - cx, e.getY(i) - cy)
        gSpan = span / max(1, n)
        val idx = (0 until e.pointerCount).filter { it != skip }
        gAngle = if (idx.size >= 2) atan2(e.getY(idx[1]) - e.getY(idx[0]), e.getX(idx[1]) - e.getX(idx[0])) else gAngle
    }

    private fun gesture(e: MotionEvent) {
        if (e.pointerCount < 2) { val dx = e.x - gCx; val dy = e.y - gCy; viewM.postTranslate(dx, dy); gCx = e.x; gCy = e.y; invalidate(); return }
        val (cx, cy, n) = centroid(e, -1)
        var span = 0f
        for (i in 0 until e.pointerCount) span += hypot(e.getX(i) - cx, e.getY(i) - cy)
        span /= n
        val ang = atan2(e.getY(1) - e.getY(0), e.getX(1) - e.getX(0))
        val ds = if (gSpan > 10f) span / gSpan else 1f
        val da = Math.toDegrees((ang - gAngle).toDouble()).toFloat()
        if (vm.floating != null && vm.tool == Tool.Transform) {
            val a = toDoc(gCx, gCy); val b = toDoc(cx, cy)
            vm.transformBy(Matrix().apply { setTranslate(b.x - a.x, b.y - a.y); postScale(ds, ds, b.x, b.y); postRotate(da, b.x, b.y) })
        } else {
            viewM.postTranslate(cx - gCx, cy - gCy)
            val s = scale()
            val clamped = (s * ds).coerceIn(0.02f, 64f) / s
            viewM.postScale(clamped, clamped, cx, cy)
            if (e.pointerCount == 2) viewM.postRotate(da, cx, cy)
            invalidate()
        }
        gCx = cx; gCy = cy; gSpan = span; gAngle = ang
    }

    fun resetRotation() {
        val v = FloatArray(9); viewM.getValues(v)
        val rot = Math.toDegrees(atan2(v[3], v[0]).toDouble()).toFloat()
        viewM.postRotate(-rot, width / 2f, height / 2f); invalidate()
    }

    override fun onDetachedFromWindow() { handler.removeCallbacksAndMessages(null); super.onDetachedFromWindow() }
}
