package com.silpa.engine

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private fun Bitmap.isBlank(): Boolean {
    val px = IntArray(width * height); getPixels(px, 0, width, 0, 0, width, height)
    return px.none { it ushr 24 != 0 }
}

object Ops {

    /** Replaces a layer's pixels with [full] (doc-sized), respecting the selection. */
    fun replaceContent(label: String, layer: Layer, full: Bitmap, selection: Selection, doc: Document): TileEdit? {
        val before = HashMap<Long, Bitmap?>(); val after = HashMap<Long, Bitmap?>()
        for (k in doc.allTileKeys()) {
            val mask = selection.maskFor(k)
            if (mask === Selection.EMPTY) continue
            val old = layer.tiles[k]
            val t = newTile()
            Canvas(t).drawBitmap(full, -(keyX(k) * TILE).toFloat(), -(keyY(k) * TILE).toFloat(), null)
            val merged = maskedMerge(old, t, mask)
            val n = if (merged.isBlank()) null else merged
            if (old == null && n == null) continue
            before[k] = old; after[k] = n
        }
        if (after.isEmpty()) return null
        for ((k, b) in after) layer.set(k, b)
        return TileEdit(label, layer, before, after)
    }

    /** Applies [draw] to every tile of the layer (or all doc tiles when [allTiles]), masked by selection. */
    fun editTiles(label: String, layer: Layer, doc: Document, selection: Selection, allTiles: Boolean, draw: (Canvas, Long) -> Unit): TileEdit? {
        val before = HashMap<Long, Bitmap?>(); val after = HashMap<Long, Bitmap?>()
        val keys = if (allTiles) doc.allTileKeys() else layer.tiles.keys.toList()
        for (k in keys) {
            val mask = selection.maskFor(k)
            if (mask === Selection.EMPTY) continue
            val old = layer.tiles[k]
            val t = old?.copy(Bitmap.Config.ARGB_8888, true) ?: newTile()
            val c = Canvas(t); c.translate(-(keyX(k) * TILE).toFloat(), -(keyY(k) * TILE).toFloat())
            draw(c, k)
            val merged = maskedMerge(old, t, mask)
            val n = if (merged.isBlank()) null else merged
            if (old == null && n == null) continue
            before[k] = old; after[k] = n
        }
        if (after.isEmpty()) return null
        for ((k, b) in after) layer.set(k, b)
        return TileEdit(label, layer, before, after)
    }

    // ---------- Fill ----------

    /** Scanline flood fill. Returns the filled region (grown by [grow] px to close small gaps/halo). */
    fun floodMask(px: IntArray, w: Int, h: Int, sx: Int, sy: Int, tolerance: Int, grow: Int): BooleanArray {
        val mask = BooleanArray(w * h)
        if (sx !in 0 until w || sy !in 0 until h) return mask
        val target = px[sy * w + sx]
        fun match(c: Int): Boolean {
            if (abs((c ushr 24) - (target ushr 24)) > tolerance) return false
            if ((target ushr 24) < 8 && (c ushr 24) < 8) return true
            return abs(((c shr 16) and 255) - ((target shr 16) and 255)) <= tolerance &&
                abs(((c shr 8) and 255) - ((target shr 8) and 255)) <= tolerance &&
                abs((c and 255) - (target and 255)) <= tolerance
        }
        val stack = IntArray(w * h / 4 + 1024).let { ArrayList<Int>(1024) }
        stack.add(sy * w + sx)
        while (stack.isNotEmpty()) {
            val idx = stack.removeAt(stack.lastIndex)
            val y = idx / w; var x = idx % w
            while (x > 0 && !mask[y * w + x - 1] && match(px[y * w + x - 1])) x--
            var up = false; var down = false
            while (x < w && !mask[y * w + x] && match(px[y * w + x])) {
                mask[y * w + x] = true
                if (y > 0) { val m = !mask[(y - 1) * w + x] && match(px[(y - 1) * w + x]); if (m && !up) { stack.add((y - 1) * w + x); up = true } else if (!m) up = false }
                if (y < h - 1) { val m = !mask[(y + 1) * w + x] && match(px[(y + 1) * w + x]); if (m && !down) { stack.add((y + 1) * w + x); down = true } else if (!m) down = false }
                x++
            }
        }
        repeat(grow) { dilate(mask, w, h) }
        return mask
    }

