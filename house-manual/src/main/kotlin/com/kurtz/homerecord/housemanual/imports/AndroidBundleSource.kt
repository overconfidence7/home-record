package com.kurtz.homerecord.housemanual.imports

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class AndroidBundleSource(private val context: Context) : BundleSource {
    override fun open(uri: String): InputStream? = try {
        context.contentResolver.openInputStream(Uri.parse(uri))
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    override fun newStagingDir(): File = File(context.cacheDir, "import-${UUID.randomUUID()}")
}
