package com.kurtz.homerecord.housemanual.report

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.coreui.platform.Intents
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.pdf.HomeReportFile
import com.kurtz.homerecord.housemodel.pdf.HomeReportSource

@Composable
fun ReportRoute(source: HomeReportSource, file: HomeReportFile, homeId: String, onBack: () -> Unit) {
    val vm = viewModel<ReportViewModel>(factory = viewModelFactory { initializer { ReportViewModel({ source.load(homeId) }, { file.export(homeId) }) } })
    val state = vm.state.collectAsState().value
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.report_share_title)
    LaunchedEffect(state.uri) {
        val uri = state.uri ?: return@LaunchedEffect
        vm.onEvent(ReportEvent.ShareHandled)
        Intents.shareUri(context, Uri.parse(uri), "application/pdf", shareTitle)
    }
    ReportScreen(state, onEvent = { if (it == ReportEvent.Back) onBack() else vm.onEvent(it) })
}