    private fun dilate(m: BooleanArray, w: Int, h: Int) {
        val src = m.copyOf()
        for (y in 0 until h) for (x in 0 until w) {
            if (src[y * w + x]) continue
            if ((x > 0 && src[y * w + x - 1]) || (x < w - 1 && src[y * w + x + 1]) || (y > 0 && src[(y - 1) * w + x]) || (y < h - 1 && src[(y + 1) * w + x])) m[y * w + x] = true
        }
    }

    fun fillMask(label: String, layer: Layer, doc: Document, selection: Selection, mask: BooleanArray, color: Int): TileEdit? {
        val w = doc.width; val h = doc.height
        val before = HashMap<Long, Bitmap?>(); val after = HashMap<Long, Bitmap?>()
        val p = Paint().apply { blendMode = if (layer.alphaLock) BlendMode.SRC_ATOP else BlendMode.SRC_OVER }
        val px = IntArray(TILE * TILE)
        for (k in doc.allTileKeys()) {
            val ox = keyX(k) * TILE; val oy = keyY(k) * TILE
            var any = false
            java.util.Arrays.fill(px, 0)
            for (y in 0 until TILE) {
                val gy = oy + y; if (gy >= h) break
                for (x in 0 until TILE) {
                    val gx = ox + x; if (gx >= w) break
                    if (mask[gy * w + gx]) { px[y * TILE + x] = color; any = true }
                }
            }
            if (!any) continue
            val sel = selection.maskFor(k); if (sel === Selection.EMPTY) continue
            val old = layer.tiles[k]
            val t = old?.copy(Bitmap.Config.ARGB_8888, true) ?: newTile()
            Canvas(t).drawBitmap(Bitmap.createBitmap(px, TILE, TILE, Bitmap.Config.ARGB_8888), 0f, 0f, p)
            val n = maskedMerge(old, t, sel)
            before[k] = old; after[k] = n; layer.set(k, n)
        }
        return if (after.isEmpty()) null else TileEdit(label, layer, before, after)
    }

    fun gradient(layer: Layer, doc: Document, selection: Selection, x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, radial: Boolean): TileEdit? {
        val shader = if (radial) RadialGradient(x0, y0, max(1f, hypot(x1 - x0, y1 - y0)), c0, c1, Shader.TileMode.CLAMP)
        else LinearGradient(x0, y0, x1, y1, c0, c1, Shader.TileMode.CLAMP)
        val p = Paint().apply { this.shader = shader; if (layer.alphaLock) blendMode = BlendMode.SRC_ATOP }
        return editTiles("Gradient", layer, doc, selection, allTiles = true) { c, _ -> c.drawPaint(p) }
    }

    // ---------- Merge / flatten ----------

    fun mergeDown(doc: Document, index: Int): Edit? {
        if (index <= 0) return null
        val upper = doc.layers[index]; val lower = doc.layers[index - 1]
        val before = doc.snapshot()
        val tb = HashMap<Long, Bitmap?>(); val ta = HashMap<Long, Bitmap?>()
        val p = Paint().apply { alpha = (upper.opacity * 255).toInt(); blendMode = if (upper.clipping) BlendMode.SRC_ATOP else upper.blend.mode }
        for ((k, ut) in upper.tiles) {
            if (!upper.visible) break
            val old = lower.tiles[k]
            val n = old?.copy(Bitmap.Config.ARGB_8888, true) ?: newTile()
            Canvas(n).drawBitmap(ut, 0f, 0f, p)
            tb[k] = old; ta[k] = n; lower.set(k, n)
        }
        doc.layers.removeAt(index); doc.activeIndex = index - 1; doc.structureChanged()
        val te = TileEdit("Merge", lower, tb, ta)
        val se = StructureEdit("Merge", doc, before, doc.snapshot())
        return CompoundEdit("Merge down", listOf(te, se))
    }

