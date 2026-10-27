package com.kurtz.homerecord.core.media

import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.MediaUploadDao
import com.kurtz.homerecord.core.data.MediaUploadEntity
import com.kurtz.homerecord.core.sync.TenantKind
import com.kurtz.homerecord.core.sync.TenantRef
import java.io.File
import java.util.UUID

/** Result of one pass over the queue. [retryAt] is when the next waiting upload is due, or null when nothing waits. */
data class UploadPass(val uploaded: Int, val failed: Int, val retryAt: Long?)

/**
 * Upload queue for photos and files (AC-F54). Rows live in Room so a restart or process death loses nothing;
 * [enqueue] is idempotent per entity/filename so the same file is never queued twice; a signed URL is reused
 * until it expires; network trouble backs off exponentially and keeps the file queued; a file that keeps failing
 * for other reasons stops after [MAX_ATTEMPTS] and waits for [retryFailed].
 * A PUT to a presigned URL can't be continued part-way, so "resume" means the whole file goes again from the queue.
 */
class MediaUploader(
    private val dao: MediaUploadDao,
    private val api: MediaTransferApi,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val onUploaded: suspend (MediaUploadEntity, Presigned) -> Unit = { _, _ -> },
) {
    /** Queues [path] for upload as `<tenant>/<entity>/<entityId>/<filename>`; returns the existing row's id when already queued. */
    suspend fun enqueue(tenant: TenantRef, entity: String, entityId: String, filename: String, path: String, mime: String): String {
        dao.find(entity, entityId, filename)?.let { return it.id }
        val id = newId()
        dao.add(
            MediaUploadEntity(
                id = id, tenantKind = tenant.kind.wire, tenantId = tenant.id, entity = entity, entityId = entityId,
                filename = filename, path = path, mime = mime, createdAt = now(),
            ),
        )
        return dao.find(entity, entityId, filename)?.id ?: id
    }

    /** Call on start: an upload that was mid-flight when the process died goes back to the queue. */
    suspend fun recover() = dao.requeueInterrupted()

    suspend fun retryFailed() = dao.retryFailed()

    suspend fun runPending(): UploadPass {
        var uploaded = 0
        var failed = 0
        for (row in dao.due(now())) {
            when (upload(row)) {
                Step.Done -> uploaded++
                Step.Failed -> failed++
                Step.Waiting -> {}
                Step.Stop -> break
            }
        }
        return UploadPass(uploaded, failed, dao.nextAttemptAt())
    }

    private enum class Step { Done, Failed, Waiting, Stop }

    private suspend fun upload(row: MediaUploadEntity): Step {
        val file = File(row.path)
        if (!file.exists()) {
            dao.save(row.copy(state = "failed"))
            return Step.Failed
        }
        var current = row.copy(state = "uploading")
        dao.save(current)
        val tenant = TenantRef(TenantKind.entries.first { it.wire == row.tenantKind }, row.tenantId)
        var presigned: Presigned? = null
        if (current.putUrl == null || current.key == null || current.putExpiresAt <= now() + EXPIRY_MARGIN_MS) {
            when (val r = api.presign(tenant, row.entity, row.entityId, row.filename, file.length(), row.mime)) {
                is ApiResult.Ok -> {
                    presigned = r.value
                    current = current.copy(key = r.value.key, putUrl = r.value.putUrl, putExpiresAt = r.value.putExpiresAt)
                    dao.save(current)
                }
                is ApiResult.Err -> return back(current, offline = r.failure == ApiFailure.Offline, giveUp = r.failure == ApiFailure.Forbidden || r.failure == ApiFailure.NotFound || r.failure == ApiFailure.Invalid)
            }
        }
        val url = current.putUrl ?: return back(current, offline = false)
        return when (api.put(url, file, row.mime)) {
            PutOutcome.Ok -> {
                dao.save(current.copy(state = "done", putUrl = null))
                onUploaded(current, presigned ?: Presigned(current.key.orEmpty(), url, current.putExpiresAt))
                Step.Done
            }
            PutOutcome.Offline -> back(current, offline = true)
            // Expired or refused signature: drop it so the next try asks for a fresh one, right away. Only a URL that
            // was fresh in this pass and is still refused counts, so a file the server keeps refusing stops after
            // MAX_ATTEMPTS instead of looping (cr:upload-rejected-loop).
            PutOutcome.Rejected -> {
                val attempts = if (presigned != null) current.attempts + 1 else current.attempts
                if (attempts >= MAX_ATTEMPTS) {
                    dao.save(current.copy(state = "failed", attempts = attempts, putUrl = null, putExpiresAt = 0))
                    Step.Failed
                } else {
                    dao.save(current.copy(state = "queued", putUrl = null, putExpiresAt = 0, attempts = attempts, nextAttemptAt = now()))
                    Step.Waiting
                }
            }
            PutOutcome.Failed -> back(current, offline = false)
        }
    }

    /** Offline never counts against the file; other failures do, and stop after [MAX_ATTEMPTS]. */
    private suspend fun back(row: MediaUploadEntity, offline: Boolean, giveUp: Boolean = false): Step {
        if (giveUp) {
            dao.save(row.copy(state = "failed"))
            return Step.Failed
        }
        val attempts = if (offline) row.attempts else row.attempts + 1
        if (attempts >= MAX_ATTEMPTS) {
            dao.save(row.copy(state = "failed", attempts = attempts))
            return Step.Failed
        }
        dao.save(row.copy(state = "queued", attempts = attempts, nextAttemptAt = now() + backoffMs(row.attempts + if (offline) 1 else 0)))
        // No network: the rest of the queue would fail the same way, so stop this pass.
        return if (offline) Step.Stop else Step.Waiting
    }

    companion object {
        const val MAX_ATTEMPTS = 6
        const val EXPIRY_MARGIN_MS = 30_000L
        private const val BASE_MS = 30_000L
        private const val CAP_MS = 60 * 60_000L

        /** 30 s, 1 min, 2 min, ... capped at an hour. */
        fun backoffMs(attempt: Int): Long = (BASE_MS shl (attempt - 1).coerceIn(0, 10)).coerceAtMost(CAP_MS)
    }
}
