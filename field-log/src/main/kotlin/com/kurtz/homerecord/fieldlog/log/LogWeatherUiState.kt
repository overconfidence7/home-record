package com.kurtz.homerecord.fieldlog.log

import androidx.compose.runtime.Immutable
import com.kurtz.homerecord.core.model.LogWeather
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/** The weather form as typed; kept in the saved state so rotation and process death keep it (rule 33). */
@Serializable
data class WeatherDraft(val conditions: String? = null, val high: String = "", val low: String = "", val precip: String = "")

enum class WeatherFieldError { NOT_A_NUMBER, OUT_OF_RANGE, LOW_ABOVE_HIGH }

data class WeatherCheck(
    val conditionsMissing: Boolean,
    val high: WeatherFieldError?,
    val low: WeatherFieldError?,
    val precip: WeatherFieldError?,
    /** The corrected weather, or null while something above fails. */
    val weather: LogWeather?,
)

/**
 * Turns the weather form into a [LogWeather] (AC-F30: the weather section is editable). Conditions are required; high,
 * low and rain or snow may stay blank. Temperatures are whole °F (a decimal rounds), rain or snow is inches.
 */
object LogWeatherForm {
    /** The condition keys the backend sends (AC-B32), in the order a person scans them; "unknown" is never picked. */
    val CONDITIONS = listOf("clear", "partly_cloudy", "cloudy", "fog", "drizzle", "rain", "showers", "freezing_rain", "snow", "thunderstorm")

    private const val MIN_F = -80
    private const val MAX_F = 140
    private const val MAX_PRECIP_IN = 40.0

    fun draftOf(w: LogWeather?): WeatherDraft = WeatherDraft(
        conditions = w?.conditions?.takeIf { it in CONDITIONS },
        high = w?.highF?.toString().orEmpty(),
        low = w?.lowF?.toString().orEmpty(),
        precip = w?.precipIn?.let { if (it == 0.0) "0" else it.toBigDecimal().stripTrailingZeros().toPlainString() }.orEmpty(),
    )

    fun check(d: WeatherDraft, previous: LogWeather? = null): WeatherCheck {
        val high = temp(d.high)
        val low = temp(d.low)
        val precip = number(d.precip)
        val highError = high.error
        var lowError = low.error
        if (highError == null && lowError == null && high.value != null && low.value != null && low.value > high.value) {
            lowError = WeatherFieldError.LOW_ABOVE_HIGH
        }
        val precipError = precip.error ?: precip.value?.let { if (it < 0 || it > MAX_PRECIP_IN) WeatherFieldError.OUT_OF_RANGE else null }
        val conditions = d.conditions?.takeIf { it in CONDITIONS }
        val ok = conditions != null && highError == null && lowError == null && precipError == null
        return WeatherCheck(
            conditionsMissing = conditions == null,
            high = highError,
            low = lowError,
            precip = precipError,
            weather = if (!ok || conditions == null) null else LogWeather(
                highF = high.value,
                lowF = low.value,
                precipIn = precip.value,
                conditions = conditions,
                fetchedAt = previous?.fetchedAt ?: 0,
            ),
        )
    }

    private class Parsed<T>(val value: T?, val error: WeatherFieldError?)

    private fun number(text: String): Parsed<Double> {
        val t = text.trim().removeSuffix("\"").removeSuffix("in").removeSuffix("°F").removeSuffix("°").trim().replace(',', '.')
        if (t.isEmpty()) return Parsed(null, null)
        val v = t.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return Parsed(null, WeatherFieldError.NOT_A_NUMBER)
        return Parsed(v, null)
    }

    private fun temp(text: String): Parsed<Int> {
        val n = number(text)
        val v = n.value ?: return Parsed(null, n.error)
        val r = v.roundToInt()
        return if (r < MIN_F || r > MAX_F) Parsed(null, WeatherFieldError.OUT_OF_RANGE) else Parsed(r, null)
    }
}

/** FL-Log weather form: correct a day's weather by hand, or go back to looking it up (AC-F30, AC-F31). */
@Immutable
data class LogWeatherUiState(
    val day: LocalDate,
    val loading: Boolean = true,
    /** No log for the day on this phone, or this person may not change it. */
    val failed: Boolean = false,
    val draft: WeatherDraft = WeatherDraft(),
    val initial: WeatherDraft = WeatherDraft(),
    /** The saved weather was typed by hand, so "Look it up again" is offered. */
    val handEntered: Boolean = false,
    /** Errors show once Save has been pressed or the field has been typed in (rule 21). */
    val showErrors: Boolean = false,
    val working: Boolean = false,
    val done: Boolean = false,
) {
    val dirty: Boolean get() = draft != initial
    val check: WeatherCheck get() = LogWeatherForm.check(draft)
}

sealed interface LogWeatherEvent {
    data class Conditions(val key: String) : LogWeatherEvent
    data class High(val text: String) : LogWeatherEvent
    data class Low(val text: String) : LogWeatherEvent
    data class Precip(val text: String) : LogWeatherEvent
    data object Save : LogWeatherEvent
    data object LookUpAgain : LogWeatherEvent
    data object Retry : LogWeatherEvent
    /** Close (X); the route pops. */
    data object Close : LogWeatherEvent
}