    // ---------- Transform ----------

    class Floating(val bitmap: Bitmap, val origin: RectF, val remainder: HashMap<Long, Bitmap>, val original: Map<Long, Bitmap>)

    fun lift(layer: Layer, selection: Selection, doc: Document): Floating? {
        val full = layer.toBitmap()
        val content: Bitmap; val rest: Bitmap
        if (selection.active) {
            content = full.copy(Bitmap.Config.ARGB_8888, true); rest = full
            val cm = Canvas(content); val cr = Canvas(rest)
            val dIn = Paint().apply { blendMode = BlendMode.DST_IN }; val dOut = Paint().apply { blendMode = BlendMode.DST_OUT }
            for (k in doc.allTileKeys()) {
                val ox = (keyX(k) * TILE).toFloat(); val oy = (keyY(k) * TILE).toFloat()
                val m = selection.tiles[k]
                if (m == null) { cm.save(); cm.clipRect(ox, oy, ox + TILE, oy + TILE); cm.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR); cm.restore() }
                else { cm.drawBitmap(m, ox, oy, dIn); cr.drawBitmap(m, ox, oy, dOut) }
            }
        } else { content = full; rest = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888) }
        val tmp = Layer("tmp", "tmp", doc.width, doc.height)
        tmp.tiles.putAll(splitIntoTiles(content, doc.width, doc.height))
        val b = tmp.contentBounds() ?: return null
        val crop = Bitmap.createBitmap(content, b.left, b.top, b.width(), b.height())
        return Floating(crop, RectF(b), splitIntoTiles(rest, doc.width, doc.height), HashMap(layer.tiles))
    }

    fun commitTransform(layer: Layer, f: Floating, m: Matrix, doc: Document): TileEdit {
        val full = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(full)
        for ((k, t) in f.remainder) c.drawBitmap(t, (keyX(k) * TILE).toFloat(), (keyY(k) * TILE).toFloat(), null)
        c.drawBitmap(f.bitmap, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        val n = splitIntoTiles(full, doc.width, doc.height)
        val keys = f.original.keys + n.keys
        val before = keys.associateWith { f.original[it] }; val after = keys.associateWith { n[it] }
        for ((k, b) in after) layer.set(k, b)
        return TileEdit("Transform", layer, before, after)
    }

    // ---------- Text ----------

    fun renderText(text: String, sizePx: Float, color: Int, family: String, bold: Boolean, italic: Boolean, align: Layout.Alignment, maxWidth: Int, outline: Float = 0f): Bitmap {
        val style = when { bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL }
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = sizePx; typeface = Typeface.create(family, style) }
        val width = min(maxWidth, max(1, text.lines().maxOf { tp.measureText(it).roundToInt() } + 4))
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, width).setAlignment(align).build()
        val pad = outline.roundToInt() + 2
        val bmp = Bitmap.createBitmap(width + pad * 2, layout.height + pad * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.translate(pad.toFloat(), pad.toFloat())
        if (outline > 0) {
            val stroke = TextPaint(tp).apply { this.style = Paint.Style.STROKE; strokeWidth = outline * 2; this.color = Color.WHITE; strokeJoin = Paint.Join.ROUND }
            StaticLayout.Builder.obtain(text, 0, text.length, stroke, width).setAlignment(align).build().draw(c)
        }
        layout.draw(c)
        return bmp
    }

    /** Places a bitmap centered (or at [at]) into a fresh layer's tiles. */
    fun placeBitmap(layer: Layer, bmp: Bitmap, doc: Document, at: RectF? = null) {
        val full = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888)
        val dst = at ?: run {
            val s = min(1f, min(doc.width * 0.9f / bmp.width, doc.height * 0.9f / bmp.height))
            val w = bmp.width * s; val h = bmp.height * s
            RectF((doc.width - w) / 2, (doc.height - h) / 2, (doc.width + w) / 2, (doc.height + h) / 2)
        }
        Canvas(full).drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        layer.tiles.clear(); layer.tiles.putAll(splitIntoTiles(full, doc.width, doc.height))
    }

    // ---------- Comic tools ----------

    val panelLayouts: Map<String, List<RectF>> = linkedMapOf(
        "Single" to listOf(RectF(0f, 0f, 1f, 1f)),
        "2 Tier" to listOf(RectF(0f, 0f, 1f, .5f), RectF(0f, .5f, 1f, 1f)),
        "3 Tier" to listOf(RectF(0f, 0f, 1f, .333f), RectF(0f, .333f, 1f, .666f), RectF(0f, .666f, 1f, 1f)),
        "4-Koma" to listOf(RectF(0f, 0f, 1f, .25f), RectF(0f, .25f, 1f, .5f), RectF(0f, .5f, 1f, .75f), RectF(0f, .75f, 1f, 1f)),
        "Grid 2×2" to listOf(RectF(0f, 0f, .5f, .5f), RectF(.5f, 0f, 1f, .5f), RectF(0f, .5f, .5f, 1f), RectF(.5f, .5f, 1f, 1f)),
        "Grid 2×3" to (0 until 3).flatMap { r -> listOf(RectF(0f, r / 3f, .5f, (r + 1) / 3f), RectF(.5f, r / 3f, 1f, (r + 1) / 3f)) },
        "Splash + 2" to listOf(RectF(0f, 0f, 1f, .6f), RectF(0f, .6f, .5f, 1f), RectF(.5f, .6f, 1f, 1f)),
        "Manga 5" to listOf(RectF(0f, 0f, .6f, .3f), RectF(.6f, 0f, 1f, .3f), RectF(0f, .3f, 1f, .62f), RectF(0f, .62f, .4f, 1f), RectF(.4f, .62f, 1f, 1f)),
    )

    fun panels(w: Int, h: Int, layout: List<RectF>, margin: Float, gutter: Float, border: Float, whiteGutters: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val aw = w - margin * 2; val ah = h - margin * 2
        val rects = layout.map { r ->
            RectF(margin + r.left * aw + gutter / 2, margin + r.top * ah + gutter / 2, margin + r.right * aw - gutter / 2, margin + r.bottom * ah - gutter / 2)
        }
        if (whiteGutters) {
            c.drawColor(Color.WHITE)
            val clear = Paint().apply { blendMode = BlendMode.CLEAR }
            rects.forEach { c.drawRect(it, clear) }
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = border; color = Color.BLACK }
        rects.forEach { c.drawRect(it, p) }
        return bmp
    }

    fun speedLines(w: Int, h: Int, angleDeg: Float, count: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val rnd = Random(7)
        c.rotate(angleDeg, w / 2f, h / 2f)
        val diag = hypot(w.toFloat(), h.toFloat())
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        repeat(count) {
            val y = (rnd.nextFloat() - 0.5f) * diag + h / 2f
            val len = diag * (0.2f + rnd.nextFloat() * 0.6f)
            val x = (w - diag) / 2 + rnd.nextFloat() * (diag - len)
            val th = 1f + rnd.nextFloat() * 4f
            c.drawPath(Path().apply { moveTo(x, y); lineTo(x + len / 2, y - th); lineTo(x + len, y); lineTo(x + len / 2, y + th); close() }, p)
        }
        return bmp
    }

    fun focusLines(w: Int, h: Int, cx: Float, cy: Float, count: Int, inner: Float, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val rnd = Random(11)
        val outer = hypot(max(cx, w - cx), max(cy, h - cy))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        repeat(count) {
            val a = rnd.nextFloat() * 2 * PI
            val r0 = outer * inner * (0.85f + rnd.nextFloat() * 0.3f)
            val half = (0.002f + rnd.nextFloat() * 0.006f) * PI
            val path = Path().apply {
                moveTo(cx + (cos(a) * r0).toFloat(), cy + (sin(a) * r0).toFloat())
                lineTo(cx + (cos(a - half) * outer).toFloat(), cy + (sin(a - half) * outer).toFloat())
                lineTo(cx + (cos(a + half) * outer).toFloat(), cy + (sin(a + half) * outer).toFloat()); close()
            }
            c.drawPath(path, p)
        }
        return bmp
    }

    fun screentone(w: Int, h: Int, cell: Float, dot: Float, angleDeg: Float, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.rotate(angleDeg, w / 2f, h / 2f)
        val diag = hypot(w.toFloat(), h.toFloat())
        var y = (h - diag) / 2
        while (y < (h + diag) / 2) {
            var x = (w - diag) / 2
            while (x < (w + diag) / 2) { c.drawCircle(x, y, dot / 2, p); x += cell }
            y += cell
        }
        return bmp
    }

    fun balloon(w: Int, h: Int, rect: RectF, tailX: Float, tailY: Float, text: String, textSize: Float, thought: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = max(2f, rect.width() / 80f) }
        val shape = Path()
        if (thought) {
            val n = 12
            for (i in 0 until n) {
                val a = 2 * PI * i / n
                val px = rect.centerX() + (cos(a) * rect.width() / 2.3).toFloat(); val py = rect.centerY() + (sin(a) * rect.height() / 2.3).toFloat()
                shape.addCircle(px, py, min(rect.width(), rect.height()) / 5f, Path.Direction.CW)
            }
            shape.addOval(RectF(rect).apply { inset(rect.width() * .08f, rect.height() * .08f) }, Path.Direction.CW)
            var bx = rect.centerX(); var by = rect.bottom
            for (i in 1..3) {
                val t = i / 4f; val r = min(rect.width(), rect.height()) / (8f + i * 3)
                bx = rect.centerX() + (tailX - rect.centerX()) * t; by = rect.centerY() + (tailY - rect.centerY()) * t + rect.height() / 2 * (1 - t)
                shape.addCircle(bx, by, r, Path.Direction.CW)
            }
        } else {
            shape.addOval(rect, Path.Direction.CW)
            val dx = tailX - rect.centerX(); val dy = tailY - rect.centerY()
            val ang = kotlin.math.atan2(dy, dx); val baseW = min(rect.width(), rect.height()) * 0.12f
            val bx = rect.centerX() + cos(ang) * rect.width() * 0.4f; val by = rect.centerY() + sin(ang) * rect.height() * 0.4f
            shape.op(Path().apply {
                moveTo(bx + cos(ang + PI / 2).toFloat() * baseW, by + sin(ang + PI / 2).toFloat() * baseW)
                lineTo(tailX, tailY)
                lineTo(bx + cos(ang - PI / 2).toFloat() * baseW, by + sin(ang - PI / 2).toFloat() * baseW); close()
            }, Path.Op.UNION)
        }
        shape.op(shape, Path.Op.UNION)
        c.drawPath(shape, fill); c.drawPath(shape, line)
        if (text.isNotBlank()) {
            val t = renderText(text, textSize, Color.BLACK, "sans-serif", true, false, Layout.Alignment.ALIGN_CENTER, (rect.width() * 0.75f).toInt())
            c.drawBitmap(t, rect.centerX() - t.width / 2f, rect.centerY() - t.height / 2f, null)
        }
        return bmp
    }
}

