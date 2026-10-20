package com.kurtz.homerecord.housemanual

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.house.FloorsDialog
import com.kurtz.homerecord.housemanual.house.FloorsScreen
import com.kurtz.homerecord.housemanual.house.FloorsUiState
import com.kurtz.homerecord.housemanual.house.HouseRoot
import com.kurtz.homerecord.housemanual.onboarding.OnboardingScreen
import com.kurtz.homerecord.housemanual.onboarding.OnboardingUiState
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HouseType

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "200% font", fontScale = 2f, showBackground = true)
annotation class ThemePreviews

@Composable
private fun Frame(content: @Composable () -> Unit) = HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { Surface { content() } }

private val floors = listOf(
    FloorSummary("f1", "Basement", 96, false, 4),
    FloorSummary("f2", "Main", 96, false, 7),
    FloorSummary("f3", "Upper", 96, false, 5),
)

private val home = HomeEntity(
    id = "h1", name = "Maple St", houseType = "COLONIAL", foundation = "BASEMENT", attachedGarage = true, createdAt = 1, updatedAt = 1,
)

@ThemePreviews
@Composable
fun OnboardingEmptyPreview() = Frame { OnboardingScreen(OnboardingUiState(), {}) }

@ThemePreviews
@Composable
fun OnboardingFilledPreview() = Frame {
    OnboardingScreen(
        OnboardingUiState(
            name = "Maple St",
            address = "418 Maple St, Harlan",
            type = HouseType.SPLIT_LEVEL,
            foundation = Foundation.WALKOUT_BASEMENT,
            attachedGarage = true,
        ),
        {},
    )
}

@ThemePreviews
@Composable
fun OnboardingErrorsPreview() = Frame { OnboardingScreen(OnboardingUiState(showErrors = true), {}) }

@ThemePreviews
@Composable
fun FloorsListPreview() = Frame { FloorsScreen(FloorsUiState(loading = false, floors = floors), {}, {}, "Maple St") }

@ThemePreviews
@Composable
fun FloorsEmptyPreview() = Frame { FloorsScreen(FloorsUiState(loading = false), {}, {}, "Maple St") }

@ThemePreviews
@Composable
fun FloorsLoadingPreview() = Frame { FloorsScreen(FloorsUiState(), {}, {}, "Maple St") }

@ThemePreviews
@Composable
fun FloorsDeleteDialogPreview() = Frame {
    FloorsScreen(FloorsUiState(loading = false, floors = floors, dialog = FloorsDialog.Delete(floors[1])), {}, {}, "Maple St")
}

@ThemePreviews
@Composable
fun FloorsEditDialogPreview() = Frame {
    FloorsScreen(FloorsUiState(loading = false, floors = floors, dialog = FloorsDialog.Edit(floors[2])), {}, {}, "Maple St")
}

@ThemePreviews
@Composable
fun HouseRootPreview() = Frame { HouseRoot(home, floorCount = 3, onOpenFloors = {}) }
