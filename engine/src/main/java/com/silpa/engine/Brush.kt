package com.silpa.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class TipShape { Round, Square, Chalk, Charcoal, Pencil, Splatter, Bristle, Flat, Star, Leaf, Cloud, Dots, Hatch, Image }
enum class BrushMode { Paint, Smudge, Erase }

/**
 * Everything that defines a brush. Sizes are in canvas pixels. Dynamics follow the usual
 * pro-app model: pressure/tilt/velocity drive size and opacity; jitter adds organic variation.
 */
data class BrushSettings(
    val id: String,
    val name: String,
    val set: String,
    val tip: TipShape = TipShape.Round,
    val hardness: Float = 0.85f,
    val roundness: Float = 1f,
    val angle: Float = 0f,
    val followDirection: Boolean = false,
    val size: Float = 12f,
    val minSize: Float = 0.2f,
    val opacity: Float = 1f,
    val flow: Float = 1f,
    val spacing: Float = 0.08f,
    val sizePressure: Boolean = true,
    val opacityPressure: Boolean = false,
    val pressureGamma: Float = 1f,
    val tiltSize: Float = 0f,
    val velocityThin: Float = 0f,
    val sizeJitter: Float = 0f,
    val angleJitter: Float = 0f,
    val scatter: Float = 0f,
    val opacityJitter: Float = 0f,
    val hueJitter: Float = 0f,
    val count: Int = 1,
    val grain: Float = 0f,
    val grainScale: Float = 1f,
    val smoothing: Float = 0.25f,
    val mode: BrushMode = BrushMode.Paint,
    val smudgeStrength: Float = 0.7f,
    val wetMix: Float = 0f,
    val glow: Boolean = false,
    val tipImagePath: String? = null,
    val custom: Boolean = false,
) : java.io.Serializable

/** Generates and caches brush tip alpha masks. All tips are procedural, so they ship at zero size cost. */
object TipFactory {
    const val RES = 128
    private val cache = HashMap<String, Bitmap>()

    fun tip(b: BrushSettings): Bitmap {
        val key = "${b.tip}:${(b.hardness * 20).toInt()}:${b.tipImagePath}"
        return synchronized(cache) { cache.getOrPut(key) { build(b) } }
    }

