package com.silpa.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ProjectInfo(val id: String, val name: String, val width: Int, val height: Int, val modified: Long, val layers: Int, val thumb: File, val animation: Boolean)

/**
 * Projects live as folders: project.json + one PNG per non-empty tile. Saves are incremental:
 * only tiles touched since the last save are rewritten, so autosave after every stroke is cheap.
 */
class ProjectStore(private val root: File) {
    init { root.mkdirs() }

    fun dir(id: String) = File(root, id)
    fun thumb(id: String) = File(dir(id), "thumb.webp")
    fun timelapseDir(id: String) = File(dir(id), "timelapse").apply { mkdirs() }

    fun list(): List<ProjectInfo> = (root.listFiles() ?: emptyArray()).mapNotNull { d ->
        runCatching {
            val j = JSONObject(File(d, "project.json").readText())
            ProjectInfo(d.name, j.getString("name"), j.getInt("width"), j.getInt("height"),
                File(d, "project.json").lastModified(), j.getJSONArray("layers").length(), File(d, "thumb.webp"),
                j.optJSONObject("animation")?.optBoolean("enabled") ?: false)
        }.getOrNull()
    }.sortedByDescending { it.modified }

    fun create(name: String, w: Int, h: Int, paper: Int = Color.WHITE, transparent: Boolean = false): String {
        val id = UUID.randomUUID().toString()
        val doc = Document(w, h).apply { this.name = name; paperColor = paper; paperVisible = !transparent; layers += newLayer("Layer 1") }
        dir(id).mkdirs()
        File(dir(id), "project.json").writeText(meta(doc).toString())
        return id
    }

