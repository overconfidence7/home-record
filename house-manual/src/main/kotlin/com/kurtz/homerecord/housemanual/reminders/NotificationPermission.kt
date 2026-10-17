package com.kurtz.homerecord.housemanual.reminders

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * AC-84: returns a function that asks for POST_NOTIFICATIONS. Call it only once a reminder task has been
 * created; [onResult] runs afterwards whether or not the permission was granted (or needed at all).
 */
@Composable
fun rememberNotificationPermissionRequester(onResult: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { latest.value() }
    return {
        val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needed) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else latest.value()
    }
}
