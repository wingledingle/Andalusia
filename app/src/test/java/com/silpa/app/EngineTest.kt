package com.silpa.app

import android.graphics.Bitmap
import android.graphics.Color
import com.silpa.engine.BrushLibrary
import com.silpa.engine.Compositor
import com.silpa.engine.Document
import com.silpa.engine.GifEncoder
import com.silpa.engine.History
import com.silpa.engine.Ops
import com.silpa.engine.Psd
import com.silpa.engine.Selection
import com.silpa.engine.StrokeEngine
import com.silpa.engine.StrokePoint
import com.silpa.engine.Symmetry
import com.silpa.engine.SymmetryKind
import com.silpa.feature.studio.renderBrushPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EngineTest {
    private fun setup(w: Int = 600, h: Int = 400): Triple<Document, Compositor, StrokeEngine> {
        val d = Document(w, h); d.layers += d.newLayer()
        val s = Selection(w, h); val c = Compositor(d, s)
        return Triple(d, c, StrokeEngine(d, s, c))
    }

    private fun painted(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }.count { Color.alpha(it) > 0 }

    @Test fun strokeUndoRedo() {
        val (d, c, e) = setup()
        val h = History(1L shl 30)
        val brush = BrushLibrary.byId("Inking/Studio Pen")!!.copy(size = 20f)
        e.begin(d.layers[0], brush, Color.RED, Symmetry(), 0f, StrokePoint(50f, 50f, 1f))
        for (i in 1..50) e.add(StrokePoint(50f + i * 8, 50f + i * 4, 1f, 0f, i * 10L))
        h.push(e.end()!!)
        val after = painted(c.renderFull(paper = false))
        assertTrue("stroke should paint pixels", after > 2000)
        h.undo(); c.invalidateAll()
        assertEquals(0, painted(c.renderFull(paper = false)))
        h.redo(); c.invalidateAll()
        assertEquals(after, painted(c.renderFull(paper = false)))
    }

    @Test fun symmetryMirrors() {
        val (d, c, e) = setup()
        e.begin(d.layers[0], BrushLibrary.byId("Inking/Monoline")!!, Color.BLACK, Symmetry(SymmetryKind.Vertical, cx = 300f, cy = 200f), 0f, StrokePoint(100f, 100f))
        for (i in 1..20) e.add(StrokePoint(100f, 100f + i * 5, 1f, 0f, i * 10L))
        e.end()
        val full = c.renderFull(paper = false)
        assertTrue(Color.alpha(full.getPixel(100, 150)) > 0)
        assertTrue("mirror side painted", Color.alpha(full.getPixel(500, 150)) > 0)
    }

    @Test fun everyBrushRenders() {
        val failures = BrushLibrary.all.mapNotNull { b -> runCatching { renderBrushPreview(b) }.exceptionOrNull()?.let { "${b.id}: $it" } }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        val blank = BrushLibrary.all.filter { b -> painted(renderBrushPreview(b)) == 0 }.map { it.id }
        assertTrue("brushes that drew nothing: $blank", blank.isEmpty())
    }

    @Test fun floodFillAndPsdRoundTrip() {
        val (d, c, _) = setup(300, 200)
        val mask = Ops.floodMask(IntArray(300 * 200), 300, 200, 10, 10, 10, 0)
        Ops.fillMask("Fill", d.layers[0], d, Selection(300, 200), mask, Color.BLUE)
        d.layers += d.newLayer("Top").also { it.opacity = 0.5f }
        val flat = c.renderFull()
        val out = ByteArrayOutputStream(); Psd.write(d, flat, out)
        val back = Psd.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(300, back.width); assertEquals(2, back.layers.size)
        assertEquals("Top", back.layers[1].name)
        assertEquals(0.5f, back.layers[1].opacity, 0.01f)
        assertEquals(Color.BLUE, back.layers[0].toBitmap().getPixel(150, 100))
    }

    @Test fun gifEncodes() {
        val frames = (0 until 3).map { i -> Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(listOf(Color.RED, Color.GREEN, Color.BLUE)[i]) } }
        val out = ByteArrayOutputStream()
        GifEncoder(out, 64, 64, 100, true).apply { buildPalette(frames); frames.forEach { addFrame(it) }; finish() }
        val bytes = out.toByteArray()
        assertEquals("GIF89a", String(bytes, 0, 6)); assertEquals(0x3B, bytes.last().toInt())
    }
}
