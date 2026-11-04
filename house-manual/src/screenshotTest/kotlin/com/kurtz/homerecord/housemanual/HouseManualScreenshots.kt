package com.kurtz.homerecord.housemanual

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@ThemePreviews
@Composable
fun OnboardingEmptyScreenshot() = OnboardingEmptyPreview()

@PreviewTest
@ThemePreviews
@Composable
fun OnboardingFilledScreenshot() = OnboardingFilledPreview()

@PreviewTest
@ThemePreviews
@Composable
fun OnboardingErrorsScreenshot() = OnboardingErrorsPreview()

@PreviewTest
@ThemePreviews
@Composable
fun FloorsListScreenshot() = FloorsListPreview()

@PreviewTest
@ThemePreviews
@Composable
fun FloorsEmptyScreenshot() = FloorsEmptyPreview()

@PreviewTest
@ThemePreviews
@Composable
fun FloorsLoadingScreenshot() = FloorsLoadingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun FloorsDeleteDialogScreenshot() = FloorsDeleteDialogPreview()

@PreviewTest
@ThemePreviews
@Composable
fun FloorsEditDialogScreenshot() = FloorsEditDialogPreview()


@PreviewTest
@ThemePreviews
@Composable
fun PlanEditorScreenshot() = PlanEditorPreview()

@PreviewTest
@ThemePreviews
@Composable
fun PlanEditorSelectedScreenshot() = PlanEditorSelectedPreview()

@PreviewTest
@ThemePreviews
@Composable
fun PlanEditorShapeScreenshot() = PlanEditorShapePreview()

@PreviewTest
@ThemePreviews
@Composable
fun PlanEditorWallScreenshot() = PlanEditorWallPreview()

@PreviewTest
@ThemePreviews
@Composable
fun PlanEditorOverlaysScreenshot() = PlanEditorOverlaysPreview()

@PreviewTest
@ThemePreviews
@Composable
fun PlanEditorOverlayPickedScreenshot() = PlanEditorOverlayPickedPreview()

@PreviewTest
@ThemePreviews
@Composable
fun PlanEditorOverlayDrawingScreenshot() = PlanEditorOverlayDrawingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun PlanEditorOverlaysEmptyScreenshot() = PlanEditorOverlaysEmptyPreview()

@PreviewTest
@ThemePreviews
@Composable
fun PlanEditorDoorScreenshot() = PlanEditorDoorPreview()

@PreviewTest
@ThemePreviews
@Composable
fun PlanEditorStairsScreenshot() = PlanEditorStairsPreview()

@PreviewTest
@ThemePreviews
@Composable
fun PlanEditorOverlapScreenshot() = PlanEditorOverlapPreview()

@PreviewTest
@ThemePreviews
@Composable
fun PlanEditorUnplacedScreenshot() = PlanEditorUnplacedPreview()

@PreviewTest
@ThemePreviews
@Composable
fun PlanEditorEmptyScreenshot() = PlanEditorEmptyPreview()

@PreviewTest
@ThemePreviews
@Composable
fun PlanEditorLoadingScreenshot() = PlanEditorLoadingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun PlanEditorNoFloorsScreenshot() = PlanEditorNoFloorsPreview()

@PreviewTest
@ThemePreviews
@Composable
fun HouseBlueprintScreenshot() = HouseBlueprintPreview()

@PreviewTest
@Preview(name = "Landscape", device = "spec:width=891dp,height=411dp")
@Composable
fun HouseBlueprintLandscapeScreenshot() = HouseBlueprintLandscapePreview()

@PreviewTest
@ThemePreviews
@Composable
fun HouseLayersScreenshot() = HouseLayersPreview()

@PreviewTest
@ThemePreviews
@Composable
fun HouseEmptyFloorScreenshot() = HouseEmptyFloorPreview()

@PreviewTest
@ThemePreviews
@Composable
fun HouseLoadingScreenshot() = HouseLoadingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun HouseNoFloorsScreenshot() = HouseNoFloorsPreview()

@PreviewTest
@ThemePreviews
@Composable
fun RoomPageScreenshot() = RoomPagePreview()

@PreviewTest
@ThemePreviews
@Composable
fun RoomWallsScreenshot() = RoomWallsPreview()

