package com.kurtz.homerecord.fieldlog.share

import android.content.Context
import com.kurtz.homerecord.core.data.JobDao
import com.kurtz.homerecord.core.share.ShareLinkKind
import com.kurtz.homerecord.core.share.ShareLinks
import com.kurtz.homerecord.core.share.ShareTarget
import com.kurtz.homerecord.coreui.platform.Intents
import com.kurtz.homerecord.fieldlog.R
import kotlinx.coroutines.flow.Flow

/** Gallery and daily-log links over [ShareLinks], sent through the system share sheet with the job's name. */
class AndroidJobLinks(private val context: Context, private val links: ShareLinks, private val jobs: JobDao) : JobLinks {
    override fun observe(target: ShareTarget): Flow<String?> = links.observeUrl(target)

    override suspend fun make(target: ShareTarget): String? = links.urlFor(target)

    override suspend fun turnOff(target: ShareTarget): Boolean = links.turnOff(target)

    override suspend fun share(target: ShareTarget, url: String, detail: String?): Boolean {
        val job = jobs.get(target.jobId)?.name.orEmpty()
        val text = when (target.kind) {
            ShareLinkKind.LOG -> context.getString(R.string.log_link_share_text, job, detail.orEmpty(), url)
            else -> context.getString(R.string.gallery_link_share_text, job, url)
        }
        return runCatching { Intents.shareText(context, text, context.getString(R.string.share_link_share_title)) }.isSuccess
    }
}
