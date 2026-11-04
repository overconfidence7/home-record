package com.kurtz.homerecord.fieldlog.materials

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.material.JobMaterialGroup
import com.kurtz.homerecord.core.material.Material
import com.kurtz.homerecord.core.material.MaterialCategory
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** FL-Materials (AC-F44): receipts by job, each job's total, the total for all jobs, Add receipt and Export CSV. */
@Composable
fun MaterialsScreen(state: MaterialsUiState, onEvent: (MaterialsEvent) -> Unit, onBack: () -> Unit) {
    ScreenScaffold(
        title = stringResource(R.string.materials_title),
        onBack = onBack,
        actions = {
            if (state.groups.isNotEmpty()) {
                IconButton({ onEvent(MaterialsEvent.Export) }, Modifier.testTag("fl_materials_export")) {
                    Icon(Icons.Rounded.IosShare, contentDescription = stringResource(R.string.materials_export))
                }
            }
        },
        floatingActionButton = {
            if (!state.loading && state.groups.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onEvent(MaterialsEvent.Add) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.materials_add)) },
                    modifier = Modifier.testTag("fl_materials_add"),
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.groups.isEmpty() -> EmptyState(
                icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                headline = stringResource(R.string.materials_empty_title),
                body = stringResource(R.string.materials_empty_body),
                actionLabel = stringResource(R.string.materials_add),
                onAction = { onEvent(MaterialsEvent.Add) },
                modifier = Modifier.padding(padding).testTag("fl_materials_empty"),
            )
            else -> LazyColumn(Modifier.padding(padding).testTag("fl_materials_list"), contentPadding = PaddingValues(bottom = 96.dp)) {
                item("all") {
                    TotalRow(stringResource(R.string.materials_all_jobs), state.totalCents, Modifier.testTag("fl_materials_total"))
                    HorizontalDivider()
                }
                state.groups.forEach { group ->
                    item("job_${group.jobId}") { TotalRow(group.jobName, group.totalCents, Modifier.padding(top = 8.dp).testTag("fl_materials_job_${group.jobId}")) }
                    items(group.items, key = { it.id }) { m -> MaterialRow(m) { onEvent(MaterialsEvent.Open(m.id)) } }
                }
            }
        }
    }
    if (state.exportFailed) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(MaterialsEvent.DismissExportFailed) },
            text = { Text(stringResource(R.string.materials_export_failed)) },
            confirmButton = { TextButton({ onEvent(MaterialsEvent.DismissExportFailed) }, Modifier.testTag("fl_materials_export_failed")) { Text(stringResource(com.kurtz.homerecord.coreui.R.string.ok)) } },
        )
    }
}

@Composable
private fun TotalRow(label: String, cents: Long, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(end = 16.dp))
        Text(money(cents), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MaterialRow(m: Material, onClick: () -> Unit) {
    val category = categoryLabel(m.category)
    val date = runCatching { LocalDate.parse(m.date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(m.date)
    RecordListItem(
        headline = m.vendor ?: category,
        supporting = listOfNotNull(date, category.takeIf { m.vendor != null }, m.note).joinToString(" · "),
        leading = { Icon(categoryIcon(m.category), contentDescription = null) },
        trailing = { Column(horizontalAlignment = Alignment.End) { Text(money(m.amountCents), style = MaterialTheme.typography.bodyLarge) } },
        onClick = onClick,
        modifier = Modifier.testTag("fl_materials_row_${m.id}"),
    )
}

/** "$1,234.56" in the phone's locale. */
fun money(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.US).format(cents / 100.0)

@Composable
fun categoryLabel(c: MaterialCategory): String = stringResource(categoryLabelRes(c))

fun categoryLabelRes(c: MaterialCategory): Int = when (c) {
    MaterialCategory.MATERIALS -> R.string.material_cat_materials
    MaterialCategory.TOOLS -> R.string.material_cat_tools
    MaterialCategory.EQUIPMENT_RENTAL -> R.string.material_cat_rental
    MaterialCategory.FUEL -> R.string.material_cat_fuel
    MaterialCategory.PERMITS -> R.string.material_cat_permits
    MaterialCategory.OTHER -> R.string.material_cat_other
}

fun categoryIcon(c: MaterialCategory): ImageVector = when (c) {
    MaterialCategory.MATERIALS -> Icons.Rounded.Inventory2
    MaterialCategory.TOOLS -> Icons.Rounded.Handyman
    MaterialCategory.EQUIPMENT_RENTAL -> Icons.Rounded.Construction
    MaterialCategory.FUEL -> Icons.Rounded.LocalGasStation
    MaterialCategory.PERMITS -> Icons.Rounded.Description
    MaterialCategory.OTHER -> Icons.AutoMirrored.Rounded.ReceiptLong
}
