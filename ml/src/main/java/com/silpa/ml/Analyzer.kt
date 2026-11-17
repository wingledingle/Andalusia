package com.silpa.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class FacePart(val category: String, val rect: Rect)

data class Analysis(
    val category: String,
    val confidence: Float,
    val labels: List<String>,
    val embedding: FloatArray?,
    val faces: List<List<FacePart>>,
    val palette: List<Int>,
    val colorNames: List<String>,
    val style: String,
    val hash: Long,
)

/**
 * On-device image understanding (MediaPipe, Apache-2.0 models): ImageNet classification mapped to
 * Silpa categories, embeddings for similarity + learning from corrections, face landmarks for
 * cutting faces into eyes / nose / mouth. Nothing leaves the device.
 */
class Analyzer(context: Context) {
    /** MediaPipe's native runtime only exists on devices; host-JVM tests skip the models. */
    private val native = android.os.Build.FINGERPRINT != "robolectric"
    private val classifier = if (!native) null else runCatching {
        ImageClassifier.createFromOptions(context, ImageClassifier.ImageClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("classifier.tflite").build())
            .setMaxResults(5).build())
    }.getOrNull()
    private val embedder = if (!native) null else runCatching {
        ImageEmbedder.createFromOptions(context, ImageEmbedder.ImageEmbedderOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("embedder.tflite").build())
            .setL2Normalize(true).setQuantize(false).build())
    }.getOrNull()
    private val faces = if (!native) null else runCatching {
        FaceLandmarker.createFromOptions(context, FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setNumFaces(4).setMinFaceDetectionConfidence(0.45f).build())
    }.getOrNull()

    fun analyze(src: Bitmap, examples: List<Pair<FloatArray, String>>): Analysis {
        val bmp = if (src.config != Bitmap.Config.ARGB_8888) src.copy(Bitmap.Config.ARGB_8888, false) else src
        val flat = flatten(bmp)
        val img = if (native) BitmapImageBuilder(flat).build() else null
        val cats = if (img == null) emptyList() else runCatching { classifier?.classify(img)?.classificationResult()?.classifications()?.firstOrNull()?.categories() }.getOrNull().orEmpty()
        val labels = cats.map { it.categoryName() to it.score() }
        val idx = cats.map { it.index() }
        val emb = if (img == null) null else runCatching { embedder?.embed(img)?.embeddingResult()?.embeddings()?.firstOrNull()?.floatEmbedding() }.getOrNull()
        val faceParts = if (img == null) emptyList() else runCatching { detectFaces(flat) }.getOrDefault(emptyList())

        var (category, conf) = mapCategory(labels, idx)
        if (faceParts.isNotEmpty()) { category = if (faceParts.size == 1 && faceParts[0][0].rect.width() > flat.width * 0.5f) "Faces" else "Characters"; conf = 0.8f }
        // Learn from the user's own corrections and the labelled starter pack (nearest neighbours).
        if (emb != null && examples.isNotEmpty()) {
            val near = examples.map { (e, c) -> cosine(emb, e) to c }.sortedByDescending { it.first }.take(5)
            val best = near.first()
            if (best.first > 0.82f || (best.first > 0.7f && best.first > conf)) {
                val votes = near.filter { it.first > best.first - 0.08f }.groupingBy { it.second }.eachCount()
                category = votes.maxByOrNull { it.value }!!.key; conf = best.first
            }
        }
        val pal = palette(flat)
        val style = style(flat)
        return Analysis(category, conf, labels.map { it.first.replace('_', ' ') }, emb, faceParts, pal, pal.map(::colorName).distinct().take(3), style, dHash(flat))
    }

    private fun flatten(b: Bitmap): Bitmap {
        val s = min(1f, 640f / max(b.width, b.height))
        val out = Bitmap.createBitmap(max(1, (b.width * s).toInt()), max(1, (b.height * s).toInt()), Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(Bitmap.createScaledBitmap(b, out.width, out.height, true), 0f, 0f, null) }
        return out
    }

    private fun detectFaces(b: Bitmap): List<List<FacePart>> {
        val r = faces?.detect(BitmapImageBuilder(b).build()) ?: return emptyList()
        return r.faceLandmarks().map { lm ->
            fun box(ids: IntArray, pad: Float): Rect {
                var l = 1f; var t = 1f; var rr = 0f; var bb = 0f
                for (i in ids) { val p = lm[i]; l = min(l, p.x()); t = min(t, p.y()); rr = max(rr, p.x()); bb = max(bb, p.y()) }
                val w = (rr - l) * pad; val h = (bb - t) * pad
                return Rect(((l - w) * b.width).toInt().coerceAtLeast(0), ((t - h) * b.height).toInt().coerceAtLeast(0),
                    ((rr + w) * b.width).toInt().coerceAtMost(b.width), ((bb + h) * b.height).toInt().coerceAtMost(b.height))
            }
            listOf(
                FacePart("Faces", box(IntArray(lm.size) { it }, 0.12f)),
                FacePart("Eyes", box(intArrayOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 246, 161, 160, 159, 158, 157, 173, 70, 63, 105, 66, 107), 0.35f)),
                FacePart("Eyes", box(intArrayOf(263, 249, 390, 373, 374, 380, 381, 382, 362, 466, 388, 387, 386, 385, 384, 398, 300, 293, 334, 296, 336), 0.35f)),
                FacePart("Noses", box(intArrayOf(1, 2, 98, 327, 168, 6, 197, 195, 5, 4, 45, 275, 129, 358), 0.3f)),
                FacePart("Mouths", box(intArrayOf(61, 291, 0, 17, 13, 14, 78, 308, 40, 270, 91, 321, 37, 267, 84, 314), 0.3f)),
            ).filter { it.rect.width() > 8 && it.rect.height() > 8 }
        }
    }

    companion object {
        private val keywords = linkedMapOf(
            "Houses" to listOf("house", "palace", "church", "monastery", "castle", "barn", "boathouse", "mobile home", "cinema", "library", "mosque", "stupa", "dome", "greenhouse", "bell cote", "prison", "planetarium", "shop", "bakery", "barbershop", "triumphal arch", "pier", "suspension bridge", "steel arch bridge", "viaduct", "water tower", "lighthouse", "beacon", "patio", "fence", "picket", "thatch", "tile roof", "yurt", "cliff dwelling", "apiary"),
            "Interiors" to listOf("dining table", "studio couch", "bookcase", "wardrobe", "home theater", "window shade", "four-poster", "quilt", "entertainment center", "desk", "china cabinet", "shower curtain", "bathtub", "rocking chair", "folding chair", "medicine chest", "refrigerator", "stove", "dishwasher", "washbasin", "chiffonier", "cradle", "crib", "throne", "barber chair"),
            "Landscapes" to listOf("alp", "cliff", "valley", "volcano", "lakeside", "seashore", "sandbar", "coral reef", "geyser", "promontory", "breakwater", "dam"),
            "Plants" to listOf("daisy", "lady's slipper", "corn", "acorn", "hip", "buckeye", "coral fungus", "agaric", "mushroom", "pot", "rapeseed", "cardoon", "strawberry", "orange", "lemon", "banana", "pineapple", "fig", "pomegranate", "granny smith", "custard apple", "jackfruit", "head cabbage", "broccoli", "cauliflower", "zucchini", "artichoke", "bell pepper", "cucumber", "hay"),
            "Vehicles" to listOf("car", "truck", "bus", "bicycle", "scooter", "airliner", "ship", "boat", "train", "locomotive", "jeep", "cab", "limousine", "tractor", "tank", "canoe", "submarine", "ambulance", "fire engine", "go-kart", "moped", "minivan", "pickup", "police van", "racer", "tram", "trolleybus", "airship", "warplane", "space shuttle", "wagon", "bobsled", "forklift", "snowmobile", "motorcycle", "unicycle", "tricycle", "liner", "schooner", "catamaran", "trimaran", "yawl", "gondola", "streetcar", "convertible", "beach wagon", "recreational vehicle", "garbage truck", "tow truck", "trailer truck", "fireboat", "lifeboat", "container ship", "aircraft carrier", "balloon"),
            "Textures" to listOf("honeycomb", "chain mail", "maze", "doormat", "prayer rug", "wool", "velvet", "chainlink fence", "window screen", "manhole cover", "stone wall", "brick", "jigsaw puzzle", "crossword", "web site", "dishrag", "envelope"),
            "Characters" to listOf("comic book", "jersey", "sweatshirt", "kimono", "abaya", "cloak", "gown", "miniskirt", "cardigan", "bonnet", "wig", "sombrero", "cowboy hat", "groom", "scuba diver", "ballplayer", "bikini", "maillot", "suit", "lab coat", "apron", "military uniform", "academic gown", "poncho", "pajama", "sarong", "trench coat", "fur coat", "mask", "ski mask", "teddy"),
        )

        fun mapCategory(labels: List<Pair<String, Float>>, idx: List<Int>): Pair<String, Float> {
            for ((i, lab) in labels.withIndex()) {
                val name = lab.first.lowercase()
                if (idx.getOrNull(i)?.let { it in 0..397 } == true) return "Animals" to lab.second
                for ((cat, words) in keywords) if (words.any { name.contains(it) }) return cat to lab.second
            }
            return "Props" to (labels.firstOrNull()?.second ?: 0f)
        }

        fun cosine(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return 0f
            var d = 0f; var na = 0f; var nb = 0f
            for (i in a.indices) { d += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
            return d / (sqrt(na) * sqrt(nb) + 1e-6f)
        }

        fun dHash(b: Bitmap): Long {
            val s = Bitmap.createScaledBitmap(b, 9, 8, true)
            var h = 0L
            for (y in 0 until 8) for (x in 0 until 8) {
                fun l(c: Int) = Color.red(c) * 3 + Color.green(c) * 6 + Color.blue(c)
                if (l(s.getPixel(x, y)) > l(s.getPixel(x + 1, y))) h = h or (1L shl (y * 8 + x))
            }
            return h
        }

        fun hamming(a: Long, b: Long) = java.lang.Long.bitCount(a xor b)

        /** k-means (k=5) dominant colours. */
        fun palette(b: Bitmap): List<Int> {
            val s = Bitmap.createScaledBitmap(b, 32, 32, true)
            val px = IntArray(32 * 32); s.getPixels(px, 0, 32, 0, 0, 32, 32)
            val pts = px.filter { Color.alpha(it) > 128 }.ifEmpty { return emptyList() }
            var cent = (0 until 5).map { pts[(it * pts.size / 5).coerceAtMost(pts.lastIndex)] }.map { floatArrayOf(Color.red(it).toFloat(), Color.green(it).toFloat(), Color.blue(it).toFloat()) }
            var counts = IntArray(5)
            repeat(8) {
                val sums = Array(5) { FloatArray(3) }; counts = IntArray(5)
                for (c in pts) {
                    val r = Color.red(c); val g = Color.green(c); val bl = Color.blue(c)
                    var best = 0; var bd = Float.MAX_VALUE
                    for ((i, ce) in cent.withIndex()) { val d = (ce[0] - r) * (ce[0] - r) + (ce[1] - g) * (ce[1] - g) + (ce[2] - bl) * (ce[2] - bl); if (d < bd) { bd = d; best = i } }
                    sums[best][0] += r.toFloat(); sums[best][1] += g.toFloat(); sums[best][2] += bl.toFloat(); counts[best]++
                }
                cent = cent.mapIndexed { i, c -> if (counts[i] == 0) c else floatArrayOf(sums[i][0] / counts[i], sums[i][1] / counts[i], sums[i][2] / counts[i]) }
            }
            return cent.indices.sortedByDescending { counts[it] }.filter { counts[it] > 0 }.map { Color.rgb(cent[it][0].toInt(), cent[it][1].toInt(), cent[it][2].toInt()) }
        }

        fun colorName(c: Int): String {
            val hsv = FloatArray(3); Color.colorToHSV(c, hsv)
            val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2])
            return when {
                v < 0.18f -> "black"
                s < 0.12f && v > 0.85f -> "white"
                s < 0.15f -> "gray"
                h < 15 || h >= 345 -> if (v < 0.55f) "brown" else "red"
                h < 40 -> if (v < 0.6f) "brown" else "orange"
                h < 65 -> "yellow"
                h < 165 -> "green"
                h < 195 -> "cyan"
                h < 255 -> "blue"
                h < 290 -> "purple"
                else -> "pink"
            }
        }

        fun style(b: Bitmap): String {
            val s = Bitmap.createScaledBitmap(b, 64, 64, true)
            val px = IntArray(64 * 64); s.getPixels(px, 0, 64, 0, 0, 64, 64)
            var white = 0; var satSum = 0f
            val uniq = HashSet<Int>()
            val hsv = FloatArray(3)
            for (c in px) {
                Color.colorToHSV(c, hsv); satSum += hsv[1]
                if (hsv[2] > 0.9f && hsv[1] < 0.1f) white++
                uniq += (c shr 4) and 0x0F0F0F
            }
            val sat = satSum / px.size
            return when {
                sat < 0.08f && white > px.size * 0.55f -> "Line art"
                uniq.size < 140 -> "Flat color"
                else -> "Painted / Photo"
            }
        }

        fun isSimilarColor(a: Int, b: Int) = abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b)) < 120
    }
}
