package com.silpa.engine

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

data class StrokePoint(val x: Float, val y: Float, val pressure: Float = 1f, val tilt: Float = 0f, val time: Long = 0L)

enum class SymmetryKind { None, Vertical, Horizontal, Quadrant, Radial, Kaleidoscope }

data class Symmetry(val kind: SymmetryKind = SymmetryKind.None, val count: Int = 6, val cx: Float = 0f, val cy: Float = 0f) {
    /** Transforms each dab position/angle is mirrored or rotated through. */
    fun transforms(): List<Matrix> {
        val out = mutableListOf(Matrix())
        fun m(f: Matrix.() -> Unit) = Matrix().apply(f)
        when (kind) {
            SymmetryKind.None -> Unit
            SymmetryKind.Vertical -> out += m { setScale(-1f, 1f, cx, cy) }
            SymmetryKind.Horizontal -> out += m { setScale(1f, -1f, cx, cy) }
            SymmetryKind.Quadrant -> { out += m { setScale(-1f, 1f, cx, cy) }; out += m { setScale(1f, -1f, cx, cy) }; out += m { setScale(-1f, -1f, cx, cy) } }
            SymmetryKind.Radial, SymmetryKind.Kaleidoscope -> {
                val n = count.coerceIn(2, 32)
                for (i in 1 until n) out += m { setRotate(360f * i / n, cx, cy) }
                if (kind == SymmetryKind.Kaleidoscope) {
                    for (i in 0 until n) out += m { setScale(-1f, 1f, cx, cy); postRotate(360f * i / n, cx, cy) }
                }
            }
        }
        return out
    }
}

/**
 * Turns pointer samples into brush dabs. Paint and erase strokes render into a [StrokeBuffer]
 * that the compositor shows live; smudge edits copies of layer tiles directly.
 */
class StrokeEngine(private val doc: Document, private val selection: Selection, private val compositor: Compositor) {
    private lateinit var brush: BrushSettings
    private var color = Color.BLACK
    private var layer: Layer? = null
    private var buffer: StrokeBuffer? = null
    private var transforms: List<Matrix> = listOf(Matrix())
    private var mirror: List<Boolean> = listOf(false)

    private var fx = 0f; private var fy = 0f
    private var lastDabX = 0f; private var lastDabY = 0f; private var lastP = 1f
    private var lastRaw: StrokePoint? = null
    private var velocity = 0f
    private var direction = 0f
    private var residual = 0f
    private var started = false
    private var smoothing = 0.25f
    private val rnd = Random(System.nanoTime())


    /** Every filtered point of this stroke, for QuickShape. */
    val points = ArrayList<StrokePoint>()
    var shape: QuickShape? = null
        private set

    // Smudge state
    private val smudgeBefore = HashMap<Long, Bitmap?>()
    private val smudgeOwned = HashSet<Long>()

    private val dabPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dabMatrix = Matrix()
    private val inv = Matrix()
    private val grainShaders = HashMap<Int, BitmapShader>()
    private var filterColor = 0
    private var dabFilter: PorterDuffColorFilter? = null
    private val localM = Matrix()

    val isActive get() = layer != null

    fun begin(target: Layer, settings: BrushSettings, argb: Int, symmetry: Symmetry, streamline: Float, p: StrokePoint) {
        brush = settings; color = argb; layer = target
        smoothing = streamline
        transforms = symmetry.transforms()
        mirror = transforms.map { val v = FloatArray(9); it.getValues(v); v[0] * v[4] - v[1] * v[3] < 0 }
        points.clear(); shape = null
        started = false; residual = 0f; velocity = 0f; lastRaw = p
        fx = p.x; fy = p.y
        carries.clear(); carryD = 0; smudgeBefore.clear(); smudgeOwned.clear()
        if (settings.mode != BrushMode.Smudge) {
            buffer = StrokeBuffer(target, settings.mode, settings.opacity, settings.glow).also { compositor.stroke = it }
        }
        add(p)
    }

    fun add(p: StrokePoint) {
        if (layer == null) return
        if (shape != null) { shape = shape!!.adjust(p.x, p.y); renderShape(); return }
        val lr = lastRaw
        if (lr != null && p.time > lr.time) {
            val v = hypot(p.x - lr.x, p.y - lr.y) / (p.time - lr.time)
            velocity = velocity * 0.7f + v * 0.3f
        }
        lastRaw = p
        val k = 1f - smoothing.coerceIn(0f, 0.95f) * 0.9f
        fx += (p.x - fx) * k; fy += (p.y - fy) * k
        points += StrokePoint(fx, fy, p.pressure, p.tilt, p.time)
        emitTo(fx, fy, p.pressure, p.tilt)
    }

