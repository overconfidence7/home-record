package com.kurtz.homerecord.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// [r12:reports-photo-count] (AC-F28): FL-Reports counts read only id, job and time of the company's live job photos.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhotoTakenQueryTest {
    @Test
    fun onlyLivePhotosOnTheCompanysLiveJobsOldestFirst() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        try {
            val jobs = db.jobDao()
            jobs.upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", createdAt = 1, updatedAt = 1))
            jobs.upsert(JobEntity(id = "j2", companyId = "c1", name = "Old porch", createdAt = 1, updatedAt = 1, deletedAt = 5))
            jobs.upsert(JobEntity(id = "j3", companyId = "c2", name = "Other company", createdAt = 1, updatedAt = 1))
            fun photo(id: String, job: String?, at: Long, deleted: Long? = null) =
                PhotoEntity(id = id, homeId = "", jobId = job, path = "/p/$id.jpg", takenAt = at, caption = "note", createdAt = 1, updatedAt = 1, deletedAt = deleted)
            val photos = db.photoDao()
            photos.upsert(photo("p2", "j1", 200))
            photos.upsert(photo("p1", "j1", 100))
            photos.upsert(photo("gone", "j1", 150, deleted = 9))
            photos.upsert(photo("porch", "j2", 120))
            photos.upsert(photo("other", "j3", 130))
            photos.upsert(photo("loose", null, 140))

            assertThat(photos.observeTakenForCompanyJobs("c1").first())
                .containsExactly(PhotoTaken("p1", "j1", 100), PhotoTaken("p2", "j1", 200)).inOrder()
        } finally {
            db.close()
        }
    }
}
