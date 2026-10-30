package com.kurtz.homerecord.coreui.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** The platform intents both apps fire: share sheet through the app's FileProvider, maps, and the app's settings page. */
object Intents {
    /** `geo:` link that drops a labelled pin at the point. */
    fun mapUri(lat: Double, lng: Double, label: String): Uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")

    fun fileUri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)

    /** Opens the share sheet for a content URI the app already holds. */
    fun shareUri(context: Context, uri: Uri, mime: String, title: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(context, Intent.createChooser(send, title))
    }

    /** Shares a file under filesDir/media or cache through FileProvider; a missing file does nothing. */
    fun shareFile(context: Context, file: File, mime: String, title: String = file.name) {
        if (!file.exists()) return
        shareUri(context, fileUri(context, file), mime, title)
    }

    fun shareText(context: Context, text: String, title: String? = null) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        start(context, Intent.createChooser(send, title))
    }

    fun openMap(context: Context, lat: Double, lng: Double, label: String) = start(context, Intent(Intent.ACTION_VIEW, mapUri(lat, lng, label)))

    fun openAppSettings(context: Context) =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

    /** No app for the intent (no maps app on a bare device) is not a crash. */
    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }
}
