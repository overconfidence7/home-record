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

    /** Adds a [widthIn] x [lengthIn] rectangle centered on [center], snapped to the grid and moved right of any room it would overlap. */
    fun addRoom(id: String, name: String, kind: RoomKind, center: Pt, widthIn: Int = DEFAULT_SIDE_IN, lengthIn: Int = DEFAULT_SIDE_IN): PlanRoom {
        val left = snapper.snap(center.x - widthIn / 2)
        val top = snapper.snap(center.y - lengthIn / 2)
        var room = PlanRoom.rectangle(id, name, kind.name, left, top, widthIn, lengthIn)
        // A new room goes next to the rooms it would cover, so the plan never starts with a stack of rooms.
        repeat(rooms.size) {
            val blocker = rooms.firstOrNull { PlanValidator().overlaps(room, it) } ?: return@repeat
            room = PlanRoom.rectangle(id, name, kind.name, snapper.snap(blocker.maxX), top, widthIn, lengthIn)
        }
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

    /** Shows corner [index] dragged by ([dxIn], [dyIn]) from where the drag began. A move that would cross the outline is ignored. */
    fun dragCorner(id: String, index: Int, dxIn: Int, dyIn: Int) {
        val base = baseFor(id) ?: return
        val start = base.points.getOrNull(index) ?: return
        val others = history.current.filterNot { it.id == id }
        val x = snapper.snapCoordinate(start.x + dxIn, others.flatMap { o -> o.points.map { it.x } })
        val y = snapper.snapCoordinate(start.y + dyIn, others.flatMap { o -> o.points.map { it.y } })
        val moved = base.withPoint(index, Pt(x, y))
        if (moved.isSimple()) draft = history.current.map { if (it.id == id) moved else it }
    }

    /** Adds a corner on edge [edgeIndex] at [at] (snapped to the grid, projected onto the edge). Returns the new corner's index. */
    fun addCorner(id: String, edgeIndex: Int, at: Pt): Int? {
        val room = rooms.firstOrNull { it.id == id } ?: return null
        val a = room.points.getOrNull(edgeIndex) ?: return null
        val b = room.points[(edgeIndex + 1) % room.points.size]
        val p = projectOnto(a, b, at)
        if (p == a || p == b) return null
        change(id) { it.withCornerOnEdge(edgeIndex, p) }
        return edgeIndex + 1
    }

    /** Deletes corner [index]; a room keeps at least three. Returns false when nothing changed. */
    fun deleteCorner(id: String, index: Int): Boolean {
        val room = rooms.firstOrNull { it.id == id } ?: return false
        val next = room.withoutCorner(index)
        if (next === room || !next.isSimple()) return false
        change(id) { next }
        return true
    }

    fun rename(id: String, name: String) = change(id) { it.copy(name = name) }

    fun setKind(id: String, kind: RoomKind) = change(id) { it.copy(kind = kind.name) }

    /** Types a new width and length for a rectangular room, keeping its top-left corner. Other shapes are unchanged. */
    fun setSize(id: String, widthIn: Int, lengthIn: Int) = change(id) { room ->
        if (!room.isRectangle() || widthIn < MIN_SIDE_IN || lengthIn < MIN_SIDE_IN) room
        else PlanRoom.rectangle(room.id, room.name, room.kind, room.minX, room.minY, widthIn, lengthIn)
            .copy(openings = room.openings, stairs = room.stairs)
            .clampOpenings()
    }

    /** Adds a door or window centered on the point of edge [edgeIndex] nearest [at]. Returns its id, or null when the edge is too short. */
    fun addOpening(roomId: String, id: String, edgeIndex: Int, kind: OpeningKind, at: Pt? = null): String? {
        val room = rooms.firstOrNull { it.id == roomId } ?: return null
        if (edgeIndex !in room.points.indices) return null
        val length = room.edgeLength(edgeIndex)
        val width = minOf(if (kind == OpeningKind.DOOR) PlanRoom.DEFAULT_DOOR_IN else PlanRoom.DEFAULT_WINDOW_IN, length)
        if (width < PlanRoom.MIN_OPENING_IN) return null
        val centre = if (at == null) length / 2 else alongEdge(room, edgeIndex, at)
        val height = if (kind == OpeningKind.DOOR) DOOR_HEIGHT_IN else WINDOW_HEIGHT_IN
        val opening = PlanOpening(id, edgeIndex, kind, (centre - width / 2).coerceIn(0, length - width), width, height)
        change(roomId) { it.withOpening(opening) }
        return id
    }

    /** Shows opening [id] slid so its centre sits nearest [at]; [commit] makes it one undo step. */
    fun dragOpening(roomId: String, id: String, at: Pt) {
        val room = history.current.firstOrNull { it.id == roomId } ?: return
        val o = room.openings.firstOrNull { it.id == id } ?: return
        val centre = alongEdge(room, o.edgeIndex, at)
        val moved = o.copy(offsetIn = snapper.snap(centre - o.widthIn / 2))
        draft = history.current.map { if (it.id == roomId) it.withOpening(moved) else it }
    }

    fun setOpeningWidth(roomId: String, id: String, widthIn: Int) = change(roomId) { room ->
        val o = room.openings.firstOrNull { it.id == id } ?: return@change room
        if (widthIn < PlanRoom.MIN_OPENING_IN || widthIn > room.edgeLength(o.edgeIndex)) room
        else room.withOpening(o.copy(widthIn = widthIn))
    }

    fun deleteOpening(roomId: String, id: String) = change(roomId) { it.withoutOpening(id) }

    /** Puts stairs of [widthIn] x [lengthIn] in the middle of the room, linked to [toFloorId]. */
    fun addStair(roomId: String, id: String, toFloorId: String?, widthIn: Int = STAIR_WIDTH_IN, lengthIn: Int = STAIR_LENGTH_IN): Boolean {
        val room = rooms.firstOrNull { it.id == roomId } ?: return false
        val w = minOf(widthIn, room.widthIn)
        val l = minOf(lengthIn, room.lengthIn)
        if (w < MIN_SIDE_IN || l < MIN_SIDE_IN) return false
        val left = snapper.snap((room.minX + room.maxX) / 2 - w / 2).coerceIn(room.minX, room.maxX - w)
        val top = snapper.snap((room.minY + room.maxY) / 2 - l / 2).coerceIn(room.minY, room.maxY - l)
        val corners = listOf(Pt(left, top), Pt(left + w, top), Pt(left + w, top + l), Pt(left, top + l))
        change(roomId) { it.withStair(PlanStair(id, toFloorId, corners)) }
        return true
    }

    fun linkStair(roomId: String, id: String, toFloorId: String?) = change(roomId) { room ->
        val s = room.stairs.firstOrNull { it.id == id } ?: return@change room
        room.withStair(s.copy(toFloorId = toFloorId))
    }

    fun deleteStair(roomId: String, id: String) = change(roomId) { it.withoutStair(id) }

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

    /** The point of segment a-b nearest [p], snapped to the grid along the segment; a and b themselves when too close. */
    private fun projectOnto(a: Pt, b: Pt, p: Pt): Pt {
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return a
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
        val raw = Pt(Math.round(a.x + t * dx).toInt(), Math.round(a.y + t * dy).toInt())
        // Axis-aligned edges keep their line; snapping the free coordinate only.
        val snapped = when {
            a.y == b.y -> Pt(snapper.snap(raw.x), a.y)
            a.x == b.x -> Pt(a.x, snapper.snap(raw.y))
            else -> raw
        }
        val minGap = snapper.gridIn
        return if (distance(snapped, a) < minGap || distance(snapped, b) < minGap) a else snapped
    }

    /** Distance in inches from the first corner of edge [edgeIndex] to the point of the edge nearest [p]. */
    private fun alongEdge(room: PlanRoom, edgeIndex: Int, p: Pt): Int {
        val a = room.points[edgeIndex]
        val b = room.points[(edgeIndex + 1) % room.points.size]
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return 0
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
        return Math.round(t * Math.sqrt(len2)).toInt()
    }

    private fun distance(a: Pt, b: Pt) = Math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    private fun PlanRoom.isRectangle(): Boolean =
        points.size == 4 && (0..3).all { i ->
            val a = points[i]
            val b = points[(i + 1) % 4]
            (a.x == b.x) != (a.y == b.y) && abs(a.x - b.x) + abs(a.y - b.y) > 0
        }

    companion object {
        const val DEFAULT_SIDE_IN = 144
        const val MIN_SIDE_IN = 12
        const val DOOR_HEIGHT_IN = 80
        const val WINDOW_HEIGHT_IN = 48
        const val STAIR_WIDTH_IN = 36
        const val STAIR_LENGTH_IN = 120
    }
}
