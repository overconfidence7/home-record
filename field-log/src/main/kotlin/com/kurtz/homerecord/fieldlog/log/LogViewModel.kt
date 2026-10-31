package com.kurtz.homerecord.fieldlog.log

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.company.RoleGates
import com.kurtz.homerecord.core.data.PhotoDao
import com.kurtz.homerecord.core.log.DailyLog
import com.kurtz.homerecord.core.log.DailyLogRepository
import com.kurtz.homerecord.core.log.WeatherStatus
import com.kurtz.homerecord.core.model.DailyLogSections
import com.kurtz.homerecord.core.model.LogDelay
import com.kurtz.homerecord.core.model.LogLineKind
import com.kurtz.homerecord.core.model.LogSection
import com.kurtz.homerecord.core.model.LogWeather
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class LogDeps(
    val jobId: String,
    val logs: DailyLogRepository,
    val photos: PhotoDao,
    val gates: Flow<RoleGates>,
    val today: () -> LocalDate = LocalDate::now,
    /** The day to open on the first time (a delay tapped in Reports); never past today. Null opens today. */
    val initialDay: LocalDate? = null,
)

/** How far the day's draft has got: [Running] while the records are read and the weather looked up. */
private sealed interface Assembly {
    val day: LocalDate?

    data class ReadOnly(override val day: LocalDate) : Assembly
    data class Running(override val day: LocalDate) : Assembly
    data class Done(override val day: LocalDate, val weather: WeatherStatus) : Assembly
    data class Failed(override val day: LocalDate) : Assembly
    data object Idle : Assembly { override val day: LocalDate? = null }
}

