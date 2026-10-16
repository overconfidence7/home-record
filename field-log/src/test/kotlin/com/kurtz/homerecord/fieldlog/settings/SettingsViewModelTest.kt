package com.kurtz.homerecord.fieldlog.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.ReportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: HomeRecordDatabase
    private lateinit var reports: ReportRepository
    private lateinit var media: MediaFileStore

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        reports = ReportRepository(db, Source.FIELD_LOG)
        media = MediaFileStore(java.nio.file.Files.createTempDirectory("media").toFile())
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun vm() = SettingsViewModel(reports, media).also { v -> v.state.first { it.loaded } }

    @Test fun `save needs a company name and persists the profile`() = runTest(dispatcher) {
        val v = vm()
        assertFalse(v.state.value.canSave)
        v.onEvent(SettingsEvent.NameChanged("Acme"))
        v.onEvent(SettingsEvent.PhoneChanged("555"))
        v.onEvent(SettingsEvent.Save)
        v.done.first()
        val reopened = vm().state.value
        assertEquals("Acme", reopened.name)
        assertEquals("555", reopened.phone)
    }

    @Test fun `replacing the logo deletes the superseded unsaved photo and the old saved one on save`() = runTest(dispatcher) {
        val savedPath = media.newPhotoPath()
        media.resolveForWrite(savedPath)!!.writeText("old")
        reports.saveProfile("Acme", null, null, savedPath)
        val v = vm()
        val tmp1 = media.newPhotoPath().also { media.resolveForWrite(it)!!.writeText("1") }
        val tmp2 = media.newPhotoPath().also { media.resolveForWrite(it)!!.writeText("2") }
        v.onLogoCaptured(tmp1)
        v.onLogoCaptured(tmp2)
        assertFalse(media.exists(tmp1))
        assertTrue(media.exists(savedPath))
        v.onEvent(SettingsEvent.Save)
        v.done.first()
        assertFalse(media.exists(savedPath))
        assertTrue(media.exists(tmp2))
        assertEquals(tmp2, reports.getProfile()?.logoPath)
    }
}
