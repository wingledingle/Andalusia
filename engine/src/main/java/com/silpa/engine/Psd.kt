package com.silpa.engine

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Layered Photoshop (.psd) import and export: 8-bit RGB, names, opacity, visibility, blend modes, clipping. */
object Psd {

    fun write(doc: Document, flattened: Bitmap, out: OutputStream) {
        val w = doc.width; val h = doc.height
        val o = DataOutputStream(out.buffered())
        o.writeBytes("8BPS"); o.writeShort(1); o.write(ByteArray(6)); o.writeShort(3)
        o.writeInt(h); o.writeInt(w); o.writeShort(8); o.writeShort(3)
        o.writeInt(0); o.writeInt(0)

        val layerInfo = ByteArrayOutputStream(); val li = DataOutputStream(layerInfo)
        val channelData = ByteArrayOutputStream()
        val layers = doc.layers
        li.writeShort(layers.size)
        for (l in layers) {
            val px = IntArray(w * h); l.toBitmap().apply { getPixels(px, 0, w, 0, 0, w, h); recycle() }
            val chans = listOf(-1 to { c: Int -> c ushr 24 }, 0 to { c: Int -> (c shr 16) and 255 }, 1 to { c: Int -> (c shr 8) and 255 }, 2 to { c: Int -> c and 255 })
            val encoded = chans.map { (id, f) -> id to rleChannel(px, w, h, f) }
            li.writeInt(0); li.writeInt(0); li.writeInt(h); li.writeInt(w)
            li.writeShort(4)
            for ((id, data) in encoded) { li.writeShort(id); li.writeInt(data.size + 2) }
            li.writeBytes("8BIM"); li.writeBytes(l.blend.psd)
            li.writeByte((l.opacity * 255).toInt()); li.writeByte(if (l.clipping) 1 else 0)
            li.writeByte(if (l.visible) 0 else 2); li.writeByte(0)
            val name = l.name.toByteArray(Charsets.UTF_8).take(255).toByteArray()
            var nameLen = 1 + name.size; while (nameLen % 4 != 0) nameLen++
            li.writeInt(4 + 4 + nameLen)
            li.writeInt(0); li.writeInt(0)
            li.writeByte(name.size); li.write(name); repeat(nameLen - 1 - name.size) { li.writeByte(0) }
            for ((_, data) in encoded) { channelData.write(byteArrayOf(0, 1)); channelData.write(data) }
        }
        val lib = layerInfo.toByteArray() + channelData.toByteArray()
        val padded = if (lib.size % 2 == 1) lib + 0 else lib
        o.writeInt(4 + padded.size + 4)
        o.writeInt(padded.size); o.write(padded)
        o.writeInt(0)

        val merged = IntArray(w * h); flattened.getPixels(merged, 0, w, 0, 0, w, h)
        o.writeShort(1)
        val rows = listOf<(Int) -> Int>({ (it shr 16) and 255 }, { (it shr 8) and 255 }, { it and 255 }).map { f -> rleRows(merged, w, h, f) }
        for (r in rows) for (row in r) o.writeShort(row.size)
        for (r in rows) for (row in r) o.write(row)
        o.flush()
    }

    private fun rleRows(px: IntArray, w: Int, h: Int, f: (Int) -> Int): List<ByteArray> = (0 until h).map { y ->
        val row = ByteArray(w) { x -> f(px[y * w + x]).toByte() }
        packBits(row)
    }

    private fun rleChannel(px: IntArray, w: Int, h: Int, f: (Int) -> Int): ByteArray {
        val rows = rleRows(px, w, h, f)
        val b = ByteArrayOutputStream(); val d = DataOutputStream(b)
        rows.forEach { d.writeShort(it.size) }; rows.forEach { d.write(it) }
        return b.toByteArray()
    }

    fun packBits(src: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(); var i = 0
        while (i < src.size) {
            var run = 1
            while (i + run < src.size && run < 128 && src[i + run] == src[i]) run++
            if (run > 1) { out.write(1 - run); out.write(src[i].toInt()); i += run; continue }
            var lit = 1
            while (i + lit < src.size && lit < 128 && !(i + lit + 1 < src.size && src[i + lit] == src[i + lit + 1])) lit++
            out.write(lit - 1); out.write(src, i, lit); i += lit
        }
        return out.toByteArray()
    }

    private fun unpack(d: DataInputStream, out: ByteArray, len: Int) {
        var i = 0
        while (i < len) {
            val n = d.readByte().toInt()
            if (n >= 0) { d.readFully(out, i, n + 1); i += n + 1 }
            else if (n != -128) { val v = d.readByte(); repeat(1 - n) { if (i < len) out[i++] = v } }
        }
    }

