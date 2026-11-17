package com.silpa.engine

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.util.UUID

/** Canvases are stored as sparse 256px tiles: empty areas cost no memory, so huge canvases are practical. */
const val TILE = 256

fun tileKey(tx: Int, ty: Int): Long = (tx.toLong() shl 32) or (ty.toLong() and 0xffffffffL)
fun keyX(k: Long): Int = (k shr 32).toInt()
fun keyY(k: Long): Int = k.toInt()

fun newTile(): Bitmap = Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888)

enum class SilpaBlend(val label: String, val mode: BlendMode, val psd: String) {
    Normal("Normal", BlendMode.SRC_OVER, "norm"),
    Multiply("Multiply", BlendMode.MULTIPLY, "mul "),
    Screen("Screen", BlendMode.SCREEN, "scrn"),
    Overlay("Overlay", BlendMode.OVERLAY, "over"),
    Darken("Darken", BlendMode.DARKEN, "dark"),
    Lighten("Lighten", BlendMode.LIGHTEN, "lite"),
    ColorDodge("Color Dodge", BlendMode.COLOR_DODGE, "div "),
    ColorBurn("Color Burn", BlendMode.COLOR_BURN, "idiv"),
    HardLight("Hard Light", BlendMode.HARD_LIGHT, "hLit"),
    SoftLight("Soft Light", BlendMode.SOFT_LIGHT, "sLit"),
    Difference("Difference", BlendMode.DIFFERENCE, "diff"),
    Exclusion("Exclusion", BlendMode.EXCLUSION, "smud"),
    Hue("Hue", BlendMode.HUE, "hue "),
    Saturation("Saturation", BlendMode.SATURATION, "sat "),
    Color("Color", BlendMode.COLOR, "colr"),
    Luminosity("Luminosity", BlendMode.LUMINOSITY, "lum "),
    Add("Add (Glow)", BlendMode.PLUS, "lddg"),
    ;

    companion object {
        fun fromPsd(key: String) = entries.firstOrNull { it.psd == key } ?: Normal
    }
}

data class LayerProps(
    val name: String,
    val visible: Boolean,
    val opacity: Float,
    val blend: SilpaBlend,
    val alphaLock: Boolean,
    val clipping: Boolean,
    val locked: Boolean,
    val reference: Boolean,
)

class Layer(val id: String, var name: String, val width: Int, val height: Int) {
    val tiles = HashMap<Long, Bitmap>()
    var visible = true
    var opacity = 1f
    var blend = SilpaBlend.Normal
    var alphaLock = false
    var clipping = false
    var locked = false
    /** Reference layers are used by the fill tool to find line-art boundaries. */
    var reference = false
    internal var listener: ((Layer, Long) -> Unit)? = null

    val tilesX get() = (width + TILE - 1) / TILE
    val tilesY get() = (height + TILE - 1) / TILE

    fun getOrCreate(k: Long): Bitmap = tiles.getOrPut(k) { newTile() }

    fun set(k: Long, b: Bitmap?) {
        if (b == null) tiles.remove(k) else tiles[k] = b
        listener?.invoke(this, k)
    }

    fun touched(k: Long) { listener?.invoke(this, k) }

    var props: LayerProps
        get() = LayerProps(name, visible, opacity, blend, alphaLock, clipping, locked, reference)
        set(p) {
            name = p.name; visible = p.visible; opacity = p.opacity; blend = p.blend
            alphaLock = p.alphaLock; clipping = p.clipping; locked = p.locked; reference = p.reference
        }

    fun duplicate(newName: String = "$name copy"): Layer {
        val l = Layer(UUID.randomUUID().toString(), newName, width, height)
        l.props = props.copy(name = newName)
        for ((k, b) in tiles) l.tiles[k] = b.copy(Bitmap.Config.ARGB_8888, true)
        return l
    }

