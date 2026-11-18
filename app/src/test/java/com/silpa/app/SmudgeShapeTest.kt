package com.silpa.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.silpa.engine.BrushLibrary
import com.silpa.engine.Compositor
import com.silpa.engine.Document
import com.silpa.engine.Selection
import com.silpa.engine.StrokeEngine
import com.silpa.engine.StrokePoint
import com.silpa.engine.Symmetry
import com.silpa.engine.splitIntoTiles
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SmudgeShapeTest {
    private val outDir = File("build/engine-shots").apply { mkdirs() }

    @Test fun smudgeDragsPaintAndBlends() {
        val w = 600; val h = 300
        val d = Document(w, h); val l = d.newLayer(); d.layers += l
        // Three colour bands to smudge across.
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(src).apply {
            val p = Paint()
            p.color = Color.rgb(220, 40, 60); drawRect(40f, 40f, 200f, 260f, p)
            p.color = Color.rgb(40, 90, 220); drawRect(200f, 40f, 330f, 260f, p)
        }
        l.tiles.putAll(splitIntoTiles(src, w, h))
        val sel = Selection(w, h); val comp = Compositor(d, sel); val eng = StrokeEngine(d, sel, comp)
        val b = BrushLibrary.byId("Smudge/Finger")!!.copy(size = 50f)
        for (row in 0 until 4) {
            val y = 70f + row * 50
            eng.begin(l, b, Color.BLACK, Symmetry(), 0f, StrokePoint(80f, y, 0.9f, 0f, 0))
            for (i in 1..60) eng.add(StrokePoint(80f + i * 7f, y + sin(i / 6.0).toFloat() * 10, 0.9f, 0f, i * 12L))
            eng.end()
        }
        val out = comp.renderFull(paper = true)
        File(outDir, "smudge.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Paint must have been dragged past the right edge of the blue band into empty canvas.
        val pulled = out.getPixel(400, 95)
        assertTrue("smudge should drag paint into empty area: ${Integer.toHexString(pulled)}", Color.red(pulled) < 240 || Color.blue(pulled) < 240)
        // Red should have been carried into the blue band (blending), not just stamped.
        val mixed = out.getPixel(260, 95)
        assertTrue("colours should mix: ${Integer.toHexString(mixed)}", Color.red(mixed) > 60)
    }

    @Test fun shapesRender() {
        val w = 600; val h = 400
        val d = Document(w, h); val l = d.newLayer(); d.layers += l
        val sel = Selection(w, h); val comp = Compositor(d, sel); val eng = StrokeEngine(d, sel, comp)
        val pen = BrushLibrary.byId("Inking/Studio Pen")!!.copy(size = 8f)
        eng.beginShape(l, pen, Color.BLACK, Symmetry()); eng.renderPath(listOf(floatArrayOf(30f, 30f), floatArrayOf(280f, 120f)), false, false); eng.end()
        val curve = (0..48).map { i -> val t = i / 48f; val u = 1 - t; floatArrayOf(u * u * 30 + 2 * u * t * 150 + t * t * 280, u * u * 380 + 2 * u * t * 150 + t * t * 380) }
        eng.beginShape(l, pen, Color.BLUE, Symmetry()); eng.renderPath(curve, false, false); eng.end()
        val hex = (0 until 6).map { i -> val a = Math.PI / 3 * i; floatArrayOf((450 + 90 * Math.cos(a)).toFloat(), (200 + 90 * Math.sin(a)).toFloat()) }
        eng.beginShape(l, pen, Color.rgb(242, 139, 107), Symmetry()); eng.renderPath(hex, true, true); eng.end()
        val out = comp.renderFull(paper = true)
        File(outDir, "shapes.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("line drawn", Color.red(out.getPixel(155, 75)) < 100)
        assertTrue("polygon filled", Color.green(out.getPixel(450, 200)) in 100..180)
    }
}