    /** Reads a PSD into a new document. Only 8-bit RGB files are supported. */
    fun read(input: InputStream): Document {
        val d = DataInputStream(input.buffered())
        require(String(ByteArray(4).also { d.readFully(it) }) == "8BPS") { "Not a PSD file" }
        require(d.readShort().toInt() == 1) { "PSB (large document) files aren't supported" }
        d.skipBytes(6)
        val channels = d.readShort().toInt(); val h = d.readInt(); val w = d.readInt()
        val depth = d.readShort().toInt(); val mode = d.readShort().toInt()
        require(depth == 8 && mode == 3) { "Only 8-bit RGB PSD files are supported" }
        d.skipBytes(d.readInt()); d.skipBytes(d.readInt())
        val doc = Document(w, h)
        val lmLen = d.readInt()
        val main = d
        if (lmLen > 0) {
            val section = ByteArray(lmLen).also { main.readFully(it) }
            val d = DataInputStream(section.inputStream())
            val liLen = d.readInt()
            if (liLen > 0) {
                val count = kotlin.math.abs(d.readShort().toInt())
                class Rec(val t: Int, val l: Int, val b: Int, val r: Int, val ch: List<Pair<Int, Int>>, val blend: String, val op: Int, val clip: Int, val flags: Int, val name: String)
                val recs = (0 until count).map {
                    val t = d.readInt(); val l = d.readInt(); val b = d.readInt(); val r = d.readInt()
                    val n = d.readShort().toInt()
                    val ch = (0 until n).map { d.readShort().toInt() to d.readInt() }
                    d.skipBytes(4); val blend = String(ByteArray(4).also { d.readFully(it) })
                    val op = d.readUnsignedByte(); val clip = d.readUnsignedByte(); val flags = d.readUnsignedByte(); d.skipBytes(1)
                    val extra = d.readInt(); val buf = ByteArray(extra); d.readFully(buf)
                    val ed = DataInputStream(buf.inputStream())
                    ed.skipBytes(ed.readInt()); ed.skipBytes(ed.readInt())
                    val nl = ed.readUnsignedByte(); val nb = ByteArray(nl); ed.readFully(nb)
                    Rec(t, l, b, r, ch, blend, op, clip, flags, String(nb, Charsets.UTF_8))
                }
                for (rec in recs) {
                    val lw = rec.r - rec.l; val lh = rec.b - rec.t
                    val planes = HashMap<Int, ByteArray>()
                    for ((id, len) in rec.ch) {
                        val comp = d.readShort().toInt()
                        if (lw <= 0 || lh <= 0) { d.skipBytes(len - 2); continue }
                        val data = ByteArray(lw * lh)
                        when (comp) {
                            0 -> d.readFully(data)
                            1 -> { val counts = IntArray(lh) { d.readUnsignedShort() }; var off = 0
                                for (y in 0 until lh) { val row = ByteArray(lw); unpack(DataInputStream(ByteArray(counts[y]).also { d.readFully(it) }.inputStream()), row, lw); System.arraycopy(row, 0, data, off, lw); off += lw } }
                            else -> { d.skipBytes(len - 2); continue }
                        }
                        planes[id] = data
                    }
                    if (lw <= 0 || lh <= 0 || planes[0] == null) continue
                    val px = IntArray(lw * lh) { i ->
                        val a = planes[-1]?.get(i)?.toInt()?.and(255) ?: 255
                        Color.argb(a, planes[0]!![i].toInt() and 255, (planes[1]?.get(i)?.toInt() ?: 0) and 255, (planes[2]?.get(i)?.toInt() ?: 0) and 255)
                    }
                    val full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    android.graphics.Canvas(full).drawBitmap(Bitmap.createBitmap(px, lw, lh, Bitmap.Config.ARGB_8888), rec.l.toFloat(), rec.t.toFloat(), null)
                    val layer = doc.newLayer(rec.name.ifBlank { "Layer" })
                    layer.tiles.putAll(splitIntoTiles(full, w, h)); full.recycle()
                    layer.opacity = rec.op / 255f; layer.visible = rec.flags and 2 == 0; layer.clipping = rec.clip == 1
                    layer.blend = SilpaBlend.fromPsd(rec.blend)
                    doc.layers += layer
                }
                if (doc.layers.isNotEmpty()) { doc.activeIndex = doc.layers.lastIndex; return doc }
            }
        }
        return readMerged(doc, d, w, h, channels)
    }

    private fun readMerged(doc: Document, d: DataInputStream, w: Int, h: Int, channels: Int): Document {
        val comp = d.readShort().toInt()
        val planes = Array(channels) { ByteArray(w * h) }
        if (comp == 1) {
            val counts = IntArray(channels * h) { d.readUnsignedShort() }
            for (c in 0 until channels) for (y in 0 until h) { val row = ByteArray(w); unpack(DataInputStream(ByteArray(counts[c * h + y]).also { d.readFully(it) }.inputStream()), row, w); System.arraycopy(row, 0, planes[c], y * w, w) }
        } else for (c in 0 until channels) d.readFully(planes[c])
        val px = IntArray(w * h) { i -> Color.argb(if (channels > 3) planes[3][i].toInt() and 255 else 255, planes[0][i].toInt() and 255, planes[1][i].toInt() and 255, planes[2][i].toInt() and 255) }
        val layer = doc.newLayer("Background")
        layer.tiles.putAll(splitIntoTiles(Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888), w, h))
        doc.layers += layer
        return doc
    }
}
