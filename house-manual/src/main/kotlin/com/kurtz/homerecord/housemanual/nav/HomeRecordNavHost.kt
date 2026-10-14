package com.kurtz.homerecord.housemanual.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kurtz.homerecord.housemanual.AppContainer
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemanual.onboarding.OnboardingRoute
import com.kurtz.homerecord.housemanual.onboarding.OnboardingViewModel
import com.kurtz.homerecord.housemanual.onboarding.QuickRoom

@Composable
fun HomeRecordNavHost(container: AppContainer) {
    val start by produceState<String?>(initialValue = null, container) {
        value = StartDestination.resolve(container.homeRepository)
    }
    val startRoute = start ?: return
    val nav = rememberNavController()
    val resources = androidx.compose.ui.platform.LocalResources.current
    NavHost(navController = nav, startDestination = startRoute) {
        composable(Routes.ONBOARDING) {
            val vm: OnboardingViewModel = viewModel(
                factory = remember {
                    simpleFactory { OnboardingViewModel(container.homeRepository) { room: QuickRoom -> resources.getString(room.label) } }
                },
            )
            OnboardingRoute(vm, onDone = {
                nav.navigate(Routes.PANEL) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        // Placeholders until T05 (hub) and T06 (panel) land.
        composable(Routes.HOME) {
            val home by container.homeRepository.observeHome().collectAsState(initial = null)
            CenteredText(home?.name ?: "", tag = "home_screen")
        }
        composable(Routes.PANEL) { CenteredText(stringResource(R.string.panel_placeholder), tag = "panel_screen") }
    }
}

@Composable
private fun CenteredText(text: String, tag: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, modifier = Modifier.testTag(tag))
    }
}
