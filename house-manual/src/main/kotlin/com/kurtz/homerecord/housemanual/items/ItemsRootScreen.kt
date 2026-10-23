package com.kurtz.homerecord.housemanual.items

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.housemanual.R

/** Items tab root until the grouped list lands (T027): an empty state that opens the add form. */
@Composable
fun ItemsRootScreen(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, modifier = Modifier.testTag("hm_items_fab")) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.items_add))
            }
        },
    ) { padding ->
        EmptyState(
            icon = Icons.Rounded.Inventory2,
            headline = stringResource(R.string.items_empty_title),
            body = stringResource(R.string.items_empty_body),
            actionLabel = stringResource(R.string.items_add),
            onAction = onAdd,
            modifier = Modifier.padding(padding),
            actionModifier = Modifier.testTag("hm_items_add"),
        )
    }
}
