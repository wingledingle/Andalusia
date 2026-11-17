package com.silpa.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

val BankCategories = listOf(
    "Faces", "Eyes", "Noses", "Mouths", "Ears", "Hands", "Hair", "Characters", "Poses",
    "Houses", "Interiors", "Landscapes", "Plants", "Animals", "Vehicles", "Props", "Textures", "Patterns",
)

data class BankItem(
    val id: String,
    val file: File,
    val category: String,
    val labels: List<String>,
    val colors: List<String>,
    val palette: List<Int>,
    val style: String,
    val hash: Long,
    val embedding: FloatArray?,
    val added: Long,
    val corrected: Boolean,
    val source: String,
    val width: Int,
    val height: Int,
    val parent: String? = null,
) {
    val tags: List<String> get() = (listOf(category, style) + colors + labels.take(3)).distinct()
}

data class SmartFolder(val id: String, val name: String, val category: String?, val query: String, val color: String?)

sealed class ImportResult {
    data class Added(val items: List<BankItem>) : ImportResult()
    data class Duplicate(val of: BankItem) : ImportResult()
    data class Failed(val reason: String) : ImportResult()
}

/** The self-organizing Image Bank. Index is a JSON file; images are stored app-privately. */
class Bank private constructor(private val context: Context) {
    private val dir = File(context.filesDir, "bank").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<BankItem>>(emptyList())
    val items: StateFlow<List<BankItem>> = _items
    private val _folders = MutableStateFlow<List<SmartFolder>>(emptyList())
    val folders: StateFlow<List<SmartFolder>> = _folders
    private val _busy = MutableStateFlow(0)
    val busy: StateFlow<Int> = _busy
    private val analyzer by lazy { Analyzer(context) }
    @Volatile private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (loaded) return@withLock
            if (indexFile.exists()) runCatching {
                val j = JSONObject(indexFile.readText())
                _items.value = j.getJSONArray("items").let { a -> (0 until a.length()).mapNotNull { fromJson(a.getJSONObject(it)) } }
                _folders.value = j.optJSONArray("folders")?.let { a -> (0 until a.length()).map { val o = a.getJSONObject(it)
                    SmartFolder(o.getString("id"), o.getString("name"), o.optString("category").ifBlank { null }, o.optString("query"), o.optString("color").ifBlank { null }) } }.orEmpty()
            }
            loaded = true
        }
    }

    /** Seeds the bank with the bundled CC0 artwork on first launch (pre-labelled, so they also teach the sorter). */
    suspend fun ensureStarter(art: List<Triple<String, Int, String>>) {
        load()
        val flag = File(dir, ".starter"); if (flag.exists()) return
        for ((key, res, cat) in art) {
            val bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeResource(context.resources, res) } ?: continue
            import(bmp, "Silpa starter: $key", cat, splitFaces = false)
        }
        flag.writeText("1")
    }

    private fun examples() = _items.value.filter { it.embedding != null && (it.corrected || it.source.startsWith("Silpa starter")) }.map { it.embedding!! to it.category }

    suspend fun import(bmp: Bitmap, source: String, presetCategory: String? = null, splitFaces: Boolean = true): ImportResult = withContext(Dispatchers.Default) {
        load()
        _busy.value++
        try {
            val a = runCatching { analyzer.analyze(bmp, examples()) }.getOrElse { return@withContext ImportResult.Failed(it.message ?: "Couldn't analyze image") }
            _items.value.firstOrNull { Analyzer.hamming(it.hash, a.hash) <= 3 && it.parent == null }?.let { return@withContext ImportResult.Duplicate(it) }
            val main = save(bmp, a, presetCategory ?: a.category, source, presetCategory != null, null)
            val added = mutableListOf(main)
            if (splitFaces && presetCategory == null) {
                val sx = bmp.width / 640f.coerceAtMost(bmp.width.toFloat()).let { if (it == 0f) 1f else it }
                val scale = maxOf(bmp.width, bmp.height) / minOf(640f, maxOf(bmp.width, bmp.height).toFloat())
                for (face in a.faces) for (part in face) {
                    if (part.category == "Faces" && a.faces.size == 1 && a.category == "Faces") continue
                    val r = android.graphics.Rect((part.rect.left * scale).toInt(), (part.rect.top * scale).toInt(), (part.rect.right * scale).toInt().coerceAtMost(bmp.width), (part.rect.bottom * scale).toInt().coerceAtMost(bmp.height))
                    if (r.width() < 16 || r.height() < 16) continue
                    val crop = Bitmap.createBitmap(bmp, r.left, r.top, r.width(), r.height())
                    val pa = a.copy(palette = Analyzer.palette(crop), colorNames = Analyzer.palette(crop).map(Analyzer::colorName).distinct().take(3), hash = Analyzer.dHash(crop), embedding = null)
                    added += save(crop, pa, part.category, "Cut from ${main.id.take(6)}", false, main.id)
                }
                if (sx < 0) Unit
            }
            ImportResult.Added(added)
        } finally { _busy.value-- }
    }

    private suspend fun save(bmp: Bitmap, a: Analysis, category: String, source: String, corrected: Boolean, parent: String?): BankItem {
        val id = UUID.randomUUID().toString()
        val s = minOf(1f, 2048f / maxOf(bmp.width, bmp.height))
        val scaled = if (s < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true) else bmp
        val f = File(dir, "$id.png")
        withContext(Dispatchers.IO) { FileOutputStream(f).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val item = BankItem(id, f, category, a.labels, a.colorNames, a.palette, a.style, a.hash, a.embedding, System.currentTimeMillis(), corrected, source, scaled.width, scaled.height, parent)
        mutex.withLock { _items.value = listOf(item) + _items.value; persist() }
        return item
    }

    /** Moving an item is a correction: it becomes a labelled example the sorter learns from. */
    suspend fun move(id: String, category: String) = mutex.withLock {
        _items.value = _items.value.map { if (it.id == id) it.copy(category = category, corrected = true) else it }; persist()
    }

    suspend fun delete(ids: Set<String>) = mutex.withLock {
        _items.value.filter { it.id in ids }.forEach { it.file.delete() }
        _items.value = _items.value.filter { it.id !in ids }; persist()
    }

    suspend fun addFolder(f: SmartFolder) = mutex.withLock { _folders.value = _folders.value + f; persist() }
    suspend fun removeFolder(id: String) = mutex.withLock { _folders.value = _folders.value.filter { it.id != id }; persist() }

    fun search(items: List<BankItem>, query: String, category: String?, color: String?): List<BankItem> {
        val q = query.trim().lowercase()
        return items.filter { i ->
            (category == null || i.category == category) && (color == null || color in i.colors) &&
                (q.isEmpty() || i.tags.any { it.lowercase().contains(q) } || i.source.lowercase().contains(q))
        }
    }

    fun similar(of: BankItem, n: Int = 24): List<BankItem> {
        val e = of.embedding
        return if (e != null) _items.value.filter { it.id != of.id && it.embedding != null }.sortedByDescending { Analyzer.cosine(e, it.embedding!!) }.take(n)
        else _items.value.filter { it.id != of.id }.sortedBy { Analyzer.hamming(of.hash, it.hash) }.take(n)
    }

    private fun persist() {
        val j = JSONObject()
        j.put("items", JSONArray().apply { _items.value.forEach { put(toJson(it)) } })
        j.put("folders", JSONArray().apply { _folders.value.forEach { f -> put(JSONObject().put("id", f.id).put("name", f.name).put("category", f.category ?: "").put("query", f.query).put("color", f.color ?: "")) } })
        val tmp = File(dir, "index.json.tmp"); tmp.writeText(j.toString()); tmp.renameTo(indexFile)
    }

    private fun toJson(i: BankItem) = JSONObject().apply {
        put("id", i.id); put("file", i.file.name); put("category", i.category); put("labels", JSONArray(i.labels)); put("colors", JSONArray(i.colors))
        put("palette", JSONArray(i.palette)); put("style", i.style); put("hash", i.hash); put("added", i.added); put("corrected", i.corrected)
        put("source", i.source); put("w", i.width); put("h", i.height); put("parent", i.parent ?: "")
        i.embedding?.let { e -> put("emb", JSONArray().apply { e.forEach { put(it.toDouble()) } }) }
    }

    private fun fromJson(o: JSONObject): BankItem? {
        val f = File(dir, o.getString("file")); if (!f.exists()) return null
        fun strs(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        return BankItem(o.getString("id"), f, o.getString("category"), strs("labels"), strs("colors"),
            o.optJSONArray("palette")?.let { a -> (0 until a.length()).map { a.getInt(it) } }.orEmpty(),
            o.optString("style"), o.optLong("hash"), o.optJSONArray("emb")?.let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } },
            o.optLong("added"), o.optBoolean("corrected"), o.optString("source"), o.optInt("w"), o.optInt("h"), o.optString("parent").ifBlank { null })
    }

    companion object {
        @Volatile private var instance: Bank? = null
        fun get(context: Context): Bank = instance ?: synchronized(this) { instance ?: Bank(context.applicationContext).also { instance = it } }
    }
}
