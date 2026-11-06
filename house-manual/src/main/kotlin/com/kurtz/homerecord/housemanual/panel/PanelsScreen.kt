package com.kurtz.homerecord.housemanual.panel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.items.labelRes

/** More → Breaker panels (AC-C40): one row per Breaker panel or Sub-panel item; a row opens its slots. */
@Composable
fun PanelsScreen(state: PanelsUiState, onEvent: (PanelsEvent) -> Unit) {
    ScreenScaffold(title = stringResource(R.string.panels_title), onBack = { onEvent(PanelsEvent.Back) }) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.panels.isEmpty() -> EmptyState(
                icon = Icons.Rounded.ElectricalServices,
                headline = stringResource(R.string.panels_empty_title),
                body = stringResource(R.string.panels_empty_body),
                actionLabel = stringResource(R.string.panels_add),
                onAction = { onEvent(PanelsEvent.Add) },
                modifier = Modifier.padding(padding),
                actionModifier = Modifier.testTag("hm_panels_add"),
            )
            else -> LazyColumn(Modifier.padding(padding).fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(state.panels, key = { _, row -> row.id }) { i, row ->
                    val type = ItemCategory.subtype(row.subtypeId)?.let { stringResource(it.labelRes) }
                    RecordListItem(
                        headline = row.name,
                        supporting = listOfNotNull(type, row.roomName.ifBlank { null }, listOfNotNull(row.make, row.model).joinToString(" ").ifBlank { null })
                            .joinToString(stringResource(R.string.list_separator)),
                        leading = { Icon(Icons.Rounded.ElectricalServices, contentDescription = null) },
                        onClick = { onEvent(PanelsEvent.Open(row.id)) },
                        modifier = Modifier.testTag("hm_panels_row_${i + 1}"),
                    )
                }
            }
        }
    }
}