    private fun meta(doc: Document) = JSONObject().apply {
        put("version", 1); put("name", doc.name); put("width", doc.width); put("height", doc.height)
        put("paper", doc.paperColor); put("paperVisible", doc.paperVisible); put("active", doc.activeIndex)
        put("animation", JSONObject().apply {
            put("enabled", doc.animation.enabled); put("fps", doc.animation.fps); put("onion", doc.animation.onionSkins)
            put("onionOpacity", doc.animation.onionOpacity.toDouble()); put("loop", doc.animation.loop); put("frame", doc.currentFrame)
        })
        put("layers", JSONArray().apply {
            doc.layers.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id); put("name", l.name); put("visible", l.visible); put("opacity", l.opacity.toDouble())
                    put("blend", l.blend.name); put("alphaLock", l.alphaLock); put("clipping", l.clipping)
                    put("locked", l.locked); put("reference", l.reference)
                })
            }
        })
    }

    fun load(id: String): Document {
        val d = dir(id)
        val j = JSONObject(File(d, "project.json").readText())
        val doc = Document(j.getInt("width"), j.getInt("height"))
        doc.name = j.getString("name"); doc.paperColor = j.optInt("paper", Color.WHITE); doc.paperVisible = j.optBoolean("paperVisible", true)
        j.optJSONObject("animation")?.let { a ->
            doc.animation = AnimationSettings(a.optBoolean("enabled"), a.optInt("fps", 12), a.optInt("onion", 1), a.optDouble("onionOpacity", 0.35).toFloat(), a.optBoolean("loop", true))
            doc.currentFrame = a.optInt("frame", 0)
        }
        val arr = j.getJSONArray("layers")
        val opts = BitmapFactory.Options().apply { inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val l = Layer(o.getString("id"), o.getString("name"), doc.width, doc.height)
            l.visible = o.optBoolean("visible", true); l.opacity = o.optDouble("opacity", 1.0).toFloat()
            l.blend = runCatching { SilpaBlend.valueOf(o.optString("blend", "Normal")) }.getOrDefault(SilpaBlend.Normal)
            l.alphaLock = o.optBoolean("alphaLock"); l.clipping = o.optBoolean("clipping"); l.locked = o.optBoolean("locked"); l.reference = o.optBoolean("reference")
            File(d, "layers/${l.id}").listFiles()?.forEach { f ->
                val parts = f.nameWithoutExtension.split('_')
                if (parts.size == 2) BitmapFactory.decodeFile(f.path, opts)?.let { l.tiles[tileKey(parts[0].toInt(), parts[1].toInt())] = it }
            }
            doc.layers += doc.attach(l)
        }
        if (doc.layers.isEmpty()) doc.layers += doc.newLayer("Layer 1")
        doc.activeIndex = j.optInt("active", doc.layers.lastIndex).coerceIn(0, doc.layers.lastIndex)
        return doc
    }

    /** Snapshot taken on the UI thread; [write] then runs on a background thread. */
    class SaveJob(val id: String, val meta: String, val tiles: List<Triple<String, Long, Bitmap?>>, val layerIds: Set<String>, val thumb: Bitmap?)

    fun prepareSave(doc: Document, id: String, dirty: Map<String, Set<Long>>, fullLayers: Set<String>, thumb: Bitmap?): SaveJob {
        val list = ArrayList<Triple<String, Long, Bitmap?>>()
        for (l in doc.layers) {
            val keys = if (l.id in fullLayers || !File(dir(id), "layers/${l.id}").exists()) l.tiles.keys.toSet() + (dirty[l.id] ?: emptySet()) else dirty[l.id] ?: continue
            for (k in keys) list += Triple(l.id, k, l.tiles[k]?.copy(Bitmap.Config.ARGB_8888, false))
        }
        return SaveJob(id, meta(doc).toString(), list, doc.layers.map { it.id }.toSet(), thumb)
    }

    fun write(job: SaveJob) {
        val d = dir(job.id); d.mkdirs()
        for ((lid, k, b) in job.tiles) {
            val ld = File(d, "layers/$lid").apply { mkdirs() }
            val f = File(ld, "${keyX(k)}_${keyY(k)}.png")
            if (b == null) f.delete() else { FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle() }
        }
        File(d, "layers").listFiles()?.forEach { if (it.name !in job.layerIds) it.deleteRecursively() }
        val tmp = File(d, "project.json.tmp"); tmp.writeText(job.meta); tmp.renameTo(File(d, "project.json"))
        job.thumb?.let { t -> FileOutputStream(thumb(job.id)).use { t.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, it) } }
    }

    /** Writes a whole in-memory document (PSD import, photo canvas) as a new project. */
    fun saveNew(doc: Document, thumb: Bitmap?): String {
        val id = UUID.randomUUID().toString()
        dir(id).mkdirs()
        write(prepareSave(doc, id, emptyMap(), doc.layers.map { it.id }.toSet(), thumb))
        return id
    }

    fun delete(id: String) = dir(id).deleteRecursively()

    fun rename(id: String, name: String) {
        val f = File(dir(id), "project.json"); val j = JSONObject(f.readText()); j.put("name", name); f.writeText(j.toString())
    }

    fun duplicate(id: String): String {
        val nid = UUID.randomUUID().toString()
        dir(id).copyRecursively(dir(nid))
        val f = File(dir(nid), "project.json"); val j = JSONObject(f.readText()); j.put("name", j.getString("name") + " copy"); f.writeText(j.toString())
        return nid
    }

    /** .silpa = zipped project folder, for sharing/backups. */
    fun exportPackage(id: String, out: java.io.OutputStream) {
        val d = dir(id)
        ZipOutputStream(out).use { z ->
            d.walkTopDown().filter { it.isFile && !it.path.contains("/timelapse/") }.forEach { f ->
                z.putNextEntry(ZipEntry(f.relativeTo(d).path)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
    }

    fun importPackage(input: java.io.InputStream): String {
        val nid = UUID.randomUUID().toString(); val d = dir(nid); d.mkdirs()
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val f = File(d, e.name).canonicalFile
                require(f.path.startsWith(d.canonicalPath)) { "Bad package entry" }
                if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) } }
                e = z.nextEntry
            }
        }
        require(File(d, "project.json").exists()) { "Not a Silpa project" }
        return nid
    }
}
