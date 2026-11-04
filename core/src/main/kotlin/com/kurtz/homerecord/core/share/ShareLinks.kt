package com.kurtz.homerecord.core.share

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ShareLinkEntity
import com.kurtz.homerecord.core.data.Sources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import java.util.Base64

/** What a share link opens on the backend (AC-B35, AC-B36); [wire] is `share_link.kind` and the URL segment. */
enum class ShareLinkKind(val wire: String) {
    /** The job's photo gallery (AC-F28). */
    GALLERY("gallery"),
    /** One daily log. */
    LOG("log"),
    /** A change order the client can view and sign (AC-F40). */
    CHANGE_ORDER("co"),
}

/** The record a link is for: a job's gallery has no target; a log or change order names its row. */
data class ShareTarget(val kind: ShareLinkKind, val jobId: String, val targetId: String? = null)

/**
 * Share links (T058b): one live link per job gallery, daily log or change order. Making one writes a `share_link` row
 * with a random token and queues it for sync; the backend serves the page once the row reaches it, so a link made
 * offline works after the next sync. Turning a link off marks it revoked (the page then says it is turned off) and the
 * next Share link makes a new token, so an old forwarded URL never comes back. Only Owner and Office may write links
 * (the backend refuses anyone else; the app hides the action).
 */
class ShareLinks(
    private val db: HomeRecordDatabase,
    private val baseUrl: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val newToken: () -> String = ::randomToken,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    private val dao get() = db.shareLinkDao()

    /** The live link's URL, or null while there is none. */
    fun observeUrl(target: ShareTarget): Flow<String?> =
        dao.observeActive(target.kind.wire, target.jobId, target.targetId).map { it?.let(::url) }

    /** The live link's URL, making the link first when there is none. Null when the job is gone. */
    suspend fun urlFor(target: ShareTarget): String? {
        dao.activeFor(target.kind.wire, target.jobId, target.targetId)?.let { return url(it) }
        if (db.jobDao().get(target.jobId) == null) return null
        val stamp = now()
        val row = ShareLinkEntity(
            id = Ids.newId(), kind = target.kind.wire, jobId = target.jobId, targetId = target.targetId, token = newToken(),
            revoked = false, createdAt = stamp, updatedAt = stamp, source = Sources.FIELD_LOG,
        )
        dao.upsert(row)
        onChange(ENTITY, row.id)
        return url(row)
    }

    /** Turns the live link off. False when there was none. */
    suspend fun turnOff(target: ShareTarget): Boolean {
        val row = dao.activeFor(target.kind.wire, target.jobId, target.targetId) ?: return false
        dao.upsert(row.copy(revoked = true, updatedAt = now()))
        onChange(ENTITY, row.id)
        return true
    }

    fun url(link: ShareLinkEntity): String = "${baseUrl.trimEnd('/')}/share/${link.kind}/${link.token}"

    companion object {
        const val ENTITY = "share_link"
        private val random = SecureRandom()

        /** 24 random bytes as 32 URL-safe characters: what the backend accepts (16–64 of A-Za-z0-9_-). */
        fun randomToken(): String {
            val bytes = ByteArray(24).also(random::nextBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
