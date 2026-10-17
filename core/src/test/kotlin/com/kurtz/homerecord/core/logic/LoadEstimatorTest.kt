package com.kurtz.homerecord.core.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class LoadEstimatorTest {
    private val workedExample = LoadInput(
        squareFeet = 2_000,
        appliances = mapOf(
            LoadAppliance.RANGE to 12_000, LoadAppliance.DRYER to 5_000, LoadAppliance.WATER_HEATER to 4_500,
            LoadAppliance.DISHWASHER to 1_200, LoadAppliance.DISPOSAL to 900,
        ),
        hvac = HvacKind.AC_ONLY,
        hvacVa = 3_500,
    )

    @Test fun worked_example_gives_97_amps() {
        val r = LoadEstimator.estimate(workedExample)
        assertEquals(34_100, r.generalVa)
        assertEquals(19_640, r.demandVa)
        assertEquals(23_140, r.totalVa)
        assertEquals(97, r.amps)
    }

    @Test fun defaults_table_matches_spec() {
        assertEquals(12_000, LoadAppliance.RANGE.defaultVa)
        assertEquals(8_000, LoadAppliance.WALL_OVEN.defaultVa)
        assertEquals(7_200, LoadAppliance.COOKTOP.defaultVa)
        assertEquals(5_000, LoadAppliance.DRYER.defaultVa)
        assertEquals(4_500, LoadAppliance.WATER_HEATER.defaultVa)
        assertEquals(1_200, LoadAppliance.DISHWASHER.defaultVa)
        assertEquals(900, LoadAppliance.DISPOSAL.defaultVa)
        assertEquals(1_500, LoadAppliance.MICROWAVE.defaultVa)
        assertEquals(7_200, LoadAppliance.EV_CHARGER.defaultVa)
        assertEquals(6_000, LoadAppliance.HOT_TUB.defaultVa)
        assertEquals(1_500, LoadAppliance.WELL_PUMP.defaultVa)
        assertEquals(1_500, LoadAppliance.POOL_PUMP.defaultVa)
    }

    @Test fun small_house_under_first_block_is_all_at_100_percent() {
        val r = LoadEstimator.estimate(LoadInput(squareFeet = 1_000, appliances = emptyMap()))
        assertEquals(7_500, r.generalVa)
        assertEquals(7_500, r.demandVa)
        assertEquals(32, r.amps) // 7,500 / 240 = 31.25
    }

    @Test fun hvac_percentages() {
        assertEquals(0, LoadEstimator.hvacDemandVa(HvacKind.NONE, 5_000))
        assertEquals(5_000, LoadEstimator.hvacDemandVa(HvacKind.AC_ONLY, 5_000))
        assertEquals(5_000, LoadEstimator.hvacDemandVa(HvacKind.HEAT_PUMP, 5_000))
        assertEquals(4_000 + 6_500, LoadEstimator.hvacDemandVa(HvacKind.HEAT_PUMP_STRIP, 4_000, 10_000))
        assertEquals(6_500, LoadEstimator.hvacDemandVa(HvacKind.ELECTRIC_HEAT_UNDER_4, 10_000))
        assertEquals(4_000, LoadEstimator.hvacDemandVa(HvacKind.ELECTRIC_HEAT_4_PLUS, 10_000))
    }

    @Test fun verdict_thresholds() {
        fun verdict(rating: Int?) = LoadEstimator.estimate(workedExample.copy(serviceRatingAmps = rating)).verdict
        assertEquals(LoadVerdict.ROOM_TO_GROW, verdict(200)) // 97/200 = 48%
        assertEquals(LoadVerdict.ROOM_TO_GROW, verdict(125)) // 77.6%
        assertEquals(LoadVerdict.NEAR_CAPACITY, verdict(100)) // 97%
        assertEquals(LoadVerdict.NEAR_CAPACITY, verdict(97)) // exactly 100%
        assertEquals(LoadVerdict.EXCEEDS, verdict(60))
        assertEquals(LoadVerdict.UNKNOWN_RATING, verdict(null))
    }

    @Test fun exactly_80_percent_is_room_to_grow() {
        // general 33,000 -> demand 10,000 + 40% x 23,000 = 19,200 VA = 80 A
        val r = LoadEstimator.estimate(
            LoadInput(squareFeet = 0, appliances = mapOf(LoadAppliance.OTHER to 28_500), serviceRatingAmps = 100),
        )
        assertEquals(80, r.amps)
        assertEquals(LoadVerdict.ROOM_TO_GROW, r.verdict)
    }

    @Test fun codec_round_trips_and_ignores_junk() {
        val map = mapOf(LoadAppliance.RANGE to 11_000, LoadAppliance.OTHER to 2_500)
        assertEquals(map, LoadProfileCodec.decode(LoadProfileCodec.encode(map)))
        assertEquals(emptyMap<LoadAppliance, Int>(), LoadProfileCodec.decode(""))
        assertEquals(mapOf(LoadAppliance.DRYER to 5_000), LoadProfileCodec.decode("BOGUS:1,DRYER:5000,RANGE:x,RANGE"))
    }
}

class LoadFormTest {
    @Test fun toggle_adds_default_va_and_removes() {
        val on = LoadForm().toggled(LoadAppliance.DRYER)
        assertEquals(mapOf(LoadAppliance.DRYER to "5000"), on.appliances)
        assertEquals(emptyMap<LoadAppliance, String>(), on.toggled(LoadAppliance.DRYER).appliances)
        assertEquals(mapOf(LoadAppliance.OTHER to ""), LoadForm().toggled(LoadAppliance.OTHER).appliances)
    }

    @Test fun invalid_numbers_give_no_input() {
        assertEquals(null, LoadForm().toInput(200))
        assertEquals(null, LoadForm("2000").toggled(LoadAppliance.OTHER).toInput(200))
        assertEquals(null, LoadForm("2000").withHvac(HvacKind.AC_ONLY).copy(hvacVa = "x").toInput(200))
    }

    @Test fun parses_and_edits_flow_to_estimate() {
        val form = LoadForm("2000").toggled(LoadAppliance.RANGE).toggled(LoadAppliance.DRYER)
            .toggled(LoadAppliance.WATER_HEATER).toggled(LoadAppliance.DISHWASHER).toggled(LoadAppliance.DISPOSAL)
            .withHvac(HvacKind.AC_ONLY).copy(hvacVa = "3500")
        assertEquals(97, LoadEstimator.estimate(form.toInput(200)!!).amps)
    }

    @Test fun strip_heat_only_counts_for_heat_pump_with_strips() {
        val form = LoadForm("1000").withHvac(HvacKind.HEAT_PUMP_STRIP).copy(stripVa = "10000")
        assertEquals(4_000 + 6_500, LoadEstimator.estimate(form.toInput(null)!!).hvacVa)
        assertEquals(4_000, LoadEstimator.estimate(form.withHvac(HvacKind.HEAT_PUMP).toInput(null)!!.copy()).hvacVa)
    }

    @Test fun from_prefills_square_feet_and_saved_profile() {
        assertEquals("1800", LoadForm.from(1800, null).squareFeet)
    }
}