/**
 * The job's Log tab (SPEC FL-Log). Opening a day builds its draft from the day's clock-ins, notes, photos, tasks,
 * change orders, materials and weather and saves it (AC-F30, AC-F31); every hand edit is marked so re-opening the day
 * never undoes it. Crew and Sub, and a Lead on someone else's job, only read the saved log (AC-F3): their phone would
 * not be allowed to sync a draft, so it never builds one. The day and the open sheet survive process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModel(private val d: LogDeps, private val saved: SavedStateHandle) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val today = d.today()
    private val firstDay = (d.initialDay?.let { minOf(it, today) } ?: today).toString()
    private val day = saved.getStateFlow(DAY, firstDay).map(LocalDate::parse)
    private val editor = saved.getStateFlow(EDITOR, "").map(::decode)
    private val hoursInvalid = MutableStateFlow(false)
    private val canEdit = d.gates.map { it.leads(d.jobId) }.distinctUntilChanged()
    private val assembly = MutableStateFlow<Assembly>(Assembly.Idle)
    private val retries = MutableStateFlow(0)
    private val log = day.flatMapLatest { d.logs.observe(d.jobId, it).map { log -> it to log } }
    private var current: DailyLog? = null

    private val thumbs = d.photos.observeForJob(d.jobId).map { rows -> rows.associate { it.id to (it.thumbPath ?: it.path) } }

    val state: StateFlow<LogUiState> = combine(
        combine(day, log) { dd, l -> dd to l },
        canEdit,
        assembly,
        thumbs,
        combine(editor, hoursInvalid) { e, h -> e to h },
    ) { (dd, dayLog), edit, asm, photos, (open, badHours) ->
        val (logDay, saved) = dayLog
        val fresh = logDay == dd
        val savedLog = saved.takeIf { fresh }
        current = savedLog
        val running = edit && (asm.day != dd || asm is Assembly.Running)
        val base = LogUiState(day = dd, today = today, canEdit = edit, editor = open, hoursInvalid = badHours)
        when {
            edit && asm is Assembly.Failed && asm.day == dd && savedLog == null -> base.copy(loading = false, failed = true)
            !fresh || (running && savedLog == null) -> base
            savedLog == null -> base.copy(loading = false)
            else -> fill(base, savedLog.sections, savedLog, photos, if (edit && asm is Assembly.Done && asm.day == dd) asm.weather else null)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LogUiState(day = LocalDate.parse(saved.get<String>(DAY) ?: firstDay), today = today))

    init {
        viewModelScope.launch {
            combine(day, canEdit, retries) { dd, edit, _ -> dd to edit }.collectLatest { (dd, edit) ->
                if (!edit) {
                    assembly.value = Assembly.ReadOnly(dd)
                    return@collectLatest
                }
                assembly.value = Assembly.Running(dd)
                val result = d.logs.assemble(d.jobId, dd)
                assembly.value = if (result == null) Assembly.Failed(dd) else Assembly.Done(dd, result.weather)
            }
        }
    }

    private fun fill(base: LogUiState, s: DailyLogSections, log: DailyLog, photos: Map<String, String>, weather: WeatherStatus?): LogUiState {
        val money = NumberFormat.getCurrencyInstance(Locale.US)
        return base.copy(
            loading = false,
            exists = true,
            sentAt = log.sentAt,
            crew = s.crew.value.map { LogCrewRow(it.memberId, it.name, it.minutes, it.onSiteNow) },
            workDone = s.workDone.value,
            photos = s.photoIds.value.map { LogPhotoThumb(it, photos[it].orEmpty()) },
            tasksDone = s.tasksDone.value.map { LogLine(it.taskId, it.title) },
            changeOrders = s.changeOrders.value.map { LogLine(it.changeOrderId, it.title, money.format(it.totalCents / 100.0)) },
            materials = s.materials.value.map { m ->
                LogLine(
                    m.materialId,
                    listOfNotNull(m.vendor, m.category).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { money.format(m.amountCents / 100.0) },
                    listOfNotNull(money.format(m.amountCents / 100.0).takeIf { !m.vendor.isNullOrBlank() || !m.category.isNullOrBlank() }, m.note?.takeIf { it.isNotBlank() })
                        .joinToString(", ").ifEmpty { null },
                )
            },
            weather = s.weather.value,
            weatherStatus = weather ?: if (s.weather.value != null) WeatherStatus.Shown else null,
            weatherEdited = s.weather.edited,
            dropped = s.dropped.mapValues { it.value.size }.filterValues { it > 0 },
            delays = s.delays,
            visitors = s.visitors,
            safety = s.safety,
            clientNotes = s.clientNotes,
        )
    }

    private fun decode(text: String?): LogEditor? =
        if (text.isNullOrEmpty()) null else runCatching { json.decodeFromString(LogEditor.serializer(), text) }.getOrNull()

    /** The open sheet as last saved, read directly: [state] can trail a quick run of events by a frame. */
    private fun openEditor(): LogEditor? = decode(saved.get<String>(EDITOR))

    fun onEvent(event: LogEvent) {
        val st = state.value
        when (event) {
            LogEvent.PreviousDay -> goTo(st.day.minusDays(1))
            LogEvent.NextDay -> if (st.canGoForward) goTo(st.day.plusDays(1))
            is LogEvent.PickDay -> goTo(event.day)
            LogEvent.OpenDayPicker -> open(LogEditor.PickDay)
            LogEvent.Retry -> retries.value++
            is LogEvent.EditText -> if (st.canEdit && st.exists) open(
                LogEditor.Text(
                    event.section,
                    when (event.section) {
                        LogTextSection.WORK_DONE -> st.workDone.joinToString("\n\n")
                        LogTextSection.VISITORS -> st.visitors
                        LogTextSection.SAFETY -> st.safety
                        LogTextSection.CLIENT_NOTES -> st.clientNotes
                    },
                ),
            )
            is LogEvent.EditHours -> st.crew.firstOrNull { it.memberId == event.memberId }?.takeIf { st.canEdit }?.let {
                open(LogEditor.Hours(it.memberId, it.name, LogHours.text(it.minutes)))
            }
            LogEvent.AddDelay -> if (st.canEdit && st.exists) open(LogEditor.Delay())
            is LogEvent.AskRemoveDelay -> if (st.canEdit && event.index in st.delays.indices) open(LogEditor.RemoveDelay(event.index))
            is LogEvent.AskDrop -> if (st.canEdit) dropLabel(st, event.kind, event.id)?.let { open(LogEditor.Drop(event.kind, event.id, it.ifEmpty { null })) }
            is LogEvent.PutBack -> putBack(st, event.kind)
            LogEvent.EditWeather -> Unit // the route opens the weather form
            is LogEvent.EditorText -> when (val e = openEditor()) {
                is LogEditor.Text -> open(e.copy(text = event.text))
                is LogEditor.Hours -> {
                    hoursInvalid.value = false
                    open(e.copy(text = event.text))
                }
                is LogEditor.Delay -> open(e.copy(note = event.text))
                else -> Unit
            }
            is LogEvent.EditorReason -> (openEditor() as? LogEditor.Delay)?.let { open(it.copy(reason = event.reason)) }
            LogEvent.DismissEditor -> close()
            LogEvent.SaveEditor -> save(st)
        }
    }

    private fun save(st: LogUiState) {
        val e = openEditor() ?: return
        val logId = current?.id ?: return
        if (!st.canEdit) return
        val change: ((DailyLogSections) -> DailyLogSections) = when (e) {
            is LogEditor.Text -> { s -> LogEdits.text(s, e.section, e.text) }
            is LogEditor.Hours -> {
                val minutes = LogHours.minutes(e.text)
                if (minutes == null) {
                    hoursInvalid.value = true
                    return
                }
                { s -> LogEdits.hours(s, e.memberId, minutes) }
            }
            is LogEditor.Delay -> {
                val reason = e.reason ?: return
                { s -> s.copy(delays = s.delays + LogDelay(reason, e.note.trim())) }
            }
            is LogEditor.RemoveDelay -> { s -> s.copy(delays = s.delays.filterIndexed { i, _ -> i != e.index }) }
            is LogEditor.Drop -> { s -> s.drop(e.kind, e.id) }
            LogEditor.PickDay -> return
        }
        close()
        viewModelScope.launch { d.logs.update(logId, change) }
    }

    /** The line's name for the confirm dialog ("" for a photo), or null when it is not on the shown day's log. */
    private fun dropLabel(st: LogUiState, kind: LogLineKind, id: String): String? = when (kind) {
        LogLineKind.PHOTO -> st.photos.firstOrNull { it.id == id }?.let { "" }
        LogLineKind.TASK -> st.tasksDone.firstOrNull { it.id == id }?.headline
        LogLineKind.CHANGE_ORDER -> st.changeOrders.firstOrNull { it.id == id }?.headline
        LogLineKind.MATERIAL -> st.materials.firstOrNull { it.id == id }?.headline
    }

    /** Forgets what was left out, then rebuilds the day so those lines come back from the records. */
    private fun putBack(st: LogUiState, kind: LogLineKind) {
        val logId = current?.id ?: return
        if (!st.canEdit || (st.dropped[kind] ?: 0) == 0) return
        viewModelScope.launch {
            d.logs.update(logId) { it.restore(kind) }
            retries.value++
        }
    }

    private fun goTo(target: LocalDate) {
        saved[DAY] = minOf(target, today).toString()
        close()
    }

    private fun open(e: LogEditor) {
        saved[EDITOR] = json.encodeToString(LogEditor.serializer(), e)
    }

    private fun close() {
        hoursInvalid.value = false
        saved[EDITOR] = ""
    }

    private companion object {
        const val DAY = "day"
        const val EDITOR = "editor"
    }
}

