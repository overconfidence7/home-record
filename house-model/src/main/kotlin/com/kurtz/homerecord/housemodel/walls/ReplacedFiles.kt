package com.kurtz.homerecord.housemodel.walls

import android.util.Log
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import kotlinx.coroutines.CancellationException

/**
 * Deletes the files of wall photos and overlay photos that were replaced or removed (R10c). A file is kept while any
 * live wall photo (as taken or straightened) or overlay still points at it: two parts of a split wall share one photo,
 * and a photo's straightened copy can be the photo itself. [delete] removes one file; a failure is logged, never thrown,
 * because the save it follows has already gone through.
 */
class ReplacedFiles(
    private val db: HomeRecordDatabase,
    private val delete: suspend (path: String) -> Unit,
) {
    suspend fun sweep(paths: Collection<String?>) {
        for (path in paths.filterNotNull().filter { it.isNotBlank() }.distinct()) {
            if (db.wallPhotoDao().countUsing(path) > 0 || db.overlayDao().countUsing(path) > 0) continue
            try {
                delete(path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete a replaced photo", e)
            }
        }
    }

    private companion object {
        const val TAG = "ReplacedFiles"
    }
}
