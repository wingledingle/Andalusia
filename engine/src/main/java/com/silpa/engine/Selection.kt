package com.silpa.engine

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

enum class SelectOp { Replace, Add, Subtract }

/** Selection stored as sparse ALPHA_8 tiles, so soft edges and magic-wand regions are exact. */
class Selection(private val width: Int, private val height: Int) {
    val tiles = HashMap<Long, Bitmap>()
    var active = false
        private set
    /** Outline for marching ants when the selection came from a shape. */
    var outline: Path? = null
        private set
    var onChanged: (() -> Unit)? = null

    private val tilesX = (width + TILE - 1) / TILE
    private val tilesY = (height + TILE - 1) / TILE

    fun snapshot(): Triple<Boolean, Map<Long, Bitmap>, Path?> =
        Triple(active, tiles.mapValues { it.value.copy(Bitmap.Config.ALPHA_8, true) }, outline?.let { Path(it) })

    fun restore(s: Triple<Boolean, Map<Long, Bitmap>, Path?>) {
        tiles.clear(); tiles.putAll(s.second); active = s.first; outline = s.third; onChanged?.invoke()
    }

    fun clear() { tiles.clear(); active = false; outline = null; onChanged?.invoke() }

    fun selectAll() {
        tiles.clear()
        for (y in 0 until tilesY) for (x in 0 until tilesX) tiles[tileKey(x, y)] = full()
        active = true; outline = Path().apply { addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW) }
        onChanged?.invoke()
    }

    private fun full() = Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ALPHA_8).apply { eraseColor(Color.BLACK) }

    fun fromPath(path: Path, op: SelectOp, feather: Float = 0f) {
        if (op == SelectOp.Replace || !active) tiles.clear()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            if (op == SelectOp.Subtract) blendMode = BlendMode.CLEAR
            if (feather > 0) maskFilter = android.graphics.BlurMaskFilter(feather, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        val b = android.graphics.RectF(); path.computeBounds(b, true)
        val x0 = ((b.left - feather) / TILE).toInt().coerceAtLeast(0); val x1 = ((b.right + feather) / TILE).toInt().coerceAtMost(tilesX - 1)
        val y0 = ((b.top - feather) / TILE).toInt().coerceAtLeast(0); val y1 = ((b.bottom + feather) / TILE).toInt().coerceAtMost(tilesY - 1)
        for (ty in y0..y1) for (tx in x0..x1) {
            val k = tileKey(tx, ty)
            if (op == SelectOp.Subtract && k !in tiles) continue
            val t = tiles.getOrPut(k) { Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ALPHA_8) }
            val c = Canvas(t); c.translate(-(tx * TILE).toFloat(), -(ty * TILE).toFloat()); c.drawPath(path, p)
        }
        active = tiles.isNotEmpty()
        outline = when (op) {
            SelectOp.Replace -> Path(path)
            SelectOp.Add -> (outline?.let { Path(it) } ?: Path()).apply { addPath(path) }
            SelectOp.Subtract -> outline?.let { o -> Path(o).apply { op(path, Path.Op.DIFFERENCE) } }
        }
        onChanged?.invoke()
    }

    /** From a boolean mask (magic wand / select layer content). */
    fun fromMask(mask: BooleanArray, op: SelectOp) {
        if (op == SelectOp.Replace || !active) tiles.clear()
        for (ty in 0 until tilesY) for (tx in 0 until tilesX) {
            val ox = tx * TILE; val oy = ty * TILE
            var any = false
            val px = IntArray(TILE * TILE)
            for (y in 0 until TILE) {
                val gy = oy + y; if (gy >= height) break
                for (x in 0 until TILE) {
                    val gx = ox + x; if (gx >= width) break
                    if (mask[gy * width + gx]) { px[y * TILE + x] = Color.BLACK; any = true }
                }
            }
            if (!any) continue
            val k = tileKey(tx, ty)
            val src = Bitmap.createBitmap(px, TILE, TILE, Bitmap.Config.ARGB_8888)
            val t = tiles.getOrPut(k) { Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ALPHA_8) }
            Canvas(t).drawBitmap(src, 0f, 0f, Paint().apply { if (op == SelectOp.Subtract) blendMode = BlendMode.DST_OUT })
        }
        active = tiles.isNotEmpty(); outline = null
        onChanged?.invoke()
    }

    fun invert() {
        if (!active) { selectAll(); return }
        val p = Paint().apply { blendMode = BlendMode.DST_OUT }
        for (y in 0 until tilesY) for (x in 0 until tilesX) {
            val k = tileKey(x, y); val old = tiles[k]
            val n = full()
            if (old != null) Canvas(n).drawBitmap(old, 0f, 0f, p)
            tiles[k] = n
        }
        outline = outline?.let { o ->
            Path().apply { addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW); op(o, Path.Op.DIFFERENCE) }
        }
        onChanged?.invoke()
    }

    /** Null = no selection (everything editable). Empty tile = nothing selected there. */
    fun maskFor(k: Long): Bitmap? = if (!active) null else tiles[k] ?: EMPTY

    companion object {
        val EMPTY: Bitmap by lazy { Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ALPHA_8) }
    }
}

private val dstIn = Paint().apply { blendMode = BlendMode.DST_IN }
private val dstOut = Paint().apply { blendMode = BlendMode.DST_OUT }
private val plus = Paint().apply { blendMode = BlendMode.PLUS }

/** result = original outside the mask, modified inside it (soft-edge aware). */
fun maskedMerge(original: Bitmap?, modified: Bitmap, mask: Bitmap?): Bitmap {
    if (mask == null) return modified
    val inside = modified.copy(Bitmap.Config.ARGB_8888, true)
    Canvas(inside).drawBitmap(mask, 0f, 0f, dstIn)
    if (original != null) {
        val outside = original.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(outside).drawBitmap(mask, 0f, 0f, dstOut)
        Canvas(inside).drawBitmap(outside, 0f, 0f, plus)
    }
    return inside
}
