package com.kurtz.homerecord.core.photo

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.PhotoEntity
import org.junit.Test
import java.time.ZoneId

// AC-F25 (before/after pair in the gallery), AC-F26 (marks, undo, saved as a copy)
class PhotoAnnotationsTest {
    private val utc = ZoneId.of("UTC")
    private fun photo(id: String, at: Long, before: String? = null) =
        PhotoEntity(id = id, homeId = "h", jobId = "j", path = "/m/$id.jpg", takenAt = at, beforeOfPhotoId = before, createdAt = at, updatedAt = at)

    @Test fun afterPairsWithItsBeforeOnTheAfterDay() {
        val day = 86_400_000L
        val rows = listOf(photo("a", 2 * day + 5, before = "b"), photo("b", day + 5), photo("c", 2 * day + 1))
        val gallery = PhotoGallery.build(rows, utc)
        assertThat(gallery).hasSize(1)
        val cells = gallery.single().cells
        assertThat(cells).hasSize(2)
        assertThat(cells[0]).isEqualTo(GalleryCell.Pair(photo("b", day + 5), photo("a", 2 * day + 5, before = "b")))
        assertThat(cells[1]).isEqualTo(GalleryCell.Single(photo("c", 2 * day + 1)))
    }

    @Test fun afterWithMissingBeforeShowsAlone() {
        val gallery = PhotoGallery.build(listOf(photo("a", 10, before = "gone")), utc)
        assertThat(gallery.single().cells.single()).isInstanceOf(GalleryCell.Single::class.java)
    }

    @Test fun undoRemovesLastMarkAndEmptyMarksAreDropped() {
        val p = NormPoint(0.1f, 0.1f)
        var e = AnnotationEditor()
        e = e.add(Annotation.Arrow(p, NormPoint(0.5f, 0.5f)))
        e = e.add(Annotation.Text(p, "  "))
        e = e.add(Annotation.Arrow(p, p))
        e = e.add(Annotation.Circle(p, 0.2f))
        assertThat(e.marks).hasSize(2)
        e = e.undo()
        assertThat(e.marks.single()).isInstanceOf(Annotation.Arrow::class.java)
        assertThat(e.undo().undo().canUndo).isFalse()
    }

    @Test fun marksRoundTripThroughJson() {
        val marks = listOf(
            Annotation.Freehand(listOf(NormPoint(0f, 0f), NormPoint(1f, 1f))),
            Annotation.Measure(NormPoint(0f, 0f), NormPoint(1f, 0f), "36 in"),
            Annotation.Rectangle(NormPoint(0.1f, 0.1f), NormPoint(0.4f, 0.4f)),
        )
        assertThat(AnnotationCodec.decode(AnnotationCodec.encode(marks))).isEqualTo(marks)
        assertThat(AnnotationCodec.decode("not json")).isEmpty()
    }

    @Test fun annotatedCopyKeepsTagsAndLeavesOriginal() {
        val original = photo("a", 100, before = "b").copy(roomId = "r1", stage = "Framing", caption = "Stud bay")
        val copy = PhotoGallery.copyWithMarks(original, "a2", "/m/a2.jpg", listOf(Annotation.Circle(NormPoint(0.5f, 0.5f), 0.1f)), 200)
        assertThat(copy.id).isEqualTo("a2")
        assertThat(copy.roomId).isEqualTo("r1")
        assertThat(copy.caption).isEqualTo("Stud bay")
        assertThat(AnnotationCodec.decode(copy.annotationsJson)).hasSize(1)
        assertThat(original.annotationsJson).isEqualTo("[]")
    }
}