// ---------- Filters ----------

enum class FilterKind(val label: String, val params: List<Pair<String, ClosedFloatingPointRange<Float>>>, val defaults: List<Float>) {
    GaussianBlur("Gaussian Blur", listOf("Radius" to 1f..100f), listOf(8f)),
    MotionBlur("Motion Blur", listOf("Distance" to 2f..120f, "Angle" to 0f..180f), listOf(30f, 0f)),
    Sharpen("Sharpen", listOf("Amount" to 0f..3f, "Radius" to 1f..10f), listOf(1f, 2f)),
    Noise("Noise", listOf("Amount" to 0f..1f), listOf(0.2f)),
    Glow("Glow / Bloom", listOf("Radius" to 2f..80f, "Strength" to 0f..1f), listOf(20f, 0.6f)),
    ChromaticAberration("Chromatic Aberration", listOf("Shift" to 1f..40f), listOf(6f)),
    Pixelate("Pixelate", listOf("Cell" to 2f..80f), listOf(12f)),
    Posterize("Posterize", listOf("Levels" to 2f..16f), listOf(5f)),
    Halftone("Halftone", listOf("Cell" to 4f..60f), listOf(12f)),
    HueSaturation("Hue / Saturation / Lightness", listOf("Hue" to -180f..180f, "Saturation" to -1f..1f, "Lightness" to -1f..1f), listOf(0f, 0f, 0f)),
    BrightnessContrast("Brightness / Contrast", listOf("Brightness" to -1f..1f, "Contrast" to -1f..1f), listOf(0f, 0f)),
    Invert("Invert", emptyList(), emptyList()),
    Desaturate("Desaturate", emptyList(), emptyList()),
    Sepia("Sepia", emptyList(), emptyList()),
    Threshold("Threshold", listOf("Level" to 0f..255f), listOf(128f)),
    GradientMap("Gradient Map", emptyList(), emptyList()),
}