    private fun emitTo(x: Float, y: Float, pressure: Float, tilt: Float) {
        if (!started) {
            started = true; lastDabX = x; lastDabY = y; lastP = pressure
            dab(x, y, pressure, tilt); return
        }
        val dx = x - lastDabX; val dy = y - lastDabY
        val dist = hypot(dx, dy)
        if (dist < 0.01f) return
        direction = atan2(dy, dx)
        var t = residual
        val sp0 = max(0.5f, brush.spacing * diameter(pressure, tilt))
        var traveled = 0f
        while (true) {
            val step = max(0.5f, brush.spacing * diameter(lastP + (pressure - lastP) * (traveled / dist), tilt))
            val next = traveled + (step - t)
            if (next > dist) { residual = t + (dist - traveled); break }
            traveled = next; t = 0f
            val f = traveled / dist
            dab(lastDabX + dx * f, lastDabY + dy * f, lastP + (pressure - lastP) * f, tilt)
        }
        if (sp0 <= 0f) residual = 0f
        lastDabX = x; lastDabY = y; lastP = pressure
    }

    private fun curve(p: Float) = p.coerceIn(0f, 1f).pow(brush.pressureGamma)

    private fun diameter(pressure: Float, tilt: Float): Float {
        var s = brush.size
        if (brush.sizePressure) s *= brush.minSize + (1f - brush.minSize) * curve(pressure)
        if (brush.tiltSize > 0f) s *= 1f + brush.tiltSize * (tilt / (PI.toFloat() / 2)).coerceIn(0f, 1f) * 2.5f
        if (brush.velocityThin > 0f) s *= 1f - brush.velocityThin * (velocity / 3f).coerceIn(0f, 0.85f)
        return max(0.5f, s)
    }

    private fun dab(x: Float, y: Float, pressure: Float, tilt: Float) {
        val b = brush
        repeat(max(1, b.count)) {
            var d = diameter(pressure, tilt)
            if (b.sizeJitter > 0) d *= 1f - b.sizeJitter * rnd.nextFloat()
            var a = b.flow * (if (b.opacityPressure) curve(pressure) else 1f)
            if (b.opacityJitter > 0) a *= 1f - b.opacityJitter * rnd.nextFloat()
            if (b.mode == BrushMode.Smudge) a = b.flow * (0.3f + 0.7f * curve(pressure))
            var ang = b.angle + if (b.followDirection) Math.toDegrees(direction.toDouble()).toFloat() else 0f
            if (b.angleJitter > 0) ang += (rnd.nextFloat() - 0.5f) * 360f * b.angleJitter
            var px = x; var py = y
            if (b.scatter > 0) {
                val r = d * b.scatter * rnd.nextFloat(); val t = rnd.nextFloat() * 2 * PI.toFloat()
                px += cos(t) * r; py += sin(t) * r
            }
            var c = color
            if (b.hueJitter > 0) {
                val hsv = FloatArray(3); Color.colorToHSV(c, hsv)
                hsv[0] = (hsv[0] + (rnd.nextFloat() - 0.5f) * 360f * b.hueJitter + 360f) % 360f
                c = Color.HSVToColor(Color.alpha(c), hsv)
            }
            if (b.wetMix > 0 && b.mode == BrushMode.Paint) c = mixWithCanvas(c, px, py, b.wetMix)
            val pt = FloatArray(2)
            for ((i, m) in transforms.withIndex()) {
                pt[0] = px; pt[1] = py; m.mapPoints(pt)
                val angle = if (mirror[i]) -ang + mirrorAngle(m) else ang + rotationOf(m)
                when (b.mode) {
                    BrushMode.Smudge -> smudgeDab(pt[0], pt[1], d, a, i)
                    else -> paintDab(pt[0], pt[1], d, a, angle, c)
                }
            }
        }
    }

    private fun rotationOf(m: Matrix): Float { val v = FloatArray(9); m.getValues(v); return Math.toDegrees(atan2(v[3], v[0]).toDouble()).toFloat() }
    private fun mirrorAngle(m: Matrix): Float { val v = FloatArray(9); m.getValues(v); return Math.toDegrees(atan2(v[3], v[0]).toDouble()).toFloat() + 180f }

