package com.kurtz.homerecord.housemanual.shutoffs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.ShutoffKind
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemanual.rooms.labelRes
import com.kurtz.homerecord.ui.PhotoTile
import com.kurtz.homerecord.ui.theme.HomeRecordTheme
import java.io.File

@Composable
fun ShutoffsRoute(viewModel: ShutoffsViewModel, media: MediaFileStore, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    ShutoffsScreen(state, photoFile = { path -> media.resolve(path) }) { event ->
        when (event) {
            ShutoffsEvent.Add -> onAdd()
            is ShutoffsEvent.Open -> onOpen(event.shutoffId)
        }
    }
}

@Composable
fun ShutoffsScreen(state: ShutoffsUiState, photoFile: (String) -> File? = { null }, onEvent: (ShutoffsEvent) -> Unit) {
    if (!state.loaded) return
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.shutoffs_title), style = MaterialTheme.typography.headlineMedium)
        Button(
            onClick = { onEvent(ShutoffsEvent.Add) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("shutoffs_add"),
        ) { Text(stringResource(R.string.shutoffs_add)) }
        if (state.shutoffs.isEmpty()) {
            Text(
                stringResource(R.string.shutoffs_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("shutoffs_empty"),
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                state.groups.forEach { (kind, list) ->
                    item(key = "h-$kind") {
                        Text(
                            stringResource(kind.labelRes()),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(list, key = { it.id }) { shutoff ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .clickable { onEvent(ShutoffsEvent.Open(shutoff.id)) }
                                .padding(vertical = 8.dp).testTag("shutoff_row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PhotoTile(shutoff.photoPath?.let(photoFile), contentDescription = null, modifier = Modifier.testTag("shutoff_thumb"))
                            Column(Modifier.weight(1f)) {
                                val room = shutoff.roomId?.let { state.roomNames[it] }
                                if (room != null) Text(room, style = MaterialTheme.typography.labelLarge)
                                Text(shutoff.instructions, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ShutoffsScreenPreview() {
    val s = Shutoff("1", "h", ShutoffKind.WATER_MAIN, null, null, "Quarter turn clockwise", 0, 0, Source.HOUSE_MANUAL)
    HomeRecordTheme { ShutoffsScreen(ShutoffsUiState(true, listOf(s)), onEvent = {}) }
}
