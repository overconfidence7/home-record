package com.kurtz.homerecord.fieldlog.share

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.share.ShareLinkKind
import com.kurtz.homerecord.core.share.ShareTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Test

/** Fake links store: one live URL per target; [shareWorks] false stands for no app taking the share sheet. */
class FakeJobLinks : JobLinks {
    val live = MutableStateFlow<Map<ShareTarget, String>>(emptyMap())
    val shared = mutableListOf<Triple<ShareTarget, String, String?>>()
    var shareWorks = true
    var jobGone = false
    private var n = 0

    override fun observe(target: ShareTarget): Flow<String?> = live.map { it[target] }
    override suspend fun make(target: ShareTarget): String? {
        if (jobGone) return null
        live.value[target]?.let { return it }
        val url = "https://api.example/share/${target.kind.wire}/tok${++n}"
        live.value = live.value + (target to url)
        return url
    }
    override suspend fun turnOff(target: ShareTarget): Boolean {
        val had = target in live.value
        live.value = live.value - target
        return had
    }
    override suspend fun share(target: ShareTarget, url: String, detail: String?): Boolean {
        shared += Triple(target, url, detail)
        return shareWorks
    }
}

// AC-F28 (gallery link), AC-B35 (log link, revocable from the app)
@OptIn(ExperimentalCoroutinesApi::class)
class ShareLinkControllerTest {
    private val scope = CoroutineScope(UnconfinedTestDispatcher())
    private val links = FakeJobLinks()
    private val allowed = MutableStateFlow(true)
    private val gallery = ShareTarget(ShareLinkKind.GALLERY, "j1")
    private val target = MutableStateFlow<ShareTarget?>(gallery)

    @After fun tearDown() = scope.cancel()

    private fun controller(detail: String? = null) = ShareLinkController(scope, links, allowed, target, detail = { detail })

    @Test fun shareMakesTheLinkOnceAndOpensTheShareSheet() {
        val c = controller()
        assertThat(c.state.value.allowed).isTrue()
        assertThat(c.state.value.url).isNull()
        c.onEvent(ShareLinkEvent.Share)
        c.onEvent(ShareLinkEvent.Share)
        assertThat(c.state.value.url).isEqualTo("https://api.example/share/gallery/tok1")
        assertThat(links.shared.map { it.second }).containsExactly("https://api.example/share/gallery/tok1", "https://api.example/share/gallery/tok1")
        assertThat(c.state.value.working).isFalse()
        assertThat(c.state.value.failed).isFalse()
    }

    @Test fun turnOffAsksFirstAndTheNextShareMakesANewLink() {
        val c = controller()
        c.onEvent(ShareLinkEvent.Share)
        c.onEvent(ShareLinkEvent.AskOff)
        assertThat(c.state.value.confirmOff).isTrue()
        c.onEvent(ShareLinkEvent.DismissOff)
        assertThat(c.state.value.url).isNotNull()
        c.onEvent(ShareLinkEvent.AskOff)
        c.onEvent(ShareLinkEvent.TurnOff)
        assertThat(c.state.value.confirmOff).isFalse()
        assertThat(c.state.value.url).isNull()
        c.onEvent(ShareLinkEvent.Share)
        assertThat(c.state.value.url).isEqualTo("https://api.example/share/gallery/tok2")
    }

    @Test fun crewAndNoTargetHideTheControlAndIgnoreEvents() {
        allowed.value = false
        val c = controller()
        c.onEvent(ShareLinkEvent.Share)
        assertThat(c.state.value.allowed).isFalse()
        assertThat(links.shared).isEmpty()
        allowed.value = true
        target.value = null
        c.onEvent(ShareLinkEvent.Share)
        assertThat(c.state.value.allowed).isFalse()
        assertThat(links.live.value).isEmpty()
    }

    @Test fun aFailedShareSaysSoAndAnotherTargetClearsIt() {
        links.shareWorks = false
        val c = controller()
        c.onEvent(ShareLinkEvent.Share)
        assertThat(c.state.value.failed).isTrue()
        links.jobGone = true
        target.value = ShareTarget(ShareLinkKind.LOG, "j1", "log-2")
        assertThat(c.state.value.failed).isFalse()
        c.onEvent(ShareLinkEvent.Share)
        assertThat(c.state.value.failed).isTrue()
        assertThat(c.state.value.url).isNull()
    }

    @Test fun aLogLinkCarriesItsDayAndEachDayHasItsOwnLink() {
        val day1 = ShareTarget(ShareLinkKind.LOG, "j1", "log-1")
        target.value = day1
        val c = controller(detail = "Oct 21, 2026")
        c.onEvent(ShareLinkEvent.Share)
        assertThat(links.shared.single().third).isEqualTo("Oct 21, 2026")
        target.value = ShareTarget(ShareLinkKind.LOG, "j1", "log-2")
        assertThat(c.state.value.url).isNull()
        target.value = day1
        assertThat(c.state.value.url).isEqualTo("https://api.example/share/log/tok1")
    }
}
