package com.kurtz.homerecord.housemodel.plan

import kotlin.math.abs

/**
 * The editing rules behind the plan editor: add, move, resize, rename, delete, with grid and neighbor snapping and
 * undo/redo (AC-C6, AC-C10). A drag shows a draft of the room; [commit] turns it into one undo step.
 */
class PlanEditor(initial: List<PlanRoom>, private val snapper: GridSnapper = GridSnapper()) {
    private val history = UndoStack(initial, capacity = 100)
    private var draft: List<PlanRoom>? = null
    private var dragBase: PlanRoom? = null

    val rooms: List<PlanRoom> get() = draft ?: history.current
    val canUndo get() = history.canUndo
    val canRedo get() = history.canRedo

    /** Adds a [widthIn] x [lengthIn] rectangle centered on [center], snapped to the grid. */
    fun addRoom(id: String, name: String, kind: RoomKind, center: Pt, widthIn: Int = DEFAULT_SIDE_IN, lengthIn: Int = DEFAULT_SIDE_IN): PlanRoom {
        val left = snapper.snap(center.x - widthIn / 2)
        val top = snapper.snap(center.y - lengthIn / 2)
        val room = PlanRoom.rectangle(id, name, kind.name, left, top, widthIn, lengthIn)
        history.push(rooms + room)
        return room
    }

    /** Places an existing room that had no shape yet. */
    fun place(room: PlanRoom) = history.push(rooms.filterNot { it.id == room.id } + room)

    /** Shows the room moved by ([dxIn], [dyIn]) from where the drag began. */
    fun dragMove(id: String, dxIn: Int, dyIn: Int) {
        val base = baseFor(id) ?: return
        val others = history.current.filterNot { it.id == id }
        val (dx, dy) = snapper.snapMove(base, dxIn, dyIn, others)
        draft = history.current.map { if (it.id == id) base.moved(dx, dy) else it }
    }

    /** Shows edge [edgeIndex] of an axis-aligned room dragged by [deltaIn] along its normal. Too-small results are ignored. */
    fun dragEdge(id: String, edgeIndex: Int, deltaIn: Int) {
        val base = baseFor(id) ?: return
        val a = base.points.getOrNull(edgeIndex) ?: return
        val b = base.points[(edgeIndex + 1) % base.points.size]
        val others = history.current.filterNot { it.id == id }
        val horizontal = a.y == b.y
        val raw = (if (horizontal) a.y else a.x) + deltaIn
        val neighbors = others.flatMap { o -> o.points.map { if (horizontal) it.y else it.x } }
        val resized = base.resizedEdge(edgeIndex, snapper.snapCoordinate(raw, neighbors))
        // Dragging an edge past the opposite one would flip the room; the signed area then changes sign.
        val flipped = (resized.signedArea2() > 0) != (base.signedArea2() > 0)
        if (!flipped && resized.widthIn >= MIN_SIDE_IN && resized.lengthIn >= MIN_SIDE_IN) {
            draft = history.current.map { if (it.id == id) resized else it }
        }
    }

    /** Ends a drag: one undo step when the room actually changed. */
    fun commit() {
        draft?.let { history.push(it) }
        draft = null
        dragBase = null
    }

    fun rename(id: String, name: String) = change(id) { it.copy(name = name) }

    fun setKind(id: String, kind: RoomKind) = change(id) { it.copy(kind = kind.name) }

    /** Types a new width and length for a rectangular room, keeping its top-left corner. Other shapes are unchanged. */
    fun setSize(id: String, widthIn: Int, lengthIn: Int) = change(id) { room ->
        if (!room.isRectangle() || widthIn < MIN_SIDE_IN || lengthIn < MIN_SIDE_IN) room
        else PlanRoom.rectangle(room.id, room.name, room.kind, room.minX, room.minY, widthIn, lengthIn)
    }

    fun delete(id: String) = history.push(rooms.filterNot { it.id == id })

    fun undo(): List<PlanRoom> {
        draft = null
        dragBase = null
        return history.undo()
    }

    fun redo(): List<PlanRoom> {
        draft = null
        dragBase = null
        return history.redo()
    }

    private fun change(id: String, block: (PlanRoom) -> PlanRoom) {
        val room = rooms.firstOrNull { it.id == id } ?: return
        history.push(rooms.map { if (it.id == id) block(room) else it })
    }

    private fun baseFor(id: String): PlanRoom? {
        dragBase?.takeIf { it.id == id }?.let { return it }
        return history.current.firstOrNull { it.id == id }?.also { dragBase = it }
    }

    private fun PlanRoom.isRectangle(): Boolean =
        points.size == 4 && (0..3).all { i ->
            val a = points[i]
            val b = points[(i + 1) % 4]
            (a.x == b.x) != (a.y == b.y) && abs(a.x - b.x) + abs(a.y - b.y) > 0
        }

    companion object {
        const val DEFAULT_SIDE_IN = 144
        const val MIN_SIDE_IN = 12
    }
}
