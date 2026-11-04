package com.kurtz.homerecord.core.share

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// T058b: AC-F40 (a change order share link for remote approval), AC-F28 (a job gallery link), AC-B35 (links are
// revocable from the app): one live link per target, made once and queued for sync, turned off by revoking it.
@AcceptanceCriterion("AC-F40", "AC-F28", "AC-B35")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ShareLinksTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var links: ShareLinks
    private val changes = mutableListOf<String>()
    private var n = 0

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        links = ShareLinks(db, "https://api.example.test/", now = { 1_000L }, newToken = { "token-${n++}-abcdefghijklmnop" }, onChange = { e, id -> changes += "$e/$id" })
        runBlocking { db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", createdAt = 1, updatedAt = 1)) }
    }

    @After fun tearDown() = db.close()

    @Test fun aLinkIsMadeOncePerTargetAndQueuedForSync() = runBlocking<Unit> {
        val co = ShareTarget(ShareLinkKind.CHANGE_ORDER, "j1", "co1")
        assertThat(links.observeUrl(co).first()).isNull()
        val url = links.urlFor(co)
        assertThat(url).isEqualTo("https://api.example.test/share/co/token-0-abcdefghijklmnop")
        assertThat(links.urlFor(co)).isEqualTo(url)
        assertThat(links.observeUrl(co).first()).isEqualTo(url)
        val row = checkNotNull(db.shareLinkDao().activeFor("co", "j1", "co1"))
        assertThat(row.targetId).isEqualTo("co1")
        assertThat(changes).containsExactly("share_link/${row.id}")
        // Another target, and the job's gallery (no target), each get their own link.
        assertThat(links.urlFor(ShareTarget(ShareLinkKind.CHANGE_ORDER, "j1", "co2"))).endsWith("/share/co/token-1-abcdefghijklmnop")
        assertThat(links.urlFor(ShareTarget(ShareLinkKind.GALLERY, "j1"))).endsWith("/share/gallery/token-2-abcdefghijklmnop")
        assertThat(links.urlFor(ShareTarget(ShareLinkKind.GALLERY, "gone"))).isNull()
    }

    @Test fun turningOffRevokesAndTheNextLinkHasANewToken() = runBlocking<Unit> {
        val co = ShareTarget(ShareLinkKind.CHANGE_ORDER, "j1", "co1")
        assertThat(links.turnOff(co)).isFalse()
        val first = links.urlFor(co)
        changes.clear()
        assertThat(links.turnOff(co)).isTrue()
        assertThat(links.observeUrl(co).first()).isNull()
        assertThat(changes).hasSize(1)
        assertThat(db.shareLinkDao().listFor("j1").single().revoked).isTrue()
        val second = links.urlFor(co)
        assertThat(second).isNotEqualTo(first)
    }

    @Test fun randomTokensAreWhatTheBackendAccepts() {
        val tokens = (1..50).map { ShareLinks.randomToken() }
        assertThat(tokens.toSet()).hasSize(50)
        tokens.forEach { assertThat(it).matches("[A-Za-z0-9_-]{32}") }
    }
}
