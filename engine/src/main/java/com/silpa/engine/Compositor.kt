package com.silpa.engine

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter

/** A stroke in progress, kept separate from the layer so stroke opacity and erasing stay clean. */
class StrokeBuffer(val layer: Layer, val mode: BrushMode, var opacity: Float, val glow: Boolean) {
    val tiles = HashMap<Long, Bitmap>()
    fun tile(k: Long) = tiles.getOrPut(k) { newTile() }
}

/**
 * Composites layers per tile and caches the result. Only tiles touched since the last frame are
 * recomposited, which keeps big canvases responsive.
 */
class Compositor(private val doc: Document, private val selection: Selection) {
    private val cache = HashMap<Long, Bitmap>()
    private val dirty = HashSet<Long>()
    private var allDirty = true

    var stroke: StrokeBuffer? = null
    /** While transforming, the lifted layer shows these tiles instead of its own. */
    var liftedLayer: Layer? = null
    var liftedTiles: Map<Long, Bitmap> = emptyMap()

    private val scratchA = newTile()
    private val scratchB = newTile()
    private val scratchC = newTile()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun invalidate(k: Long) { dirty += k }
    fun invalidateAll() { allDirty = true }

    fun tile(k: Long): Bitmap {
        val c = cache[k]
        if (c != null && !allDirty && k !in dirty) return c
        val out = c ?: newTile().also { cache[k] = it }
        compose(k, out, true)
        dirty -= k
        return out
    }

    /** Call after a full redraw pass so stale flags don't linger. */
    fun frameDone(visibleAll: Boolean) {
        if (visibleAll) { allDirty = false; dirty.clear() }
    }

    fun endAllDirtyIfCovered(covered: Collection<Long>) {
        if (allDirty) { for (k in covered) dirty -= k; allDirty = false; dirty += cache.keys.filter { it !in covered } }
    }

    fun releaseCache() { cache.values.forEach { it.recycle() }; cache.clear(); allDirty = true }

    private fun layerTile(layer: Layer, k: Long): Bitmap? {
        val base = if (layer === liftedLayer) liftedTiles[k] else layer.tiles[k]
        val s = stroke
        if (s == null || s.layer !== layer) return base
        val st = s.tiles[k] ?: return base
        val out = scratchB
        out.eraseColor(Color.TRANSPARENT)
        val c = Canvas(out)
        if (base != null) c.drawBitmap(base, 0f, 0f, null)
        val mask = selection.maskFor(k)
        val src = if (mask != null) {
            scratchC.eraseColor(Color.TRANSPARENT)
            Canvas(scratchC).apply { drawBitmap(st, 0f, 0f, null); drawBitmap(mask, 0f, 0f, Paint().apply { blendMode = BlendMode.DST_IN }) }
            scratchC
        } else st
        paint.alpha = (s.opacity * 255).toInt()
        paint.blendMode = when {
            s.mode == BrushMode.Erase -> BlendMode.DST_OUT
            layer.alphaLock -> BlendMode.SRC_ATOP
            s.glow -> BlendMode.PLUS
            else -> BlendMode.SRC_OVER
        }
        c.drawBitmap(src, 0f, 0f, paint)
        paint.blendMode = BlendMode.SRC_OVER; paint.alpha = 255
        return out
    }