    private fun mixWithCanvas(c: Int, x: Float, y: Float, amount: Float): Int {
        val l = layer ?: return c
        val ix = x.toInt(); val iy = y.toInt()
        if (ix !in 0 until doc.width || iy !in 0 until doc.height) return c
        val t = l.tiles[tileKey(ix / TILE, iy / TILE)] ?: return c
        val s = t.getPixel(ix % TILE, iy % TILE)
        if (Color.alpha(s) < 16) return c
        val f = amount * Color.alpha(s) / 255f
        fun mix(a: Int, b: Int) = (a + (b - a) * f).toInt()
        return Color.argb(Color.alpha(c), mix(Color.red(c), Color.red(s)), mix(Color.green(c), Color.green(s)), mix(Color.blue(c), Color.blue(s)))
    }

    private fun grainShader(): BitmapShader? {
        if (brush.grain <= 0f) return null
        val bucket = (brush.grain * 10).toInt().coerceIn(1, 10)
        return grainShaders.getOrPut(bucket) {
            val g = TipFactory.grain(); val n = g.width
            val px = IntArray(n * n); g.getPixels(px, 0, n, 0, 0, n, n)
            val amt = bucket / 10f
            for (i in px.indices) {
                val v = Color.alpha(px[i]) / 255f
                px[i] = Color.argb(((1f - amt * (1f - v)) * 255).toInt().coerceIn(0, 255), 0, 0, 0)
            }
            BitmapShader(Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    private fun forTiles(x: Float, y: Float, r: Float, f: (Long, Int, Int) -> Unit) {
        val x0 = floor((x - r) / TILE).toInt().coerceAtLeast(0); val x1 = floor((x + r) / TILE).toInt().coerceAtMost(doc.tilesX - 1)
        val y0 = floor((y - r) / TILE).toInt().coerceAtLeast(0); val y1 = floor((y + r) / TILE).toInt().coerceAtMost(doc.tilesY - 1)
        for (ty in y0..y1) for (tx in x0..x1) f(tileKey(tx, ty), tx * TILE, ty * TILE)
    }

    private fun paintDab(x: Float, y: Float, d: Float, alpha: Float, angle: Float, c: Int) {
        val buf = buffer ?: return
        val tip = TipFactory.tip(brush)
        val res = TipFactory.RES.toFloat()
        val sx = d / res; val sy = d * brush.roundness.coerceIn(0.05f, 1f) / res
        val shader = grainShader()
        dabPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt().coerceAtLeast(1)
        if (shader != null) {
            dabPaint.shader = shader
            if (c != filterColor) { filterColor = c; dabFilter = PorterDuffColorFilter(c or 0xFF000000.toInt(), PorterDuff.Mode.SRC_IN) }
            dabPaint.colorFilter = dabFilter
        } else {
            dabPaint.shader = null; dabPaint.colorFilter = null
            dabPaint.color = c or 0xFF000000.toInt()
            dabPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt().coerceAtLeast(1)
        }
        forTiles(x, y, d * 0.75f) { k, ox, oy ->
            val canvas = buf.canvas(k)
            dabMatrix.reset()
            dabMatrix.postTranslate(-res / 2, -res / 2)
            dabMatrix.postScale(sx, sy)
            dabMatrix.postRotate(angle)
            dabMatrix.postTranslate(x - ox, y - oy)
            if (shader != null) {
                dabMatrix.invert(inv)
                localM.setScale(brush.grainScale * 2f, brush.grainScale * 2f); localM.postTranslate(-ox.toFloat(), -oy.toFloat()); localM.postConcat(inv)
                shader.setLocalMatrix(localM)
            }
            canvas.drawBitmap(tip, dabMatrix, dabPaint)
            compositor.invalidate(k)
        }
    }

    private val scaledTips = HashMap<Int, Bitmap>()
    /** Paint picked up by the finger (premultiplied RGBA floats), one per symmetry copy. */
    private val carries = HashMap<Int, FloatArray>()
    private var carryD = 0
    private var tipAlpha = FloatArray(0)
    private var region = IntArray(0)

    private fun editable(k: Long): Bitmap {
        val l = layer!!
        if (k !in smudgeOwned) {
            val orig = l.tiles[k]
            smudgeBefore[k] = orig
            val copy = orig?.copy(Bitmap.Config.ARGB_8888, true) ?: newTile()
            l.tiles[k] = copy; smudgeOwned += k
        }
        return l.tiles[k]!!
    }

    /**
     * Real smudge, computed per pixel in floating point (no rounding drift, so paint never fades):
     * canvas = canvas + (carried − canvas)·tip·strength, then the carried paint picks up the canvas.
     * Works in premultiplied space so colour drags cleanly into empty areas.
     */
    private fun smudgeDab(x: Float, y: Float, diameter: Float, alpha: Float, idx: Int) {
        val d = ceil(diameter).toInt().coerceIn(2, 320)
        if (d != carryD) {
            carries.clear(); carryD = d
            val tip = Bitmap.createScaledBitmap(TipFactory.tip(brush), d, d, true)
            val px = IntArray(d * d); tip.getPixels(px, 0, d, 0, 0, d, d)
            tipAlpha = FloatArray(d * d) { (px[it] ushr 24) / 255f }
            region = IntArray(d * d)
        }
        val left = kotlin.math.round(x - d / 2f).toInt(); val top = kotlin.math.round(y - d / 2f).toInt()
        // Gather the canvas under the dab (unpremultiplied ARGB from tiles).
        val reg = region
        java.util.Arrays.fill(reg, 0)
        val l = layer!!
        forTiles(x, y, d / 2f + 1) { k, ox, oy ->
            val t = l.tiles[k] ?: return@forTiles
            val x0 = maxOf(left, ox); val y0 = maxOf(top, oy); val x1 = minOf(left + d, ox + TILE); val y1 = minOf(top + d, oy + TILE)
            if (x1 > x0 && y1 > y0) t.getPixels(reg, (y0 - top) * d + (x0 - left), d, x0 - ox, y0 - oy, x1 - x0, y1 - y0)
        }
        val carry = carries[idx]
        if (carry == null) {
            carries[idx] = FloatArray(d * d * 4).also { c -> for (i in 0 until d * d) premul(reg[i], c, i * 4) }
            return
        }
        val a = (brush.smudgeStrength * alpha).coerceIn(0.02f, 1f)
        val keep = brush.smudgeStrength.coerceIn(0f, 0.98f)
        val out = IntArray(d * d)
        val cur = FloatArray(4)
        for (i in 0 until d * d) {
            premul(reg[i], cur, 0)
            val t = tipAlpha[i] * a
            val ci = i * 4
            val na = cur[0] + (carry[ci] - cur[0]) * t
            val nr = cur[1] + (carry[ci + 1] - cur[1]) * t
            val ng = cur[2] + (carry[ci + 2] - cur[2]) * t
            val nb = cur[3] + (carry[ci + 3] - cur[3]) * t
            out[i] = unpremul(na, nr, ng, nb)
            // Pick up what was under the finger.
            carry[ci] = carry[ci] * keep + cur[0] * (1 - keep)
            carry[ci + 1] = carry[ci + 1] * keep + cur[1] * (1 - keep)
            carry[ci + 2] = carry[ci + 2] * keep + cur[2] * (1 - keep)
            carry[ci + 3] = carry[ci + 3] * keep + cur[3] * (1 - keep)
        }
        // Write back, respecting the selection mask.
        forTiles(x, y, d / 2f + 1) { k, ox, oy ->
            val mask = selection.maskFor(k)
            if (mask === Selection.EMPTY) return@forTiles
            val x0 = maxOf(left, ox, 0); val y0 = maxOf(top, oy, 0); val x1 = minOf(left + d, ox + TILE, doc.width); val y1 = minOf(top + d, oy + TILE, doc.height)
            if (x1 <= x0 || y1 <= y0) return@forTiles
            val t = editable(k)
            val w = x1 - x0; val h = y1 - y0
            val patch = IntArray(w * h)
            val old = if (mask != null) IntArray(w * h).also { t.getPixels(it, 0, w, x0 - ox, y0 - oy, w, h) } else null
            val m = if (mask != null) IntArray(w * h).also { mask.getPixels(it, 0, w, x0 - ox, y0 - oy, w, h) } else null
            for (yy in 0 until h) for (xx in 0 until w) {
                val v = out[(y0 - top + yy) * d + (x0 - left + xx)]
                patch[yy * w + xx] = if (m == null) v else lerpColor(old!![yy * w + xx], v, (m[yy * w + xx] ushr 24) / 255f)
            }
            t.setPixels(patch, 0, w, x0 - ox, y0 - oy, w, h)
            l.touched(k); compositor.invalidate(k)
        }
    }

    private fun premul(c: Int, o: FloatArray, at: Int) {
        val a = (c ushr 24).toFloat(); val f = a / 255f
        o[at] = a; o[at + 1] = ((c shr 16) and 255) * f; o[at + 2] = ((c shr 8) and 255) * f; o[at + 3] = (c and 255) * f
    }

    private fun unpremul(a: Float, r: Float, g: Float, b: Float): Int {
        val ai = kotlin.math.round(a).toInt().coerceIn(0, 255)
        if (ai == 0) return 0
        val inv = 255f / a
        return (ai shl 24) or (kotlin.math.round(r * inv).toInt().coerceIn(0, 255) shl 16) or
            (kotlin.math.round(g * inv).toInt().coerceIn(0, 255) shl 8) or kotlin.math.round(b * inv).toInt().coerceIn(0, 255)
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val pa = FloatArray(4); val pb = FloatArray(4); premul(a, pa, 0); premul(b, pb, 0)
        return unpremul(pa[0] + (pb[0] - pa[0]) * t, pa[1] + (pb[1] - pa[1]) * t, pa[2] + (pb[2] - pa[2]) * t, pa[3] + (pb[3] - pa[3]) * t)
    }

    // ---- Shapes: line, curve, polygon, rectangle, ellipse drawn with the current brush ----

    fun beginShape(target: Layer, settings: BrushSettings, argb: Int, symmetry: Symmetry) {
        brush = if (settings.mode == BrushMode.Smudge) settings.copy(mode = BrushMode.Paint) else settings
        color = argb; layer = target
        transforms = symmetry.transforms()
        mirror = transforms.map { val v = FloatArray(9); it.getValues(v); v[0] * v[4] - v[1] * v[3] < 0 }
        points.clear(); shape = null; started = false; residual = 0f
        buffer = StrokeBuffer(target, brush.mode, brush.opacity, brush.glow).also { compositor.stroke = it }
    }

    /** Re-renders the pending shape from scratch (called live while the user adjusts it). */
    fun renderPath(pts: List<FloatArray>, closed: Boolean, fill: Boolean) {
        val buf = buffer ?: return
        for (k in buf.tiles.keys) compositor.invalidate(k)
        buf.clear()
        if (pts.isEmpty()) return
        if (fill && pts.size >= 3) {
            val path = android.graphics.Path().apply { moveTo(pts[0][0], pts[0][1]); for (i in 1 until pts.size) lineTo(pts[i][0], pts[i][1]); close() }
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = this@StrokeEngine.color or 0xFF000000.toInt() }
            for (m in transforms) {
                val tp = android.graphics.Path(path); tp.transform(m)
                val b = android.graphics.RectF(); tp.computeBounds(b, true)
                val x0 = floor(b.left / TILE).toInt().coerceAtLeast(0); val x1 = floor(b.right / TILE).toInt().coerceAtMost(doc.tilesX - 1)
                val y0 = floor(b.top / TILE).toInt().coerceAtLeast(0); val y1 = floor(b.bottom / TILE).toInt().coerceAtMost(doc.tilesY - 1)
                for (ty in y0..y1) for (tx in x0..x1) {
                    val k = tileKey(tx, ty); val c = buf.canvas(k)
                    c.save(); c.translate(-(tx * TILE).toFloat(), -(ty * TILE).toFloat()); c.drawPath(tp, p); c.restore()
                    compositor.invalidate(k)
                }
            }
        }
        started = false; residual = 0f; direction = 0f
        val seq = if (closed && pts.size > 2) pts + listOf(pts[0]) else pts
        for (pt in seq) emitTo(pt[0], pt[1], 0.85f, 0f)
    }

    /** Finishes the stroke and returns the undoable edit. */
    fun end(): Edit? {
        val l = layer ?: return null
        layer = null
        if (brush.mode == BrushMode.Smudge) {
            carries.clear(); carryD = 0
            if (smudgeBefore.isEmpty()) return null
            val after = smudgeOwned.associateWith { l.tiles[it] }
            return TileEdit(brush.name, l, HashMap(smudgeBefore), after)
        }
        val buf = buffer ?: return null
        buffer = null
        compositor.stroke = null
        if (buf.tiles.isEmpty()) return null
        val before = HashMap<Long, Bitmap?>(); val after = HashMap<Long, Bitmap?>()
        val p = Paint().apply {
            alpha = (buf.opacity * 255).toInt()
            blendMode = when {
                buf.mode == BrushMode.Erase -> BlendMode.DST_OUT
                l.alphaLock -> BlendMode.SRC_ATOP
                buf.glow -> BlendMode.PLUS
                else -> BlendMode.SRC_OVER
            }
        }
        for ((k, st) in buf.tiles) {
            val old = l.tiles[k]
            if (old == null && (buf.mode == BrushMode.Erase || l.alphaLock)) { st.recycle(); continue }
            val n = old?.copy(Bitmap.Config.ARGB_8888, true) ?: newTile()
            val mask = selection.maskFor(k)
            if (mask != null) Canvas(st).drawBitmap(mask, 0f, 0f, Paint().apply { blendMode = BlendMode.DST_IN })
            Canvas(n).drawBitmap(st, 0f, 0f, p)
            st.recycle()
            before[k] = old; after[k] = n
            l.set(k, n)
        }
        return if (after.isEmpty()) null else TileEdit(brush.name, l, before, after)
    }

    fun cancel() {
        val l = layer ?: return
        layer = null
        buffer?.tiles?.keys?.forEach { compositor.invalidate(it) }
        buffer = null; compositor.stroke = null
        for ((k, b) in smudgeBefore) l.set(k, b)
        smudgeBefore.clear(); smudgeOwned.clear()
    }

    // ---- QuickShape: hold at the end of a stroke to snap it to a clean shape ----

    fun snapToShape(): Boolean {
        if (buffer == null || points.size < 4) return false
        val s = QuickShape.detect(points) ?: return false
        shape = s; renderShape(); return true
    }

    private fun renderShape() {
        val buf = buffer ?: return
        val s = shape ?: return
        for (k in buf.tiles.keys) compositor.invalidate(k)
        buf.clear()
        val pts = s.outline()
        started = false; residual = 0f
        val pr = points.map { it.pressure }.average().toFloat().coerceIn(0.3f, 1f)
        for (pt in pts) emitTo(pt[0], pt[1], pr, 0f)
    }
}

/** Recognized shapes for QuickShape. */
sealed class QuickShape {
    abstract fun outline(): List<FloatArray>
    abstract fun adjust(x: Float, y: Float): QuickShape

    data class Line(val x0: Float, val y0: Float, val x1: Float, val y1: Float) : QuickShape() {
        override fun outline() = listOf(floatArrayOf(x0, y0), floatArrayOf(x1, y1))
        override fun adjust(x: Float, y: Float) = copy(x1 = x, y1 = y)
    }

    data class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : QuickShape() {
        override fun outline() = (0..96).map { i ->
            val t = 2 * PI * i / 96
            floatArrayOf(cx + (cos(t) * rx).toFloat(), cy + (sin(t) * ry).toFloat())
        }
        override fun adjust(x: Float, y: Float) = copy(rx = max(2f, abs(x - cx)), ry = max(2f, abs(y - cy)))
    }

    data class Rect(val l: Float, val t: Float, val r: Float, val b: Float) : QuickShape() {
        override fun outline() = listOf(floatArrayOf(l, t), floatArrayOf(r, t), floatArrayOf(r, b), floatArrayOf(l, b), floatArrayOf(l, t))
        override fun adjust(x: Float, y: Float) = copy(r = x, b = y)
    }

    companion object {
        fun detect(pts: List<StrokePoint>): QuickShape? {
            val s = pts.first(); val e = pts.last()
            var len = 0f
            for (i in 1 until pts.size) len += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
            if (len < 12f) return null
            val chord = hypot(e.x - s.x, e.y - s.y)
            if (chord / len > 0.92f) return Line(s.x, s.y, e.x, e.y)
            val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
            val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
            val w = maxX - minX; val h = maxY - minY
            if (chord > 0.25f * max(w, h)) return null
            val tol = 0.12f * max(w, h)
            val nearEdge = pts.count { p ->
                min(min(abs(p.x - minX), abs(p.x - maxX)), min(abs(p.y - minY), abs(p.y - maxY))) < tol
            }
            return if (nearEdge > pts.size * 0.85f) Rect(minX, minY, maxX, maxY)
            else Ellipse((minX + maxX) / 2, (minY + maxY) / 2, w / 2, h / 2)
        }
    }
}
