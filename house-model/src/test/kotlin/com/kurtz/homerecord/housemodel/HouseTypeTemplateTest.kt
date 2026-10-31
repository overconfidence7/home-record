package com.kurtz.homerecord.housemodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AC-C2
class HouseTypeTemplateTest {
    private fun names(type: HouseType, foundation: Foundation, garage: Boolean = false) =
        HouseTypeTemplate.forHome(type, foundation, garage).floors.map { it.role }

    @Test fun rancherWithBasementHasBasementAndMain() {
        assertThat(names(HouseType.RANCHER, Foundation.BASEMENT)).containsExactly(FloorRole.BASEMENT, FloorRole.MAIN).inOrder()
        assertThat(names(HouseType.RANCHER, Foundation.WALKOUT_BASEMENT)).containsExactly(FloorRole.BASEMENT, FloorRole.MAIN).inOrder()
    }

    @Test fun rancherOnSlabOrCrawlspaceHasMainOnly() {
        assertThat(names(HouseType.RANCHER, Foundation.SLAB)).containsExactly(FloorRole.MAIN)
        assertThat(names(HouseType.RANCHER, Foundation.CRAWLSPACE)).containsExactly(FloorRole.MAIN)
    }

    @Test fun twoStoryAndColonialHaveMainAndUpper() {
        assertThat(names(HouseType.TWO_STORY, Foundation.SLAB)).containsExactly(FloorRole.MAIN, FloorRole.UPPER).inOrder()
        assertThat(names(HouseType.COLONIAL, Foundation.BASEMENT)).containsExactly(FloorRole.BASEMENT, FloorRole.MAIN, FloorRole.UPPER).inOrder()
    }

    @Test fun capeCodUpperIsHalfStory() {
        val floors = HouseTypeTemplate.forHome(HouseType.CAPE_COD, Foundation.BASEMENT, false).floors
        assertThat(floors.map { it.role }).containsExactly(FloorRole.BASEMENT, FloorRole.MAIN, FloorRole.UPPER).inOrder()
        assertThat(floors.single { it.role == FloorRole.UPPER }.halfLevel).isTrue()
        assertThat(floors.single { it.role == FloorRole.MAIN }.halfLevel).isFalse()
    }

    @Test fun splitLevelHasThreeHalfLevelOffsets() {
        val floors = HouseTypeTemplate.forHome(HouseType.SPLIT_LEVEL, Foundation.SLAB, false).floors
        assertThat(floors.map { it.role }).containsExactly(FloorRole.LOWER, FloorRole.MAIN, FloorRole.UPPER).inOrder()
        assertThat(floors.map { it.elevationIn }).containsExactly(-48, 0, 48).inOrder()
        assertThat(floors.map { it.halfLevel }).containsExactly(true, false, true).inOrder()
    }

    @Test fun raisedRanchHasLowerAndUpperRegardlessOfFoundation() {
        assertThat(names(HouseType.RAISED_RANCH, Foundation.SLAB)).containsExactly(FloorRole.LOWER, FloorRole.UPPER).inOrder()
        assertThat(names(HouseType.RAISED_RANCH, Foundation.BASEMENT)).containsExactly(FloorRole.LOWER, FloorRole.UPPER).inOrder()
    }

    @Test fun townhouseAddsLowerOnlyWithBasement() {
        assertThat(names(HouseType.TOWNHOUSE, Foundation.SLAB)).containsExactly(FloorRole.MAIN, FloorRole.UPPER).inOrder()
        assertThat(names(HouseType.TOWNHOUSE, Foundation.BASEMENT)).containsExactly(FloorRole.LOWER, FloorRole.MAIN, FloorRole.UPPER).inOrder()
    }

    @Test fun walkoutMarksBasementAtGrade() {
        val walkout = HouseTypeTemplate.forHome(HouseType.TWO_STORY, Foundation.WALKOUT_BASEMENT, false).floors
        assertThat(walkout.first().exteriorWallAtGrade).isTrue()
        val plain = HouseTypeTemplate.forHome(HouseType.TWO_STORY, Foundation.BASEMENT, false).floors
        assertThat(plain.first().exteriorWallAtGrade).isFalse()
    }

    @Test fun garageGoesOnMainFloor() {
        val t = HouseTypeTemplate.forHome(HouseType.TWO_STORY, Foundation.BASEMENT, true)
        assertThat(t.floors[checkNotNull(t.garageFloorIndex)].role).isEqualTo(FloorRole.MAIN)
        assertThat(HouseTypeTemplate.forHome(HouseType.TWO_STORY, Foundation.BASEMENT, false).garageFloorIndex).isNull()
    }

    @Test fun garageFallsBackToLowerWhenThereIsNoMain() {
        val t = HouseTypeTemplate.forHome(HouseType.RAISED_RANCH, Foundation.SLAB, true)
        assertThat(t.floors[checkNotNull(t.garageFloorIndex)].role).isEqualTo(FloorRole.LOWER)
    }

    @Test fun ceilingsDefaultToEightFeetAndFloorsRiseInOrder() {
        val floors = HouseTypeTemplate.forHome(HouseType.COLONIAL, Foundation.BASEMENT, false).floors
        assertThat(floors.map { it.ceilingHeightIn }.toSet()).containsExactly(96)
        assertThat(floors.map { it.elevationIn }).isInOrder()
    }

    @Test fun parseFallsBackOnUnknownValues() {
        assertThat(HouseType.parse("nope")).isEqualTo(HouseType.OTHER)
        assertThat(Foundation.parse("nope")).isEqualTo(Foundation.BASEMENT)
        assertThat(HouseType.parse("COLONIAL")).isEqualTo(HouseType.COLONIAL)
    }
}
