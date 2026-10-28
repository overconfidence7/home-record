package com.kurtz.homerecord.core.photo

import com.kurtz.homerecord.core.data.PhotoEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A point on the photo as a fraction of its width and height (0..1), so marks survive resizing. */
@Serializable
data class NormPoint(val x: Float, val y: Float)

/** One mark drawn on a photo (AC-F26). Stored in `annotationsJson` of the saved copy. */
@Serializable
sealed interface Annotation {
    @Serializable @SerialName("arrow") data class Arrow(val from: NormPoint, val to: NormPoint) : Annotation
    @Serializable @SerialName("circle") data class Circle(val center: NormPoint, val radius: Float) : Annotation
    @Serializable @SerialName("rect") data class Rectangle(val topLeft: NormPoint, val bottomRight: NormPoint) : Annotation
    @Serializable @SerialName("pen") data class Freehand(val points: List<NormPoint>) : Annotation
    @Serializable @SerialName("text") data class Text(val at: NormPoint, val text: String) : Annotation
    @Serializable @SerialName("measure") data class Measure(val from: NormPoint, val to: NormPoint, val label: String) : Annotation
}

object AnnotationCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(marks: List<Annotation>): String = json.encodeToString(marks)

    /** Unreadable JSON gives no marks rather than a crash. */
    fun decode(raw: String): List<Annotation> = runCatching { json.decodeFromString<List<Annotation>>(raw) }.getOrDefault(emptyList())
}

/** Marks being drawn, with undo (AC-F26). Empty text and zero-length marks are dropped, not added. */
data class AnnotationEditor(val marks: List<Annotation> = emptyList()) {
    val canUndo: Boolean get() = marks.isNotEmpty()

    fun add(mark: Annotation): AnnotationEditor = if (isMeaningful(mark)) copy(marks = marks + mark) else this

    fun undo(): AnnotationEditor = copy(marks = marks.dropLast(1))

    private fun isMeaningful(mark: Annotation): Boolean = when (mark) {
        is Annotation.Arrow -> mark.from != mark.to
        is Annotation.Circle -> mark.radius > 0f
        is Annotation.Rectangle -> mark.topLeft != mark.bottomRight
        is Annotation.Freehand -> mark.points.size >= 2
        is Annotation.Text -> mark.text.isNotBlank()
        is Annotation.Measure -> mark.from != mark.to && mark.label.isNotBlank()
    }
}

/** A gallery cell: a single photo, or a before/after pair shown side by side (AC-F25). */
sealed interface GalleryCell {
    data class Single(val photo: PhotoEntity) : GalleryCell
    data class Pair(val before: PhotoEntity, val after: PhotoEntity) : GalleryCell
}

data class GalleryDay(val day: java.time.LocalDate, val cells: List<GalleryCell>)

object PhotoGallery {
    /**
     * Groups by day (AC-F24) and merges each "after" with the photo it points at through `beforeOfPhotoId` (AC-F25).
     * The pair sits on the day of the after shot; an after whose before is gone shows alone.
     */
    fun build(photos: List<PhotoEntity>, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<GalleryDay> {
        val byId = photos.associateBy { it.id }
        val paired = photos.mapNotNull { it.beforeOfPhotoId?.takeIf { id -> id in byId && id != it.id } }.toSet()
        return PhotoGrouping.byDay(photos.filter { it.id !in paired }, zone).map { d ->
            GalleryDay(
                d.day,
                d.photos.map { p ->
                    val before = p.beforeOfPhotoId?.let { byId[it] }
                    if (before != null && before.id != p.id) GalleryCell.Pair(before, p) else GalleryCell.Single(p)
                },
            )
        }
    }

    /** Annotated copies keep the original: the copy is a new row with the original's tags and the marks (AC-F26). */
    fun copyWithMarks(original: PhotoEntity, newId: String, newPath: String, marks: List<Annotation>, now: Long): PhotoEntity =
        original.copy(
            id = newId, path = newPath, thumbPath = null, annotationsJson = AnnotationCodec.encode(marks),
            beforeOfPhotoId = null, pairRole = null, createdAt = now, updatedAt = now,
        )
}
