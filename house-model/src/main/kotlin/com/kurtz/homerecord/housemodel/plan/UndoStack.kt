package com.kurtz.homerecord.housemodel.plan

/** Snapshot undo/redo. Holds up to [capacity] undo steps (SPEC asks for at least 50). */
class UndoStack<T>(initial: T, private val capacity: Int = 100) {
    private val undo = ArrayDeque<T>()
    private val redo = ArrayDeque<T>()

    var current: T = initial
        private set

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    /** Records a new state; clears redo. Equal states are ignored. */
    fun push(next: T) {
        if (next == current) return
        undo.addLast(current)
        if (undo.size > capacity) undo.removeFirst()
        redo.clear()
        current = next
    }

    fun undo(): T {
        if (undo.isNotEmpty()) {
            redo.addLast(current)
            current = undo.removeLast()
        }
        return current
    }

    fun redo(): T {
        if (redo.isNotEmpty()) {
            undo.addLast(current)
            current = redo.removeLast()
        }
        return current
    }
}
