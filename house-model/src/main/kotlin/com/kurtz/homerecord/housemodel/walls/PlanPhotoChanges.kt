package com.kurtz.homerecord.housemodel.walls

/**
 * What a plan save or a floor delete did to the photos on its walls, handed on after the write so the files can follow.
 * [recutIds] are wall photos whose outline was cut or stretched (R10a): their straightened copy is gone and must be made
 * again. [droppedFiles] are files that rows of the write stopped pointing at (R10d): the straightened copies of recut
 * photos, and the photos as taken, straightened copies and overlay photos of walls and rooms the write removed. They are
 * deleted only once no live row uses them ([ReplacedFiles]).
 */
data class PlanPhotoChanges(
    val recutIds: List<String> = emptyList(),
    val droppedFiles: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = recutIds.isEmpty() && droppedFiles.isEmpty()

    operator fun plus(other: PlanPhotoChanges) = PlanPhotoChanges(recutIds + other.recutIds, droppedFiles + other.droppedFiles)
}