    private fun build(b: BrushSettings): Bitmap {
        if (b.tip == TipShape.Image && b.tipImagePath != null) {
            BitmapFactory.decodeFile(b.tipImagePath)?.let { return imageToAlpha(it) }
        }
        val bmp = Bitmap.createBitmap(RES, RES, Bitmap.Config.ALPHA_8)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val r = RES / 2f
        val rnd = Random(b.tip.ordinal * 7919 + 13)
        when (b.tip) {
            TipShape.Round, TipShape.Image -> {
                val h = b.hardness.coerceIn(0f, 0.99f)
                p.shader = RadialGradient(r, r, r, intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                    floatArrayOf(0f, h, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(r, r, r, p)
            }
            TipShape.Square -> c.drawRect(4f, 4f, RES - 4f, RES - 4f, p)
            TipShape.Flat -> c.drawRoundRect(2f, r - 14f, RES - 2f, r + 14f, 10f, 10f, p)
            TipShape.Chalk -> repeat(900) {
                val a = rnd.nextFloat() * 2 * PI; val d = r * kotlin.math.sqrt(rnd.nextFloat()) * 0.95f
                p.alpha = 90 + rnd.nextInt(165)
                c.drawCircle(r + (cos(a) * d).toFloat(), r + (sin(a) * d).toFloat(), 1.5f + rnd.nextFloat() * 3f, p)
            }
            TipShape.Charcoal -> repeat(500) {
                val x = rnd.nextFloat() * RES; val y = r + (rnd.nextFloat() - 0.5f) * RES * 0.55f
                p.alpha = 60 + rnd.nextInt(195)
                c.drawRect(x, y, x + 2 + rnd.nextFloat() * 6, y + 1 + rnd.nextFloat() * 3, p)
            }
            TipShape.Pencil -> repeat(1400) {
                val a = rnd.nextFloat() * 2 * PI; val d = r * kotlin.math.sqrt(rnd.nextFloat())
                p.alpha = 120 + rnd.nextInt(135)
                c.drawPoint(r + (cos(a) * d).toFloat(), r + (sin(a) * d).toFloat(), p.apply { strokeWidth = 2.2f })
            }
            TipShape.Splatter -> repeat(40) {
                val a = rnd.nextFloat() * 2 * PI; val d = r * rnd.nextFloat() * 0.9f
                c.drawCircle(r + (cos(a) * d).toFloat(), r + (sin(a) * d).toFloat(), 2f + rnd.nextFloat() * 9f, p)
            }
            TipShape.Bristle -> repeat(26) {
                val x = 8 + rnd.nextFloat() * (RES - 16); val y = 8 + rnd.nextFloat() * (RES - 16)
                p.alpha = 140 + rnd.nextInt(115)
                c.drawCircle(x, y, 3f + rnd.nextFloat() * 5f, p)
            }
            TipShape.Star -> {
                val path = Path()
                for (i in 0 until 10) {
                    val rr = if (i % 2 == 0) r * 0.95f else r * 0.4f
                    val a = -PI / 2 + i * PI / 5
                    val x = r + (cos(a) * rr).toFloat(); val y = r + (sin(a) * rr).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close(); c.drawPath(path, p)
            }
            TipShape.Leaf -> {
                val path = Path().apply {
                    moveTo(r, 4f); quadTo(RES - 10f, r, r, RES - 4f); quadTo(10f, r, r, 4f); close()
                }
                c.drawPath(path, p)
            }
            TipShape.Cloud -> repeat(16) {
                val a = rnd.nextFloat() * 2 * PI; val d = r * 0.45f * rnd.nextFloat()
                p.shader = null; p.alpha = 70
                c.drawCircle(r + (cos(a) * d).toFloat(), r + (sin(a) * d).toFloat(), r * (0.3f + rnd.nextFloat() * 0.25f), p)
            }
            TipShape.Dots -> for (y in 0 until 5) for (x in 0 until 5) {
                c.drawCircle(16f + x * 24f, 16f + y * 24f, 7f, p)
            }
            TipShape.Hatch -> {
                p.strokeWidth = 5f
                for (i in -RES until RES step 16) c.drawLine(i.toFloat(), RES.toFloat(), i + RES.toFloat(), 0f, p)
            }
        }
        return bmp
    }

    fun imageToAlpha(src: Bitmap): Bitmap {
        val s = Bitmap.createScaledBitmap(src, RES, RES, true)
        val px = IntArray(RES * RES); s.getPixels(px, 0, RES, 0, 0, RES, RES)
        val out = Bitmap.createBitmap(RES, RES, Bitmap.Config.ALPHA_8)
        val hasAlpha = px.any { it ushr 24 < 250 }
        val a = IntArray(RES * RES) { i ->
            val c = px[i]
            val lum = (Color.red(c) * 0.3f + Color.green(c) * 0.59f + Color.blue(c) * 0.11f)
            val v = if (hasAlpha) Color.alpha(c) else (255 - lum).toInt()
            Color.argb(v.coerceIn(0, 255), 0, 0, 0)
        }
        val tmp = Bitmap.createBitmap(a, RES, RES, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(tmp, 0f, 0f, null)
        return out
    }

    private var grainBmp: Bitmap? = null

    /** Paper grain texture (tileable value noise), anchored to the canvas so it reads like real paper. */
    @Synchronized
    fun grain(): Bitmap = grainBmp ?: run {
        val n = 128; val rnd = Random(42)
        val base = FloatArray(16 * 16) { rnd.nextFloat() }
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            fun v(ix: Int, iy: Int) = base[(iy and 15) * 16 + (ix and 15)]
            val fx = x / 8f; val fy = y / 8f; val ix = fx.toInt(); val iy = fy.toInt()
            val tx = fx - ix; val ty = fy - iy
            val a = v(ix, iy) * (1 - tx) + v(ix + 1, iy) * tx
            val bb = v(ix, iy + 1) * (1 - tx) + v(ix + 1, iy + 1) * tx
            val smooth = a * (1 - ty) + bb * ty
            val fine = rnd.nextFloat()
            val g = (smooth * 0.6f + fine * 0.4f)
            px[y * n + x] = Color.argb((g * 255).toInt().coerceIn(0, 255), 0, 0, 0)
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).also { grainBmp = it }
    }
}

/** The bundled brush library: 14 sets, each brush hand-tuned with distinct dynamics. */
object BrushLibrary {
    private fun b(set: String, name: String, f: BrushSettings.() -> BrushSettings) =
        BrushSettings(id = "$set/$name", name = name, set = set).f()

    val all: List<BrushSettings> = buildList {
        // Sketching
        add(b("Sketching", "HB Pencil") { copy(tip = TipShape.Pencil, size = 5f, minSize = 0.5f, opacityPressure = true, grain = 0.55f, spacing = 0.12f, smoothing = 0.1f) })
        add(b("Sketching", "6B Pencil") { copy(tip = TipShape.Pencil, size = 10f, minSize = 0.4f, opacityPressure = true, grain = 0.8f, spacing = 0.1f) })
        add(b("Sketching", "Mechanical") { copy(tip = TipShape.Round, hardness = 0.95f, size = 3f, minSize = 0.7f, opacityPressure = true, opacity = 0.9f, smoothing = 0.15f) })
        add(b("Sketching", "Blue Line") { copy(tip = TipShape.Pencil, size = 6f, opacity = 0.7f, grain = 0.5f, opacityPressure = true) })
        add(b("Sketching", "Charcoal Stick") { copy(tip = TipShape.Charcoal, size = 28f, opacityPressure = true, grain = 0.7f, followDirection = true, spacing = 0.06f) })
        add(b("Sketching", "Tilt Shader") { copy(tip = TipShape.Pencil, size = 18f, tiltSize = 0.9f, opacity = 0.6f, grain = 0.8f, opacityPressure = true) })
        add(b("Sketching", "Gesture") { copy(tip = TipShape.Round, hardness = 0.7f, size = 8f, velocityThin = 0.5f, smoothing = 0.05f) })
        // Inking
        add(b("Inking", "Studio Pen") { copy(tip = TipShape.Round, hardness = 0.95f, size = 9f, minSize = 0.05f, smoothing = 0.45f) })
        add(b("Inking", "Technical Pen") { copy(tip = TipShape.Round, hardness = 1f, size = 6f, sizePressure = false, smoothing = 0.4f) })
        add(b("Inking", "G-Pen") { copy(tip = TipShape.Round, hardness = 0.97f, size = 12f, minSize = 0.02f, pressureGamma = 1.6f, smoothing = 0.5f) })
        add(b("Inking", "Maru Pen") { copy(tip = TipShape.Round, hardness = 0.97f, size = 5f, minSize = 0.1f, smoothing = 0.5f) })
        add(b("Inking", "Brush Pen") { copy(tip = TipShape.Leaf, hardness = 1f, size = 18f, minSize = 0.05f, followDirection = true, smoothing = 0.4f) })
        add(b("Inking", "Dry Ink") { copy(tip = TipShape.Bristle, size = 16f, minSize = 0.2f, followDirection = true, grain = 0.35f) })
        add(b("Inking", "Ink Wash") { copy(tip = TipShape.Round, hardness = 0.3f, size = 40f, opacity = 0.5f, flow = 0.3f) })
        add(b("Inking", "Monoline") { copy(tip = TipShape.Round, hardness = 1f, size = 8f, sizePressure = false, smoothing = 0.7f) })
        // Painting
        add(b("Painting", "Round Brush") { copy(tip = TipShape.Round, hardness = 0.8f, size = 30f, minSize = 0.3f, opacityPressure = true, flow = 0.7f) })
        add(b("Painting", "Flat Brush") { copy(tip = TipShape.Flat, size = 40f, followDirection = true, flow = 0.8f, wetMix = 0.25f) })
        add(b("Painting", "Oil Paint") { copy(tip = TipShape.Bristle, size = 45f, followDirection = true, wetMix = 0.45f, grain = 0.2f, spacing = 0.05f) })
        add(b("Painting", "Gouache") { copy(tip = TipShape.Round, hardness = 0.9f, size = 35f, wetMix = 0.2f, grain = 0.35f) })
        add(b("Painting", "Acrylic") { copy(tip = TipShape.Bristle, size = 38f, wetMix = 0.3f, opacityPressure = true) })
        add(b("Painting", "Blender") { copy(tip = TipShape.Round, hardness = 0.4f, size = 50f, mode = BrushMode.Smudge, smudgeStrength = 0.85f) })
        add(b("Painting", "Palette Knife") { copy(tip = TipShape.Flat, size = 50f, mode = BrushMode.Smudge, smudgeStrength = 0.95f, followDirection = true) })
        // Watercolor
        add(b("Watercolor", "Wet Wash") { copy(tip = TipShape.Cloud, size = 70f, opacity = 0.45f, flow = 0.25f, wetMix = 0.5f) })
        add(b("Watercolor", "Granulating") { copy(tip = TipShape.Round, hardness = 0.3f, size = 55f, opacity = 0.5f, flow = 0.3f, grain = 0.9f) })
        add(b("Watercolor", "Round Wash") { copy(tip = TipShape.Round, hardness = 0.2f, size = 45f, opacity = 0.55f, flow = 0.35f, wetMix = 0.3f) })
        add(b("Watercolor", "Bleed") { copy(tip = TipShape.Cloud, size = 90f, opacity = 0.3f, flow = 0.15f, mode = BrushMode.Smudge, smudgeStrength = 0.6f) })
        add(b("Watercolor", "Dry Brush") { copy(tip = TipShape.Bristle, size = 35f, grain = 0.85f, opacity = 0.7f, followDirection = true) })
        // Airbrush
        add(b("Airbrush", "Soft Air") { copy(tip = TipShape.Round, hardness = 0f, size = 90f, flow = 0.08f, sizePressure = false, opacityPressure = true, spacing = 0.05f) })
        add(b("Airbrush", "Hard Air") { copy(tip = TipShape.Round, hardness = 0.6f, size = 50f, flow = 0.12f, sizePressure = false, opacityPressure = true) })
        add(b("Airbrush", "Spray") { copy(tip = TipShape.Splatter, size = 60f, flow = 0.4f, scatter = 0.3f, angleJitter = 1f) })
        add(b("Airbrush", "Mist") { copy(tip = TipShape.Cloud, size = 140f, flow = 0.05f, sizePressure = false) })
        // Markers
        add(b("Markers", "Alcohol Marker") { copy(tip = TipShape.Flat, size = 22f, opacity = 0.6f, sizePressure = false, followDirection = true) })
        add(b("Markers", "Fineliner") { copy(tip = TipShape.Round, hardness = 1f, size = 4f, sizePressure = false, smoothing = 0.3f) })
        add(b("Markers", "Highlighter") { copy(tip = TipShape.Square, size = 30f, opacity = 0.4f, sizePressure = false, roundness = 0.35f) })
        add(b("Markers", "Felt Tip") { copy(tip = TipShape.Round, hardness = 0.75f, size = 12f, opacity = 0.85f, grain = 0.15f) })
        // Textures
        add(b("Textures", "Chalk") { copy(tip = TipShape.Chalk, size = 30f, grain = 0.6f, angleJitter = 1f) })
        add(b("Textures", "Pastel") { copy(tip = TipShape.Chalk, size = 40f, grain = 0.9f, opacityPressure = true, angleJitter = 1f) })
        add(b("Textures", "Crayon") { copy(tip = TipShape.Pencil, size = 20f, grain = 1f, opacityPressure = true) })
        add(b("Textures", "Grunge") { copy(tip = TipShape.Splatter, size = 70f, angleJitter = 1f, sizeJitter = 0.4f, scatter = 0.2f) })
        add(b("Textures", "Sponge") { copy(tip = TipShape.Bristle, size = 60f, angleJitter = 1f, sizeJitter = 0.3f, flow = 0.6f) })
        add(b("Textures", "Noise") { copy(tip = TipShape.Pencil, size = 80f, flow = 0.3f, angleJitter = 1f, grain = 1f) })
        // Effects
        add(b("Effects", "Glow") { copy(tip = TipShape.Round, hardness = 0f, size = 60f, flow = 0.2f, glow = true) })
        add(b("Effects", "Light Pen") { copy(tip = TipShape.Round, hardness = 0.7f, size = 10f, glow = true, smoothing = 0.4f) })
        add(b("Effects", "Sparkles") { copy(tip = TipShape.Star, size = 26f, scatter = 1.5f, sizeJitter = 0.7f, angleJitter = 1f, spacing = 1.2f, glow = true) })
        add(b("Effects", "Bokeh") { copy(tip = TipShape.Round, hardness = 0.85f, size = 50f, scatter = 2f, sizeJitter = 0.8f, opacityJitter = 0.7f, spacing = 1.5f, glow = true) })
        add(b("Effects", "Rainbow") { copy(tip = TipShape.Round, hardness = 0.8f, size = 25f, hueJitter = 1f, spacing = 0.15f) })
        // Nature
        add(b("Nature", "Foliage") { copy(tip = TipShape.Leaf, size = 30f, scatter = 1.2f, sizeJitter = 0.5f, angleJitter = 1f, spacing = 0.5f, hueJitter = 0.06f) })
        add(b("Nature", "Grass") { copy(tip = TipShape.Hatch, size = 40f, angleJitter = 0.2f, sizeJitter = 0.4f, spacing = 0.4f) })
        add(b("Nature", "Clouds") { copy(tip = TipShape.Cloud, size = 120f, flow = 0.25f, sizeJitter = 0.4f, angleJitter = 1f, spacing = 0.2f) })
        add(b("Nature", "Snow") { copy(tip = TipShape.Round, hardness = 0.7f, size = 10f, scatter = 3f, sizeJitter = 0.8f, spacing = 2f) })
        add(b("Nature", "Stars") { copy(tip = TipShape.Star, size = 14f, scatter = 3f, sizeJitter = 0.8f, spacing = 2.5f, angleJitter = 1f) })
        // Comic
        add(b("Comic", "Halftone") { copy(tip = TipShape.Dots, size = 60f, sizePressure = false) })
        add(b("Comic", "Hatching") { copy(tip = TipShape.Hatch, size = 50f, sizePressure = false) })
        add(b("Comic", "Speed Liner") { copy(tip = TipShape.Round, hardness = 1f, size = 6f, minSize = 0f, velocityThin = 0.8f, smoothing = 0.8f) })
        add(b("Comic", "Kake-ami") { copy(tip = TipShape.Hatch, size = 30f, followDirection = true) })
        // Calligraphy
        add(b("Calligraphy", "Chisel") { copy(tip = TipShape.Flat, size = 20f, angle = 45f, sizePressure = false) })
        add(b("Calligraphy", "Script") { copy(tip = TipShape.Round, hardness = 1f, size = 14f, minSize = 0.15f, pressureGamma = 1.4f, smoothing = 0.6f) })
        add(b("Calligraphy", "Sumi") { copy(tip = TipShape.Bristle, size = 30f, minSize = 0.1f, followDirection = true, grain = 0.25f) })
        // Pixel
        add(b("Pixel", "Pixel 1px") { copy(tip = TipShape.Square, size = 1f, sizePressure = false, spacing = 0.5f, smoothing = 0f) })
        add(b("Pixel", "Pixel 4px") { copy(tip = TipShape.Square, size = 4f, sizePressure = false, spacing = 0.3f, smoothing = 0f) })
        // Erasers
        add(b("Erasers", "Hard Eraser") { copy(tip = TipShape.Round, hardness = 0.95f, size = 30f, mode = BrushMode.Erase, sizePressure = false) })
        add(b("Erasers", "Soft Eraser") { copy(tip = TipShape.Round, hardness = 0f, size = 80f, mode = BrushMode.Erase, flow = 0.3f) })
        add(b("Erasers", "Texture Eraser") { copy(tip = TipShape.Chalk, size = 40f, mode = BrushMode.Erase, grain = 0.6f) })
        // Smudge
        add(b("Smudge", "Finger") { copy(tip = TipShape.Round, hardness = 0.5f, size = 30f, mode = BrushMode.Smudge, smudgeStrength = 0.8f) })
        add(b("Smudge", "Liquify Push") { copy(tip = TipShape.Round, hardness = 0.6f, size = 70f, mode = BrushMode.Smudge, smudgeStrength = 0.98f, sizePressure = false) })
        add(b("Smudge", "Soft Blend") { copy(tip = TipShape.Round, hardness = 0.1f, size = 60f, mode = BrushMode.Smudge, smudgeStrength = 0.6f) })
        add(b("Smudge", "Texture Smudge") { copy(tip = TipShape.Chalk, size = 40f, mode = BrushMode.Smudge, smudgeStrength = 0.75f) })
    }

    val sets: List<String> get() = all.map { it.set }.distinct()

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