    /** Composes one tile. [paper] false gives a transparent background (PNG export). */
    fun compose(k: Long, out: Bitmap, paper: Boolean) {
        out.eraseColor(if (paper && doc.paperVisible) doc.paperColor else Color.TRANSPARENT)
        val c = Canvas(out)
        val layers = doc.layers
        if (doc.animation.enabled) {
            val f = doc.currentFrame.coerceIn(0, layers.lastIndex)
            for (i in doc.animation.onionSkins downTo 1) {
                val before = f - i; val after = f + i
                val a = (doc.animation.onionOpacity * (1f - (i - 1f) / (doc.animation.onionSkins + 1))).coerceIn(0f, 1f)
                if (before >= 0) drawTinted(c, layers[before], k, 0xFFE0405A.toInt(), a)
                if (after <= layers.lastIndex) drawTinted(c, layers[after], k, 0xFF3FB27F.toInt(), a)
            }
            layers.getOrNull(f)?.let { l -> layerTile(l, k)?.let { drawLayer(c, it, l) } }
            return
        }
        var i = 0
        while (i < layers.size) {
            val base = layers[i]
            var j = i + 1
            while (j < layers.size && layers[j].clipping) j++
            if (base.visible) {
                val bt = layerTile(base, k)
                if (bt != null) {
                    if (j > i + 1) {
                        scratchA.eraseColor(Color.TRANSPARENT)
                        val g = Canvas(scratchA)
                        g.drawBitmap(bt, 0f, 0f, null)
                        for (ci in i + 1 until j) {
                            val cl = layers[ci]; if (!cl.visible) continue
                            val ct = layerTile(cl, k) ?: continue
                            paint.alpha = (cl.opacity * 255).toInt()
                            paint.blendMode = if (cl.blend == SilpaBlend.Normal) BlendMode.SRC_ATOP else cl.blend.mode
                            g.drawBitmap(ct, 0f, 0f, paint)
                        }
                        paint.alpha = 255; paint.blendMode = BlendMode.DST_IN
                        g.drawBitmap(bt, 0f, 0f, paint)
                        paint.blendMode = BlendMode.SRC_OVER
                        drawLayer(c, scratchA, base)
                    } else drawLayer(c, bt, base)
                }
            }
            i = j
        }
    }

    private fun drawLayer(c: Canvas, b: Bitmap, l: Layer) {
        paint.alpha = (l.opacity * 255).toInt(); paint.blendMode = l.blend.mode
        c.drawBitmap(b, 0f, 0f, paint)
        paint.alpha = 255; paint.blendMode = BlendMode.SRC_OVER
    }

    private fun drawTinted(c: Canvas, l: Layer, k: Long, color: Int, alpha: Float) {
        val t = l.tiles[k] ?: return
        val p = Paint().apply { colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP); this.alpha = (alpha * 255).toInt() }
        c.drawBitmap(t, 0f, 0f, p)
    }

    /** Full-resolution flatten for export. */
    fun renderFull(paper: Boolean = true, scale: Float = 1f): Bitmap {
        val w = maxOf(1, (doc.width * scale).toInt()); val h = maxOf(1, (doc.height * scale).toInt())
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.scale(scale, scale)
        val tmp = newTile()
        for (k in doc.allTileKeys()) {
            compose(k, tmp, paper)
            c.drawBitmap(tmp, (keyX(k) * TILE).toFloat(), (keyY(k) * TILE).toFloat(), paint)
        }
        tmp.recycle()
        return out
    }

    /** Fast downscaled render that reuses cached tiles (thumbnails, timelapse frames). */
    fun renderPreview(maxSide: Int): Bitmap {
        val s = minOf(1f, maxSide.toFloat() / maxOf(doc.width, doc.height))
        val out = Bitmap.createBitmap(maxOf(1, (doc.width * s).toInt()), maxOf(1, (doc.height * s).toInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.scale(s, s)
        for (k in doc.allTileKeys()) c.drawBitmap(tile(k), (keyX(k) * TILE).toFloat(), (keyY(k) * TILE).toFloat(), paint)
        return out
    }

    fun pixelAt(x: Int, y: Int): Int {
        if (x !in 0 until doc.width || y !in 0 until doc.height) return Color.TRANSPARENT
        return tile(tileKey(x / TILE, y / TILE)).getPixel(x % TILE, y % TILE)
    }

    companion object {
        /** Color-blindness simulation matrices (Machado et al. 2009, severity 1.0). */
        fun colorBlindFilter(kind: Int): ColorMatrixColorFilter? {
            val m = when (kind) {
                1 -> floatArrayOf(0.152f, 1.053f, -0.205f, 0f, 0f, 0.115f, 0.786f, 0.099f, 0f, 0f, -0.004f, -0.048f, 1.052f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
                2 -> floatArrayOf(0.367f, 0.861f, -0.228f, 0f, 0f, 0.280f, 0.673f, 0.047f, 0f, 0f, -0.012f, 0.043f, 0.969f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
                3 -> floatArrayOf(1.256f, -0.077f, -0.179f, 0f, 0f, -0.078f, 0.931f, 0.148f, 0f, 0f, 0.005f, 0.691f, 0.304f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
                4 -> floatArrayOf(0.299f, 0.587f, 0.114f, 0f, 0f, 0.299f, 0.587f, 0.114f, 0f, 0f, 0.299f, 0.587f, 0.114f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
                else -> return null
            }
            return ColorMatrixColorFilter(ColorMatrix(m))
        }
    }
}
