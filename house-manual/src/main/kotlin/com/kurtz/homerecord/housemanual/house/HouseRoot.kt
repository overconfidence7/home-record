package com.kurtz.homerecord.housemanual.house

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemanual.onboarding.foundationLabel
import com.kurtz.homerecord.housemanual.onboarding.houseTypeLabel
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HouseType

/** HM-House tab root for now: the home's summary and a way into the floors. The plan and blueprint land here with T013 and T015. */
@Composable
fun HouseRoot(home: HomeEntity, floorCount: Int, onOpenFloors: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(home.name, style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(
                    R.string.house_summary,
                    houseTypeLabel(HouseType.parse(home.houseType)),
                    foundationLabel(Foundation.parse(home.foundation)),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RecordListItem(
            headline = stringResource(R.string.floors_row),
            supporting = stringResource(R.string.floors_row_count, floorCount),
            leading = { Icon(Icons.Rounded.Layers, contentDescription = null) },
            onClick = onOpenFloors,
        )
    }
}
