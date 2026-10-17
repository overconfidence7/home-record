package com.kurtz.homerecord.fieldlog.report

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kurtz.homerecord.core.model.DelayReason
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.ui.theme.HomeRecordTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun ReportRoute(viewModel: ReportViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.pdfReady) {
        state.pdfReady?.let { sharePdf(context, it) }
        if (state.pdfReady != null) viewModel.onEvent(ReportEvent.PdfShared)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }
    ReportScreen(state, viewModel::onEvent)
}

private fun sharePdf(context: Context, relativePath: String) {
    val file = File(context.cacheDir, relativePath)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.report_make_pdf)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(state: ReportUiState, onEvent: (ReportEvent) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.report_title), style = MaterialTheme.typography.headlineMedium)
        Text(state.jobName, style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = { picking = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("report_day"),
        ) { Text(stringResource(R.string.report_day, state.day)) }
        Text(
            pluralStringResource(R.plurals.report_photos, state.photoCount, state.photoCount),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (state.loaded) {
            ReportField(R.string.report_weather, state.weather, { onEvent(ReportEvent.WeatherChanged(it)) }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ReportField(
                    R.string.report_crew, state.crew, { onEvent(ReportEvent.CrewChanged(it)) },
                    Modifier.weight(1f), singleLine = true, keyboardType = KeyboardType.Number,
                )
                ReportField(
                    R.string.report_hours, state.hours, { onEvent(ReportEvent.HoursChanged(it)) },
                    Modifier.weight(1f), singleLine = true, keyboardType = KeyboardType.Decimal,
                )
            }
            ReportField(R.string.report_work, state.workCompleted, { onEvent(ReportEvent.WorkChanged(it)) }, minLines = 4, tag = "report_work")
            Text(stringResource(R.string.report_delay_reason), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DelayReason.entries.forEach { reason ->
                    FilterChip(
                        selected = reason == state.delayReason,
                        onClick = { onEvent(ReportEvent.DelayReasonChanged(reason)) },
                        label = { Text(stringResource(reason.label())) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            ReportField(R.string.report_delays, state.delays, { onEvent(ReportEvent.DelaysChanged(it)) }, minLines = 2)
            ReportField(R.string.report_materials, state.materials, { onEvent(ReportEvent.MaterialsChanged(it)) }, minLines = 2)
            ReportField(R.string.report_next_steps, state.nextSteps, { onEvent(ReportEvent.NextStepsChanged(it)) }, minLines = 2)
            Button(
                onClick = { onEvent(ReportEvent.Save) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(if (state.saved) R.string.report_saved else R.string.report_save)) }
            Button(
                onClick = { onEvent(ReportEvent.MakePdf) },
                enabled = !state.makingPdf,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("report_make_pdf"),
            ) { Text(stringResource(if (state.makingPdf) R.string.report_making_pdf else R.string.report_make_pdf)) }
            if (state.pdfFailed) {
                Text(
                    stringResource(R.string.report_pdf_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    if (picking) {
        val initial = runCatching { LocalDate.parse(state.day) }.getOrNull()
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onEvent(ReportEvent.DayChanged(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()))
                    }
                    picking = false
                }) { Text(stringResource(R.string.report_ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.report_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun ReportField(
    label: Int,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    tag: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = (if (modifier == Modifier) Modifier.fillMaxWidth() else modifier).let { if (tag != null) it.testTag(tag) else it },
    )
}

fun DelayReason.label(): Int = when (this) {
    DelayReason.NONE -> R.string.delay_none
    DelayReason.WEATHER -> R.string.delay_weather
    DelayReason.MATERIALS -> R.string.delay_materials
    DelayReason.CLIENT -> R.string.delay_client
    DelayReason.INSPECTION -> R.string.delay_inspection
    DelayReason.SUBCONTRACTOR -> R.string.delay_subcontractor
    DelayReason.OTHER -> R.string.delay_other
}

@Preview(showBackground = true)
@Composable
private fun ReportScreenPreview() {
    HomeRecordTheme {
        ReportScreen(ReportUiState(loaded = true, jobName = "Elm St", day = "2026-10-14", workCompleted = "Framed wall", photoCount = 3), {})
    }
}