    /** Flattens this layer into one full-size bitmap (used by filters, transform, export). */
    fun toBitmap(): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        for ((k, b) in tiles) c.drawBitmap(b, (keyX(k) * TILE).toFloat(), (keyY(k) * TILE).toFloat(), null)
        return out
    }

    /** Pixel bounds of non-transparent content, or null when empty. */
    fun contentBounds(): Rect? {
        if (tiles.isEmpty()) return null
        var l = Int.MAX_VALUE; var t = Int.MAX_VALUE; var r = Int.MIN_VALUE; var b = Int.MIN_VALUE
        val px = IntArray(TILE * TILE)
        for ((k, bmp) in tiles) {
            bmp.getPixels(px, 0, TILE, 0, 0, TILE, TILE)
            val ox = keyX(k) * TILE; val oy = keyY(k) * TILE
            for (y in 0 until TILE) {
                val row = y * TILE
                for (x in 0 until TILE) {
                    if (px[row + x] ushr 24 != 0) {
                        val gx = ox + x; val gy = oy + y
                        if (gx < l) l = gx; if (gx > r) r = gx
                        if (gy < t) t = gy; if (gy > b) b = gy
                    }
                }
            }
        }
        if (r < l) return null
        return Rect(l, t, minOf(r + 1, width), minOf(b + 1, height))
    }
}

/** Splits a full-size bitmap into tiles, dropping fully transparent ones. */
fun splitIntoTiles(src: Bitmap, width: Int, height: Int): HashMap<Long, Bitmap> {
    val out = HashMap<Long, Bitmap>()
    val tx = (width + TILE - 1) / TILE; val ty = (height + TILE - 1) / TILE
    val px = IntArray(TILE * TILE)
    for (y in 0 until ty) for (x in 0 until tx) {
        val t = newTile()
        Canvas(t).drawBitmap(src, -(x * TILE).toFloat(), -(y * TILE).toFloat(), null)
        t.getPixels(px, 0, TILE, 0, 0, TILE, TILE)
        if (px.any { it ushr 24 != 0 }) out[tileKey(x, y)] = t else t.recycle()
    }
    return out
}

data class AnimationSettings(
    val enabled: Boolean = false,
    val fps: Int = 12,
    val onionSkins: Int = 1,
    val onionOpacity: Float = 0.35f,
    val loop: Boolean = true,
)

class Document(val width: Int, val height: Int) {
    val layers = mutableListOf<Layer>()
    var activeIndex = 0
    var paperColor = Color.WHITE
    var paperVisible = true
    var animation = AnimationSettings()
    /** In animation mode every layer is one frame, Procreate-style. */
    var currentFrame = 0
    var name = "Untitled"

    val tilesX get() = (width + TILE - 1) / TILE
    val tilesY get() = (height + TILE - 1) / TILE

    var onTileChanged: ((Layer, Long) -> Unit)? = null
    var onStructureChanged: (() -> Unit)? = null

    val active: Layer get() = layers[activeIndex.coerceIn(0, layers.lastIndex)]

    fun attach(l: Layer): Layer { l.listener = { layer, k -> onTileChanged?.invoke(layer, k) }; return l }

    fun newLayer(name: String = "Layer ${layers.size + 1}"): Layer =
        attach(Layer(UUID.randomUUID().toString(), name, width, height))

    fun structureChanged() {
        layers.forEach { attach(it) }
        activeIndex = activeIndex.coerceIn(0, maxOf(0, layers.lastIndex))
        onStructureChanged?.invoke()
    }

    fun allTileKeys(): List<Long> {
        val out = ArrayList<Long>(tilesX * tilesY)
        for (y in 0 until tilesY) for (x in 0 until tilesX) out += tileKey(x, y)
        return out
    }

    fun tileRect(k: Long) = Rect(keyX(k) * TILE, keyY(k) * TILE, keyX(k) * TILE + TILE, keyY(k) * TILE + TILE)
}

internal val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
