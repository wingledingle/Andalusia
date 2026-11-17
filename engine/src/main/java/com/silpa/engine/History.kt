package com.silpa.engine

import android.graphics.Bitmap

interface Edit {
    val label: String
    val bytes: Long
    fun undo()
    fun redo()
}

/** Pixel edit: swaps tile bitmaps. Tiles are never shared between before/after, so this is exact. */
class TileEdit(
    override val label: String,
    private val layer: Layer,
    private val before: Map<Long, Bitmap?>,
    private val after: Map<Long, Bitmap?>,
) : Edit {
    override val bytes: Long = (before.values + after.values).sumOf { it?.allocationByteCount?.toLong() ?: 0L }
    override fun undo() { for ((k, b) in before) layer.set(k, b) }
    override fun redo() { for ((k, b) in after) layer.set(k, b) }
}

data class DocState(val layers: List<Pair<Layer, LayerProps>>, val active: Int)

fun Document.snapshot() = DocState(layers.map { it to it.props }, activeIndex)

fun Document.restore(s: DocState) {
    layers.clear()
    for ((l, p) in s.layers) { l.props = p; layers += l }
    activeIndex = s.active
    structureChanged()
}

/** Layer list/property edit (add, delete, reorder, rename, blend, opacity...). */
class StructureEdit(override val label: String, private val doc: Document, private val before: DocState, private val after: DocState) : Edit {
    override val bytes = 256L
    override fun undo() = doc.restore(before)
    override fun redo() = doc.restore(after)
}

class CompoundEdit(override val label: String, private val parts: List<Edit>) : Edit {
    override val bytes = parts.sumOf { it.bytes }
    override fun undo() = parts.asReversed().forEach { it.undo() }
    override fun redo() = parts.forEach { it.redo() }
}

/** Unlimited undo, bounded only by a memory budget. Oldest steps drop first. */
class History(private val budgetBytes: Long) {
    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()
    var onChanged: (() -> Unit)? = null

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()
    val entries: List<String> get() = undoStack.map { it.label }
    val size get() = undoStack.size

    fun push(e: Edit) {
        undoStack.addLast(e); redoStack.clear()
        var total = undoStack.sumOf { it.bytes }
        while (total > budgetBytes && undoStack.size > 1) total -= undoStack.removeFirst().bytes
        onChanged?.invoke()
    }

    fun undo(): Boolean {
        val e = undoStack.removeLastOrNull() ?: return false
        e.undo(); redoStack.addLast(e); onChanged?.invoke(); return true
    }

    fun redo(): Boolean {
        val e = redoStack.removeLastOrNull() ?: return false
        e.redo(); undoStack.addLast(e); onChanged?.invoke(); return true
    }

    /** Jump back to a point in the history panel. */
    fun undoTo(count: Int) { while (undoStack.size > count && undo()) Unit }

    fun clear() { undoStack.clear(); redoStack.clear(); onChanged?.invoke() }
}