@PreviewTest
@Preview(name = "Light", showBackground = true, heightDp = 1300)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, heightDp = 1300)
@Preview(name = "200% font", fontScale = 2f, showBackground = true, heightDp = 2200)
@Composable
fun RoomRemovedWallPhotosScreenshot() = RoomRemovedWallPhotosPreview()

@PreviewTest
@ThemePreviews
@Composable
fun RoomMissingScreenshot() = RoomMissingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemsEmptyScreenshot() = ItemsEmptyPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemsByCategoryScreenshot() = ItemsByCategoryPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemsByRoomScreenshot() = ItemsByRoomPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemsFilteredScreenshot() = ItemsFilteredPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemsNoMatchScreenshot() = ItemsNoMatchPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemsLoadingScreenshot() = ItemsLoadingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemEditNewScreenshot() = ItemEditNewPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemEditFilledScreenshot() = ItemEditFilledPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemEditErrorsScreenshot() = ItemEditErrorsPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemEditTypeSheetScreenshot() = ItemEditTypeSheetPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemEditTypeSheetSearchScreenshot() = ItemEditTypeSheetSearchPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemEditPlateSheetScreenshot() = ItemEditPlateSheetPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemEditLoadingScreenshot() = ItemEditLoadingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemDetailScreenshot() = ItemDetailPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemDetailNoWarrantyScreenshot() = ItemDetailNoWarrantyPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemDetailDeleteScreenshot() = ItemDetailDeletePreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemDetailMissingScreenshot() = ItemDetailMissingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemDetailLoadingScreenshot() = ItemDetailLoadingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemDetailLookupNoteScreenshot() = ItemDetailLookupNotePreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemDetailReviewBadgeScreenshot() = ItemDetailReviewBadgePreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemDetailReviewSheetScreenshot() = ItemDetailReviewSheetPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemDetailUpkeepScreenshot() = ItemDetailUpkeepPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemDetailConsumableDeleteScreenshot() = ItemDetailConsumableDeletePreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemDetailRecallsScreenshot() = ItemDetailRecallsPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemDetailConsumableFormScreenshot() = ItemDetailConsumableFormPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemDetailServiceFormScreenshot() = ItemDetailServiceFormPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ContactsListScreenshot() = ContactsListPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ContactsEmptyScreenshot() = ContactsEmptyPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ContactsLoadingScreenshot() = ContactsLoadingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ContactFormScreenshot() = ContactFormPreview()

@PreviewTest
@ThemePreviews
@Composable
fun DocumentsListScreenshot() = DocumentsListPreview()

@PreviewTest
@ThemePreviews
@Composable
fun DocumentsEmptyScreenshot() = DocumentsEmptyPreview()

@PreviewTest
@ThemePreviews
@Composable
fun DocumentsLoadingScreenshot() = DocumentsLoadingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun DocumentsUnsupportedScreenshot() = DocumentsUnsupportedPreview()

@PreviewTest
@ThemePreviews
@Composable
fun DocumentFormScreenshot() = DocumentFormPreview()

@PreviewTest
@ThemePreviews
@Composable
fun DocumentViewerScreenshot() = DocumentViewerPreview()

@PreviewTest
@ThemePreviews
@Composable
fun DocumentViewerMissingScreenshot() = DocumentViewerMissingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun HomeEmptyScreenshot() = HomeEmptyPreview()

@PreviewTest
@ThemePreviews
@Composable
fun HomeFullScreenshot() = HomeFullPreview()

@PreviewTest
@ThemePreviews
@Composable
fun HomeQuietScreenshot() = HomeQuietPreview()

@PreviewTest
@ThemePreviews
@Composable
fun HomeSearchScreenshot() = HomeSearchPreview()

@PreviewTest
@ThemePreviews
@Composable
fun HomeSearchNoneScreenshot() = HomeSearchNonePreview()

@PreviewTest
@ThemePreviews
@Composable
fun HomeLoadingScreenshot() = HomeLoadingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun PaywallItemsScreenshot() = PaywallItemsPreview()

@PreviewTest
@ThemePreviews
@Composable
fun PaywallFailedScreenshot() = PaywallFailedPreview()

@PreviewTest
@ThemePreviews
@Composable
fun PaywallLoadingScreenshot() = PaywallLoadingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun MoreSignedInScreenshot() = MoreSignedInPreview()