object Filters {
    fun apply(kind: FilterKind, v: List<Float>, src: Bitmap, c1: Int = Color.rgb(20, 10, 60), c2: Int = Color.rgb(255, 210, 160)): Bitmap {
        val w = src.width; val h = src.height
        return when (kind) {
            FilterKind.HueSaturation -> colorMatrix(src, ColorMatrix().apply {
                val hue = ColorMatrix(); hue.setRotate(0, v[0]); hue.setRotate(1, v[0]); hue.setRotate(2, v[0])
                val sat = ColorMatrix().apply { setSaturation(1f + v[1]) }
                set(hueMatrix(v[0])); postConcat(sat)
                val l = v[2] * 255f
                postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, l, 0f, 1f, 0f, 0f, l, 0f, 0f, 1f, 0f, l, 0f, 0f, 0f, 1f, 0f)))
            })
            FilterKind.BrightnessContrast -> {
                val cst = 1f + v[1]; val br = v[0] * 255f + 128f * (1 - cst)
                colorMatrix(src, ColorMatrix(floatArrayOf(cst, 0f, 0f, 0f, br, 0f, cst, 0f, 0f, br, 0f, 0f, cst, 0f, br, 0f, 0f, 0f, 1f, 0f)))
            }
            FilterKind.Invert -> colorMatrix(src, ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)))
            FilterKind.Desaturate -> colorMatrix(src, ColorMatrix().apply { setSaturation(0f) })
            FilterKind.Sepia -> colorMatrix(src, ColorMatrix(floatArrayOf(.393f, .769f, .189f, 0f, 0f, .349f, .686f, .168f, 0f, 0f, .272f, .534f, .131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
            else -> {
                val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
                val out = when (kind) {
                    FilterKind.GaussianBlur -> blur(px, w, h, v[0])
                    FilterKind.MotionBlur -> motionBlur(px, w, h, v[0], v[1])
                    FilterKind.Sharpen -> {
                        val b = blur(px, w, h, v[1])
                        IntArray(px.size) { i -> mapRgb(px[i]) { ch, s -> (s + (s - ch(b[i])) * v[0]) } }
                    }
                    FilterKind.Noise -> { val r = Random(3); IntArray(px.size) { i -> val n = (r.nextFloat() - 0.5f) * 255f * v[0]; mapRgb(px[i]) { _, s -> s + n } } }
                    FilterKind.Glow -> {
                        val b = blur(px, w, h, v[0])
                        IntArray(px.size) { i -> val a = px[i]; val g = b[i]; val s = v[1]
                            Color.argb(max(a ushr 24, ((g ushr 24) * s).toInt()).coerceIn(0, 255),
                                min(255, Color.red(a) + (Color.red(g) * s).toInt()), min(255, Color.green(a) + (Color.green(g) * s).toInt()), min(255, Color.blue(a) + (Color.blue(g) * s).toInt())) }
                    }
                    FilterKind.ChromaticAberration -> {
                        val d = v[0].toInt()
                        IntArray(px.size) { i -> val x = i % w; val y = i / w
                            val r = px[y * w + (x - d).coerceIn(0, w - 1)]; val b = px[y * w + (x + d).coerceIn(0, w - 1)]; val c = px[i]
                            Color.argb(max(Color.alpha(c), max(Color.alpha(r), Color.alpha(b))), Color.red(r), Color.green(c), Color.blue(b)) }
                    }
                    FilterKind.Pixelate -> {
                        val cs = v[0].toInt().coerceAtLeast(2); val o = IntArray(px.size)
                        for (by in 0 until h step cs) for (bx in 0 until w step cs) {
                            val c = px[min(h - 1, by + cs / 2) * w + min(w - 1, bx + cs / 2)]
                            for (y in by until min(h, by + cs)) for (x in bx until min(w, bx + cs)) o[y * w + x] = c
                        }
                        o
                    }
                    FilterKind.Posterize -> { val lv = v[0].toInt().coerceAtLeast(2) - 1; IntArray(px.size) { i -> mapRgb(px[i]) { _, s -> ((s / 255f * lv).roundToInt() / lv.toFloat()) * 255f } } }
                    FilterKind.Threshold -> IntArray(px.size) { i -> val c = px[i]; val l = Color.red(c) * .3f + Color.green(c) * .59f + Color.blue(c) * .11f
                        val o = if (l >= v[0]) 255 else 0; Color.argb(Color.alpha(c), o, o, o) }
                    FilterKind.GradientMap -> IntArray(px.size) { i -> val c = px[i]; val t = (Color.red(c) * .3f + Color.green(c) * .59f + Color.blue(c) * .11f) / 255f
                        Color.argb(Color.alpha(c), lerp(Color.red(c1), Color.red(c2), t), lerp(Color.green(c1), Color.green(c2), t), lerp(Color.blue(c1), Color.blue(c2), t)) }
                    FilterKind.Halftone -> return halftone(src, v[0])
                    else -> px
                }
                Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
            }
        }
    }

    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).roundToInt().coerceIn(0, 255)

    private inline fun mapRgb(c: Int, f: ((Int) -> Int, Float) -> Float): Int {
        val r = f({ Color.red(it) }, Color.red(c).toFloat()).roundToInt().coerceIn(0, 255)
        val g = f({ Color.green(it) }, Color.green(c).toFloat()).roundToInt().coerceIn(0, 255)
        val b = f({ Color.blue(it) }, Color.blue(c).toFloat()).roundToInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(c), r, g, b)
    }

    private fun hueMatrix(deg: Float): ColorMatrix {
        val a = Math.toRadians(deg.toDouble()); val cosA = cos(a).toFloat(); val sinA = sin(a).toFloat()
        val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
        return ColorMatrix(floatArrayOf(
            lr + cosA * (1 - lr) + sinA * (-lr), lg + cosA * (-lg) + sinA * (-lg), lb + cosA * (-lb) + sinA * (1 - lb), 0f, 0f,
            lr + cosA * (-lr) + sinA * 0.143f, lg + cosA * (1 - lg) + sinA * 0.140f, lb + cosA * (-lb) + sinA * (-0.283f), 0f, 0f,
            lr + cosA * (-lr) + sinA * (-(1 - lr)), lg + cosA * (-lg) + sinA * lg, lb + cosA * (1 - lb) + sinA * lb, 0f, 0f,
            0f, 0f, 0f, 1f, 0f))
    }

    private fun colorMatrix(src: Bitmap, m: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
        return out
    }

    /** Three-pass box blur ≈ Gaussian, done on premultiplied channels to avoid dark halos. */
    fun blur(px: IntArray, w: Int, h: Int, radius: Float): IntArray {
        val n = px.size
        val ch = Array(4) { FloatArray(n) }
        for (i in 0 until n) {
            val c = px[i]; val a = (c ushr 24) / 255f
            ch[0][i] = a; ch[1][i] = ((c shr 16) and 255) * a; ch[2][i] = ((c shr 8) and 255) * a; ch[3][i] = (c and 255) * a
        }
        val r = max(1, (radius / 1.7f).roundToInt())
        val tmp = FloatArray(n)
        for (c in ch) repeat(3) { boxH(c, tmp, w, h, r); boxV(tmp, c, w, h, r) }
        return IntArray(n) { i ->
            val a = ch[0][i]
            if (a <= 0.001f) 0 else Color.argb((a * 255).roundToInt().coerceIn(0, 255),
                (ch[1][i] / a).roundToInt().coerceIn(0, 255), (ch[2][i] / a).roundToInt().coerceIn(0, 255), (ch[3][i] / a).roundToInt().coerceIn(0, 255))
        }
    }

    private fun boxH(s: FloatArray, d: FloatArray, w: Int, h: Int, r: Int) {
        val inv = 1f / (2 * r + 1)
        for (y in 0 until h) {
            val row = y * w; var acc = 0f
            for (x in -r..r) acc += s[row + x.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                d[row + x] = acc * inv
                acc += s[row + (x + r + 1).coerceAtMost(w - 1)] - s[row + (x - r).coerceAtLeast(0)]
            }
        }
    }

    private fun boxV(s: FloatArray, d: FloatArray, w: Int, h: Int, r: Int) {
        val inv = 1f / (2 * r + 1)
        for (x in 0 until w) {
            var acc = 0f
            for (y in -r..r) acc += s[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                d[y * w + x] = acc * inv
                acc += s[(y + r + 1).coerceAtMost(h - 1) * w + x] - s[(y - r).coerceAtLeast(0) * w + x]
            }
        }
    }

    private fun motionBlur(px: IntArray, w: Int, h: Int, dist: Float, angle: Float): IntArray {
        val steps = dist.toInt().coerceIn(2, 60)
        val dx = cos(Math.toRadians(angle.toDouble())).toFloat(); val dy = sin(Math.toRadians(angle.toDouble())).toFloat()
        return IntArray(px.size) { i ->
            val x = i % w; val y = i / w
            var a = 0f; var r = 0f; var g = 0f; var b = 0f
            for (s in 0 until steps) {
                val t = s - steps / 2
                val c = px[(y + (dy * t).roundToInt()).coerceIn(0, h - 1) * w + (x + (dx * t).roundToInt()).coerceIn(0, w - 1)]
                val ca = (c ushr 24) / 255f
                a += ca; r += ((c shr 16) and 255) * ca; g += ((c shr 8) and 255) * ca; b += (c and 255) * ca
            }
            if (a <= 0.001f) 0 else Color.argb((a / steps * 255).roundToInt().coerceIn(0, 255), (r / a).roundToInt().coerceIn(0, 255), (g / a).roundToInt().coerceIn(0, 255), (b / a).roundToInt().coerceIn(0, 255))
        }
    }

    private fun halftone(src: Bitmap, cellF: Float): Bitmap {
        val w = src.width; val h = src.height; val cell = cellF.toInt().coerceAtLeast(4)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (y in 0 until h step cell) for (x in 0 until w step cell) {
            val s = src.getPixel(min(w - 1, x + cell / 2), min(h - 1, y + cell / 2))
            if (Color.alpha(s) < 10) continue
            val l = (Color.red(s) * .3f + Color.green(s) * .59f + Color.blue(s) * .11f) / 255f
            p.color = s or 0xFF000000.toInt()
            c.drawCircle(x + cell / 2f, y + cell / 2f, (1f - l) * cell * 0.72f + 0.5f, p)
        }
        return out
    }
}

fun Rect.toRectF() = RectF(this)
