package com.kurtz.homerecord.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

/** Empty shell screen shown by both apps until real screens land. */
@Composable
fun HomeRecordShellScreen(title: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Preview
@Composable
private fun HomeRecordShellScreenPreview() {
    HomeRecordTheme { HomeRecordShellScreen(title = "HomeRecord") }
}
