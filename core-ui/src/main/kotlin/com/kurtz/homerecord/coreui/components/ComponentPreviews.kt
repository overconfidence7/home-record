package com.kurtz.homerecord.coreui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "200% font", fontScale = 2f, showBackground = true)
annotation class ThemePreviews

@Composable
private fun Frame(content: @Composable () -> Unit) = HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { Surface { content() } }

@ThemePreviews
@Composable
fun RecordListItemPreview() = Frame {
    Column {
        RecordListItem(
            headline = "Water heater",
            supporting = "Basement utility room · Rheem, 50 gal",
            leading = { androidx.compose.material3.Icon(Icons.Rounded.Inventory2, contentDescription = null) },
            onClick = {},
        )
        RecordListItem(
            headline = "Kitchen",
            supporting = "4 items, 2 breakers",
            leading = { androidx.compose.material3.Icon(Icons.Rounded.MeetingRoom, contentDescription = null) },
            onClick = {},
        )
    }
}

@ThemePreviews
@Composable
fun EmptyStatePreview() = Frame {
    EmptyState(Icons.Rounded.Bolt, "No breakers yet", "Tap a slot to add one.", "Add breaker", {})
}

@ThemePreviews
@Composable
fun LoadingStatePreview() = Frame { LoadingState() }

@ThemePreviews
@Composable
fun ErrorStatePreview() = Frame {
    ErrorState("Can't load items right now. Check your connection and tap Retry.", {})
}

@ThemePreviews
@Composable
fun LinkChipRowPreview() = Frame {
    LinkChipRow(
        links = listOf(
            LinkChip("1", "Breaker 14", Icons.Rounded.Bolt),
            LinkChip("2", "Kitchen", Icons.Rounded.MeetingRoom),
            LinkChip("3", "Water main shutoff"),
        ),
        onOpen = {},
        onRemove = {},
        modifier = Modifier.padding(16.dp),
    )
}

@ThemePreviews
@Composable
fun FormFieldsPreview() = Frame {
    Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
        LabeledField("Name", "Water heater", {}, required = true)
        LengthField("Ceiling height", 96, {}, UnitSystem.IMPERIAL)
        MoneyField("Price", 84900, {})
        DateField("Purchased", "2024-03-15", {})
        EnumPicker(listOf("1-pole", "2-pole", "Tandem"), "2-pole", {}, { it })
    }
}

@ThemePreviews
@Composable
fun SignInEmailPreview() = Frame {
    com.kurtz.homerecord.coreui.auth.SignInScreen(
        com.kurtz.homerecord.coreui.auth.SignInUiState(email = "dana.whitfield@gmail.com"),
        {},
    )
}

@ThemePreviews
@Composable
fun SignInCodePreview() = Frame {
    com.kurtz.homerecord.coreui.auth.SignInScreen(
        com.kurtz.homerecord.coreui.auth.SignInUiState(
            step = com.kurtz.homerecord.coreui.auth.SignInStep.Code,
            email = "dana.whitfield@gmail.com",
            code = "48",
            error = com.kurtz.homerecord.core.auth.AuthResult.InvalidCode,
        ),
        {},
    )
}