/** The hand edits on a day's sections. Each marks the section edited so re-assembling keeps it (AC-F30). */
object LogEdits {
    /** Weather typed by hand: kept as typed, never replaced by a lookup. */
    fun weather(s: DailyLogSections, weather: LogWeather): DailyLogSections = s.copy(weather = LogSection(weather, edited = true))

    /**
     * Back to looking the weather up. The typed weather stays on screen until a lookup answers, and is marked as never
     * fetched so the next assembly asks the backend (AC-F31).
     */
    fun lookUpWeather(s: DailyLogSections): DailyLogSections =
        s.copy(weather = LogSection(s.weather.value?.copy(fetchedAt = 0), edited = false))

    fun text(s: DailyLogSections, section: LogTextSection, text: String): DailyLogSections = when (section) {
        // one entry per paragraph, as the sheet shows them
        LogTextSection.WORK_DONE -> s.copy(
            workDone = LogSection(text.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotEmpty() }, edited = true),
        )
        LogTextSection.VISITORS -> s.copy(visitors = text.trim())
        LogTextSection.SAFETY -> s.copy(safety = text.trim())
        LogTextSection.CLIENT_NOTES -> s.copy(clientNotes = text.trim())
    }

    /**
     * Sets one person's time; zero takes them off the day. The crew section is then fixed as typed, so nobody stays
     * marked "on site now" on a list that no longer follows the clock.
     */
    fun hours(s: DailyLogSections, memberId: String, minutes: Int): DailyLogSections = s.copy(
        crew = LogSection(
            s.crew.value.mapNotNull { line ->
                when {
                    line.memberId != memberId -> line.copy(onSiteNow = false)
                    minutes == 0 -> null
                    else -> line.copy(minutes = minutes, onSiteNow = false)
                }
            },
            edited = true,
        ),
    )
}
