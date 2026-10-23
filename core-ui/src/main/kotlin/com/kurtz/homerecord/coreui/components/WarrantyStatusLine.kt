package com.kurtz.homerecord.coreui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.coreui.R
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** One line of warranty state with an icon and color; the words carry the meaning, color only backs them (AC-C33). */
@Composable
fun WarrantyStatusLine(status: WarrantyStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (icon, color, text) = when (status) {
        is WarrantyStatus.Unknown -> Triple(Icons.Rounded.HelpOutline, scheme.onSurfaceVariant, stringResource(R.string.warranty_unknown))
        is WarrantyStatus.Active -> Triple(Icons.Rounded.CheckCircle, scheme.primary, stringResource(R.string.warranty_active, displayDate(status.endsOn)))
        is WarrantyStatus.Expiring -> Triple(Icons.Rounded.Schedule, scheme.tertiary, stringResource(R.string.warranty_expiring, displayDate(status.endsOn)))
        is WarrantyStatus.Expired -> Triple(Icons.Rounded.ErrorOutline, scheme.error, stringResource(R.string.warranty_expired, displayDate(status.endsOn)))
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

private fun displayDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(iso)

@ThemePreviews
@Composable
fun WarrantyStatusLinePreview() = HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
    Surface {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WarrantyStatusLine(WarrantyStatus.Active("2029-06-15"))
            WarrantyStatusLine(WarrantyStatus.Expiring("2026-12-01", 39))
            WarrantyStatusLine(WarrantyStatus.Expired("2025-03-14"))
            WarrantyStatusLine(WarrantyStatus.Unknown)
        }
    }
}
