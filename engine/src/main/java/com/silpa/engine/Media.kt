package com.silpa.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object Export {
    fun image(bmp: Bitmap, format: Bitmap.CompressFormat, quality: Int, out: OutputStream) {
        val src = if (format == Bitmap.CompressFormat.JPEG) Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(bmp, 0f, 0f, null) }
        } else bmp
        src.compress(format, quality, out)
    }

    fun pdf(bmp: Bitmap, out: OutputStream) {
        val pdf = PdfDocument()
        val scale = minOf(1f, 14400f / maxOf(bmp.width, bmp.height))
        val w = (bmp.width * scale).toInt(); val h = (bmp.height * scale).toInt()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, 1).create())
        page.canvas.drawBitmap(bmp, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        pdf.finishPage(page); pdf.writeTo(out); pdf.close()
    }
}

/** Animated GIF encoder (LZW), with a per-animation 256-colour palette built from all frames. */
class GifEncoder(private val out: OutputStream, private val width: Int, private val height: Int, private val delayMs: Int, private val loop: Boolean) {
    private var palette = IntArray(256)
    private var lookup = HashMap<Int, Int>()
    private var started = false

    fun buildPalette(frames: List<Bitmap>) {
        val hist = HashMap<Int, Int>()
        for (f in frames) {
            val px = IntArray(f.width * f.height); f.getPixels(px, 0, f.width, 0, 0, f.width, f.height)
            var i = 0
            while (i < px.size) { val c = px[i]; val q = ((c shr 19) and 31 shl 10) or ((c shr 11) and 31 shl 5) or ((c shr 3) and 31); hist[q] = (hist[q] ?: 0) + 1; i += 3 }
        }
        val top = hist.entries.sortedByDescending { it.value }.take(255).map { it.key }
        palette = IntArray(256) { i ->
            if (i < top.size) { val q = top[i]; Color.rgb(((q shr 10) and 31) * 255 / 31, ((q shr 5) and 31) * 255 / 31, (q and 31) * 255 / 31) } else 0
        }
        lookup.clear()
    }

    private fun nearest(c: Int): Int {
        val key = c and 0xF8F8F8
        return lookup.getOrPut(key) {
            var best = 0; var bd = Int.MAX_VALUE
            val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
            for (i in palette.indices) {
                val p = palette[i]
                val d = (Color.red(p) - r).let { it * it * 2 } + (Color.green(p) - g).let { it * it * 4 } + (Color.blue(p) - b).let { it * it * 3 }
                if (d < bd) { bd = d; best = i }
            }
            best
        }
    }

    private fun short(v: Int) { out.write(v and 255); out.write((v shr 8) and 255) }

    fun addFrame(frame: Bitmap) {
        if (!started) {
            started = true
            out.write("GIF89a".toByteArray()); short(width); short(height)
            out.write(0xF7); out.write(0); out.write(0)
            for (c in palette) { out.write(Color.red(c)); out.write(Color.green(c)); out.write(Color.blue(c)) }
            if (loop) { out.write(byteArrayOf(0x21, 0xFF.toByte(), 11)); out.write("NETSCAPE2.0".toByteArray()); out.write(byteArrayOf(3, 1, 0, 0, 0)) }
        }
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0)); short(delayMs / 10); out.write(0); out.write(0)
        out.write(0x2C); short(0); short(0); short(width); short(height); out.write(0)
        val px = IntArray(width * height); frame.getPixels(px, 0, width, 0, 0, width, height)
        val idx = ByteArray(px.size) { i ->
            val c = px[i]; val a = c ushr 24
            val solid = if (a < 255) { val f = a / 255f; Color.rgb((Color.red(c) * f + 255 * (1 - f)).toInt(), (Color.green(c) * f + 255 * (1 - f)).toInt(), (Color.blue(c) * f + 255 * (1 - f)).toInt()) } else c
            nearest(solid).toByte()
        }
        lzw(idx)
    }

    fun finish() { out.write(0x3B); out.flush() }

    private fun lzw(data: ByteArray) {
        val minCode = 8
        out.write(minCode)
        val block = ByteArray(255); var blockLen = 0
        var acc = 0; var bits = 0
        fun emitByte(b: Int) { block[blockLen++] = b.toByte(); if (blockLen == 255) { out.write(255); out.write(block, 0, 255); blockLen = 0 } }
        var codeSize = minCode + 1
        fun emit(code: Int) { acc = acc or (code shl bits); bits += codeSize; while (bits >= 8) { emitByte(acc and 255); acc = acc ushr 8; bits -= 8 } }
        val clear = 1 shl minCode; val eoi = clear + 1
        val dict = HashMap<Int, Int>()
        var next = eoi + 1
        emit(clear)
        var prefix = data[0].toInt() and 255
        for (i in 1 until data.size) {
            val c = data[i].toInt() and 255
            val key = (prefix shl 8) or c
            val found = dict[key]
            if (found != null) { prefix = found; continue }
            emit(prefix)
            if (next < 4096) {
                dict[key] = next++
                if (next > (1 shl codeSize) && codeSize < 12) codeSize++
            } else { emit(clear); dict.clear(); next = eoi + 1; codeSize = minCode + 1 }
            prefix = c
        }
        emit(prefix); emit(eoi)
        if (bits > 0) emitByte(acc and 255)
        if (blockLen > 0) { out.write(blockLen); out.write(block, 0, blockLen) }
        out.write(0)
    }
}

/** H.264 MP4 encoder fed with bitmaps (timelapse + animation export). */
class Mp4Encoder(file: File, width: Int, height: Int, private val fps: Int) {
    val w = width / 16 * 16
    val h = height / 16 * 16
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var track = -1
    private var frame = 0L
    private val info = MediaCodec.BufferInfo()

