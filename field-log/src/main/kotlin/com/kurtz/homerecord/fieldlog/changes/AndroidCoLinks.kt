package com.kurtz.homerecord.fieldlog.changes

import android.content.Context
import com.kurtz.homerecord.core.share.ShareLinkKind
import com.kurtz.homerecord.core.share.ShareLinks
import com.kurtz.homerecord.core.share.ShareTarget
import com.kurtz.homerecord.coreui.platform.Intents
import com.kurtz.homerecord.fieldlog.R
import kotlinx.coroutines.flow.Flow

/** A change order's share link (AC-F40) over [ShareLinks], sent through the system share sheet. */
class AndroidCoLinks(private val context: Context, private val jobId: String, private val links: ShareLinks) : CoLinks {
    private fun target(id: String) = ShareTarget(ShareLinkKind.CHANGE_ORDER, jobId, id)

    override fun observe(changeOrderId: String): Flow<String?> = links.observeUrl(target(changeOrderId))

    override suspend fun make(changeOrderId: String): String? = links.urlFor(target(changeOrderId))

    override suspend fun turnOff(changeOrderId: String): Boolean = links.turnOff(target(changeOrderId))

    override fun share(url: String, title: String, forSigning: Boolean): Boolean = runCatching {
        val text = context.getString(if (forSigning) R.string.co_link_share_text_sign else R.string.co_link_share_text, title, url)
        Intents.shareText(context, text, context.getString(R.string.co_link_share_title))
    }.isSuccess
}