@PreviewTest
@ThemePreviews
@Composable
fun MoreSignedOutScreenshot() = MoreSignedOutPreview()

@PreviewTest
@ThemePreviews
@Composable
fun MoreSignOutConfirmScreenshot() = MoreSignOutConfirmPreview()

@PreviewTest
@ThemePreviews
@Composable
fun WallOutlineScreenshot() = WallOutlinePreview()

@PreviewTest
@ThemePreviews
@Composable
fun WallOutlineDraggingScreenshot() = WallOutlineDraggingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun WallOutlineCrossedScreenshot() = WallOutlineCrossedPreview()

@PreviewTest
@ThemePreviews
@Composable
fun WallOutlineSavingScreenshot() = WallOutlineSavingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun WallOutlineUnreadableScreenshot() = WallOutlineUnreadablePreview()

@PreviewTest
@ThemePreviews
@Composable
fun House3DScreenshot() = House3DPreview()

@PreviewTest
@ThemePreviews
@Composable
fun House3DMarkersScreenshot() = House3DMarkersPreview()

@PreviewTest
@ThemePreviews
@Composable
fun House3DRoughInScreenshot() = House3DRoughInPreview()

@PreviewTest
@ThemePreviews
@Composable
fun House3DRoomScreenshot() = House3DRoomPreview()

@PreviewTest
@ThemePreviews
@Composable
fun House3DStackedScreenshot() = House3DStackedPreview()

@PreviewTest
@ThemePreviews
@Composable
fun House3DAllFloorsScreenshot() = House3DAllFloorsPreview()

@PreviewTest
@ThemePreviews
@Composable
fun House3DEmptyScreenshot() = House3DEmptyPreview()

@PreviewTest
@ThemePreviews
@Composable
fun House3DLoadingScreenshot() = House3DLoadingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun House3DErrorScreenshot() = House3DErrorPreview()

@PreviewTest
@Preview(name = "Landscape", device = "spec:width=891dp,height=411dp")
@Composable
fun House3DLandscapeScreenshot() = House3DLandscapePreview()

@PreviewTest
@ThemePreviews
@Composable
fun WallOverlaysScreenshot() = WallOverlaysPreview()

@PreviewTest
@ThemePreviews
@Composable
fun WallOverlaysDrawingScreenshot() = WallOverlaysDrawingPreview()

@PreviewTest
@ThemePreviews
@Composable
fun WallOverlaysSelectedScreenshot() = WallOverlaysSelectedPreview()

@PreviewTest
@ThemePreviews
@Composable
fun WallOverlaysEmptyScreenshot() = WallOverlaysEmptyPreview()

@PreviewTest
@ThemePreviews
@Composable
fun WallOverlaysDeleteScreenshot() = WallOverlaysDeletePreview()

@PreviewTest
@ThemePreviews
@Composable
fun OverlayFormScreenshot() = OverlayFormPreview()

@PreviewTest
@ThemePreviews
@Composable
fun OverlayFormMissingKindScreenshot() = OverlayFormMissingKindPreview()

@PreviewTest
@ThemePreviews
@Composable
fun OverlayFormLinkedScreenshot() = OverlayFormLinkedPreview()

@PreviewTest
@ThemePreviews
@Composable
fun OverlayItemListScreenshot() = OverlayItemListPreview()

@PreviewTest
@ThemePreviews
@Composable
fun OverlayItemListNoMatchScreenshot() = OverlayItemListNoMatchPreview()

@PreviewTest
@ThemePreviews
@Composable
fun WallOverlaysSelectedLinkedScreenshot() = WallOverlaysSelectedLinkedPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemDetailLinksScreenshot() = ItemDetailLinksPreview()

@PreviewTest
@ThemePreviews
@Composable
fun OverlayKindListScreenshot() = OverlayKindListPreview()

@PreviewTest
@ThemePreviews
@Composable
fun RoomOverlaysScreenshot() = RoomOverlaysPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemPlaceScreenshot() = ItemPlacePreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemPlaceUnplacedScreenshot() = ItemPlaceUnplacedPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemPlaceOnWallScreenshot() = ItemPlaceOnWallPreview()

@PreviewTest
@ThemePreviews
@Composable
fun ItemPlaceEmptyFloorScreenshot() = ItemPlaceEmptyFloorPreview()
