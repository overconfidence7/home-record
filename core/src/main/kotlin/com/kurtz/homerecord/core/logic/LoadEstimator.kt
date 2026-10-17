package com.kurtz.homerecord.core.logic

/** AC-114: appliances offered in the estimate form, with the default VA shown (editable). */
enum class LoadAppliance(val defaultVa: Int) {
    RANGE(12_000),
    WALL_OVEN(8_000),
    COOKTOP(7_200),
    DRYER(5_000),
    WATER_HEATER(4_500),
    DISHWASHER(1_200),
    DISPOSAL(900),
    MICROWAVE(1_500),
    EV_CHARGER(7_200),
    HOT_TUB(6_000),
    WELL_PUMP(1_500),
    POOL_PUMP(1_500),
    OTHER(0),
}

/** AC-114: HVAC choices; [defaultVa] is 0 for NONE. */
enum class HvacKind(val defaultVa: Int) {
    NONE(0),
    AC_ONLY(3_500),
    HEAT_PUMP(4_000),
    HEAT_PUMP_STRIP(4_000),
    ELECTRIC_HEAT_UNDER_4(10_000),
    ELECTRIC_HEAT_4_PLUS(15_000),
}

data class LoadInput(
    val squareFeet: Int,
    /** Only the appliances that are switched on, with their VA. */
    val appliances: Map<LoadAppliance, Int>,
    val hvac: HvacKind = HvacKind.NONE,
    val hvacVa: Int = 0,
    /** Strip-heat VA, used only with [HvacKind.HEAT_PUMP_STRIP] (counted at 65%). */
    val stripVa: Int = 0,
    val serviceRatingAmps: Int? = null,
)

enum class LoadVerdict { ROOM_TO_GROW, NEAR_CAPACITY, EXCEEDS, UNKNOWN_RATING }

data class LoadResult(
    val generalVa: Int,
    val demandVa: Int,
    val hvacVa: Int,
    val totalVa: Int,
    val amps: Int,
    val ratingAmps: Int?,
    val verdict: LoadVerdict,
    /** Estimated amps as a share of the rating (0f when unknown), for the bar. */
    val fraction: Float,
)

/** AC-115..AC-118: NEC 220.82 optional method for an existing dwelling, 240 V service. */
object LoadEstimator {
    const val DEFAULT_STRIP_VA = 10_000
    const val SMALL_APPLIANCE_AND_LAUNDRY_VA = 4_500
    const val VA_PER_SQ_FT = 3
    const val FIRST_BLOCK_VA = 10_000
    const val SERVICE_VOLTS = 240

    fun hvacDemandVa(kind: HvacKind, va: Int, stripVa: Int = 0): Int = when (kind) {
        HvacKind.NONE -> 0
        HvacKind.AC_ONLY, HvacKind.HEAT_PUMP -> va
        HvacKind.HEAT_PUMP_STRIP -> va + (stripVa * 65 + 50) / 100
        HvacKind.ELECTRIC_HEAT_UNDER_4 -> (va * 65 + 50) / 100
        HvacKind.ELECTRIC_HEAT_4_PLUS -> (va * 40 + 50) / 100
    }

    fun estimate(input: LoadInput): LoadResult {
        val general = VA_PER_SQ_FT * input.squareFeet.coerceAtLeast(0) +
            SMALL_APPLIANCE_AND_LAUNDRY_VA + input.appliances.values.sumOf { it.coerceAtLeast(0) }
        val remainder = (general - FIRST_BLOCK_VA).coerceAtLeast(0)
        val demand = minOf(general, FIRST_BLOCK_VA) + (remainder * 40 + 50) / 100
        val hvac = hvacDemandVa(input.hvac, input.hvacVa.coerceAtLeast(0), input.stripVa.coerceAtLeast(0))
        val total = demand + hvac
        val amps = (total + SERVICE_VOLTS - 1) / SERVICE_VOLTS
        val rating = input.serviceRatingAmps?.takeIf { it > 0 }
        val fraction = rating?.let { amps.toFloat() / it } ?: 0f
        val verdict = when {
            rating == null -> LoadVerdict.UNKNOWN_RATING
            fraction <= 0.8f -> LoadVerdict.ROOM_TO_GROW
            fraction <= 1f -> LoadVerdict.NEAR_CAPACITY
            else -> LoadVerdict.EXCEEDS
        }
        return LoadResult(general, demand, hvac, total, amps, rating, verdict, fraction)
    }
}

/** Text form of the enabled-appliance map stored in the LoadProfile row. */
object LoadProfileCodec {
    fun encode(appliances: Map<LoadAppliance, Int>): String =
        appliances.entries.joinToString(",") { "${it.key.name}:${it.value}" }

    fun decode(text: String): Map<LoadAppliance, Int> = text.split(',').mapNotNull { part ->
        val (name, va) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        val appliance = runCatching { LoadAppliance.valueOf(name) }.getOrNull() ?: return@mapNotNull null
        val value = va.toIntOrNull() ?: return@mapNotNull null
        appliance to value
    }.toMap()
}

/** AC-114: text-field state of the estimate form. An appliance key present in [appliances] is switched on. */
data class LoadForm(
    val squareFeet: String = "",
    val appliances: Map<LoadAppliance, String> = emptyMap(),
    val hvac: HvacKind = HvacKind.NONE,
    val hvacVa: String = "",
    val stripVa: String = LoadEstimator.DEFAULT_STRIP_VA.toString(),
) {
    fun toggled(appliance: LoadAppliance): LoadForm =
        if (appliance in appliances) copy(appliances = appliances - appliance)
        else copy(appliances = appliances + (appliance to appliance.defaultVa.takeIf { it > 0 }?.toString().orEmpty()))

    fun withHvac(kind: HvacKind): LoadForm =
        copy(hvac = kind, hvacVa = kind.defaultVa.takeIf { it > 0 }?.toString().orEmpty())

    /** Null until the square footage and every switched-on VA parse as whole numbers (blank VA = invalid). */
    fun toInput(serviceRatingAmps: Int?): LoadInput? {
        val sqft = squareFeet.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val parsed = appliances.mapValues { (_, text) -> text.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null }
        val hvacValue = if (hvac == HvacKind.NONE) 0 else hvacVa.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val strip = if (hvac == HvacKind.HEAT_PUMP_STRIP) stripVa.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null else 0
        return LoadInput(sqft, parsed, hvac, hvacValue, strip, serviceRatingAmps)
    }

    companion object {
        /** Saved profile wins; square feet comes from the Home when there is no profile yet. */
        fun from(squareFeet: Int?, saved: com.kurtz.homerecord.core.model.LoadProfile?): LoadForm {
            val base = LoadForm(squareFeet = squareFeet?.toString().orEmpty())
            val p = saved ?: return base
            return base.copy(
                appliances = p.appliances.mapValues { it.value.toString() },
                hvac = p.hvac,
                hvacVa = if (p.hvac == HvacKind.NONE) "" else p.hvacVa.toString(),
                stripVa = p.stripVa.toString(),
            )
        }
    }
}
