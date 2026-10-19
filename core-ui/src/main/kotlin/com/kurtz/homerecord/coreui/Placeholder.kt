package com.kurtz.homerecord.coreui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Empty first screen for each app; replaced by the real shell in T003. */
@Composable
fun EmptyAppScreen(title: String) {
    Text(text = title, style = MaterialTheme.typography.headlineMedium)
}