    init {
        val f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, (w * h * fps * 0.15).toInt().coerceIn(1_000_000, 20_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
    }

    fun add(bmp: Bitmap) {
        val scaled = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(scaled).apply {
            drawColor(Color.WHITE)
            val s = minOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
            val dw = bmp.width * s; val dh = bmp.height * s
            drawBitmap(bmp, null, RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val px = IntArray(w * h); scaled.getPixels(px, 0, w, 0, 0, w, h); scaled.recycle()
        var idx = -1
        while (idx < 0) { idx = codec.dequeueInputBuffer(10_000); drain(false) }
        val img = codec.getInputImage(idx)!!
        val yP = img.planes[0]; val uP = img.planes[1]; val vP = img.planes[2]
        for (y in 0 until h) for (x in 0 until w) {
            val c = px[y * w + x]; val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            yP.buffer.put(y * yP.rowStride + x * yP.pixelStride, (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte())
            if (y % 2 == 0 && x % 2 == 0) {
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128; val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                uP.buffer.put((y / 2) * uP.rowStride + (x / 2) * uP.pixelStride, u.toByte())
                vP.buffer.put((y / 2) * vP.rowStride + (x / 2) * vP.pixelStride, v.toByte())
            }
        }
        codec.queueInputBuffer(idx, 0, w * h * 3 / 2, frame * 1_000_000L / fps, 0)
        frame++
        drain(false)
    }

    private fun drain(end: Boolean) {
        while (true) {
            val i = codec.dequeueOutputBuffer(info, if (end) 10_000 else 0)
            when {
                i == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end) return
                i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start() }
                i >= 0 -> {
                    val buf = codec.getOutputBuffer(i)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && track >= 0) muxer.writeSampleData(track, buf, info)
                    codec.releaseOutputBuffer(i, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    fun finish() {
        val idx = codec.dequeueInputBuffer(100_000)
        if (idx >= 0) codec.queueInputBuffer(idx, 0, 0, frame * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(true)
        codec.stop(); codec.release()
        if (track >= 0) muxer.stop()
        muxer.release()
    }
}

/** Best-effort reader for Photoshop .abr brush packs (sampled brushes, v1/v2 and v6). */
object Abr {
    fun read(input: InputStream): List<Pair<String, Bitmap>> {
        val bytes = input.readBytes()
        val d = DataInputStream(bytes.inputStream())
        val version = d.readShort().toInt()
        val out = mutableListOf<Pair<String, Bitmap>>()
        if (version == 1 || version == 2) {
            val count = d.readShort().toInt()
            repeat(count) {
                val type = d.readShort().toInt(); val size = d.readInt()
                if (type != 2) { d.skipBytes(size); return@repeat }
                val start = bytes.size - d.available()
                d.skipBytes(4); d.skipBytes(2)
                var name = "Brush ${out.size + 1}"
                if (version == 2) { val n = d.readInt(); val sb = StringBuilder(); repeat(n) { sb.append(d.readChar()) }; name = sb.toString().trimEnd('\u0000') }
                d.skipBytes(1)
                val bmp = readSample(d, version)
                bmp?.let { out += name to it }
                val consumed = bytes.size - d.available() - start
                if (consumed < size) d.skipBytes(size - consumed)
            }
        } else if (version == 6 || version == 10) {
            val sub = d.readShort().toInt()
            while (d.available() > 12) {
                val sig = String(ByteArray(4).also { d.readFully(it) }); val key = String(ByteArray(4).also { d.readFully(it) })
                val len = d.readInt()
                if (sig != "8BIM") break
                if (key != "samp") { d.skipBytes((len + 3) and 3.inv()); continue }
                val end = bytes.size - d.available() + len
                while (bytes.size - d.available() < end - 4) {
                    val bl = d.readInt(); val bs = bytes.size - d.available()
                    runCatching {
                        val idLen = d.readUnsignedByte(); d.skipBytes(idLen)
                        d.skipBytes(if (sub == 1) 10 else 264)
                        val bmp = readRect(d)
                        bmp?.let { out += "Brush ${out.size + 1}" to it }
                    }
                    val consumed = bytes.size - d.available() - bs
                    val pad = (bl + 3) and 3.inv()
                    if (consumed < pad) d.skipBytes(pad - consumed)
                }
                break
            }
        }
        return out
    }

    private fun readSample(d: DataInputStream, version: Int): Bitmap? = readRect(d)

    private fun readRect(d: DataInputStream): Bitmap? {
        val top = d.readInt(); val left = d.readInt(); val bottom = d.readInt(); val right = d.readInt()
        val depth = d.readShort().toInt(); val comp = d.readUnsignedByte()
        val w = right - left; val h = bottom - top
        if (w <= 0 || h <= 0 || w > 5000 || h > 5000 || depth != 8) return null
        val data = ByteArray(w * h)
        if (comp == 0) d.readFully(data) else {
            val counts = IntArray(h) { d.readUnsignedShort() }
            var off = 0
            for (y in 0 until h) {
                val row = ByteArray(counts[y]); d.readFully(row)
                var i = 0; var x = 0
                while (i < row.size && x < w) {
                    val n = row[i++].toInt()
                    if (n >= 0) { repeat(n + 1) { if (x < w && i < row.size) data[off + x++] = row[i++] } }
                    else if (n != -128) { val v = row[i++]; repeat(1 - n) { if (x < w) data[off + x++] = v } }
                }
                off += w
            }
        }
        val px = IntArray(w * h) { Color.argb(data[it].toInt() and 255, 0, 0, 0) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}
