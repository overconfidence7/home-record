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
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOf
import com.kurtz.homerecord.core.share.ShareLinkKind
import com.kurtz.homerecord.core.share.ShareTarget
import com.kurtz.homerecord.fieldlog.share.ShareLinkUi
import com.kurtz.homerecord.fieldlog.share.ShareLinkController
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class LogDeps(
    val jobId: String,
    val logs: DailyLogRepository,
    val photos: PhotoDao,
    val gates: Flow<RoleGates>,
    val today: () -> LocalDate = LocalDate::now,
    /** Emits when the calendar day may have turned ([com.kurtz.homerecord.core.log.Midnight.ticks]); empty in previews and tests. */
    val dayTurns: Flow<Unit> = emptyFlow(),
    /** The day to open on the first time (a delay tapped in Reports); never past today. Null opens today. */
    val initialDay: LocalDate? = null,
    /** Makes and hands off the PDF; null leaves Send out (previews). */
    val sender: LogSender? = null,
    /** Share links for a sent log (AC-B35); null hides Share link. */
    val links: com.kurtz.homerecord.fieldlog.share.JobLinks? = null,
)

/** The open sheet and its transient flags, bundled so the state combine stays at five flows. */
private data class Editing(
    val editor: LogEditor?, val hoursInvalid: Boolean, val sending: Boolean, val sendFailed: Boolean,
    val link: com.kurtz.homerecord.fieldlog.share.ShareLinkUi,
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
    /** Moves on at midnight while the tab is open, and when the screen comes back to the front (R09d). */
    private val today = MutableStateFlow(d.today())
    private val firstDay = (d.initialDay?.let { minOf(it, today.value) } ?: today.value).toString()
    private val day = saved.getStateFlow(DAY, firstDay).map(LocalDate::parse)
    private val editor = saved.getStateFlow(EDITOR, "").map(::decode)
    private val hoursInvalid = MutableStateFlow(false)
    private val sending = MutableStateFlow(false)
    private val sendFailed = MutableStateFlow(false)
    private val canEdit = d.gates.map { it.leads(d.jobId) }.distinctUntilChanged()
    private val assembly = MutableStateFlow<Assembly>(Assembly.Idle)
    private val retries = MutableStateFlow(0)
    private val log = day.flatMapLatest { d.logs.observe(d.jobId, it).map { log -> it to log } }
    private var current: DailyLog? = null

    /** A sent log's share link (AC-B35): Owner and Office, for the shown day once its log has gone out. */
    private val link = d.links?.let { links ->
        val target = combine(day, log) { dd, (logDay, l) ->
            l?.takeIf { logDay == dd && it.sentAt != null }?.let { ShareTarget(ShareLinkKind.LOG, d.jobId, it.id) }
        }
        ShareLinkController(
            viewModelScope, links, d.gates.map { it.canEditJobs }, target,
            detail = { state.value.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) },
        )
    }

    private val thumbs = d.photos.observeForJob(d.jobId).map { rows -> rows.associate { it.id to (it.thumbPath ?: it.path) } }

    val state: StateFlow<LogUiState> = combine(
        combine(day, log, today) { dd, l, t -> Triple(dd, l, t) },
        canEdit,
        assembly,
        thumbs,
        combine(editor, hoursInvalid, sending, sendFailed, link?.state ?: flowOf(ShareLinkUi())) { e, h, s, f, l -> Editing(e, h, s, f, l) },
    ) { (dd, dayLog, now), edit, asm, photos, (open, badHours, isSending, failedSend, linkUi) ->
        val (logDay, saved) = dayLog
        val fresh = logDay == dd
        val savedLog = saved.takeIf { fresh }
        current = savedLog
        val running = edit && (asm.day != dd || asm is Assembly.Running)
        val base = LogUiState(
            day = dd, today = now, canEdit = edit, editor = open, hoursInvalid = badHours, sending = isSending, sendFailed = failedSend,
            link = linkUi,
        )
        when {
            edit && asm is Assembly.Failed && asm.day == dd && savedLog == null -> base.copy(loading = false, failed = true)
            !fresh || (running && savedLog == null) -> base
            savedLog == null -> base.copy(loading = false)
            else -> fill(base, savedLog.sections, savedLog, photos, if (edit && asm is Assembly.Done && asm.day == dd) asm.weather else null)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LogUiState(day = LocalDate.parse(saved.get<String>(DAY) ?: firstDay), today = today.value))

    init {
        viewModelScope.launch { d.dayTurns.collect { checkToday() } }
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
            sentTo = log.recipients,
            changedSinceSent = log.changedSinceSent,
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
            LogEvent.CheckToday -> checkToday()
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
            is LogEvent.AskRemoveDelay -> if (st.canEdit && st.delays.any { it.key == event.key }) open(LogEditor.RemoveDelay(event.key))
            is LogEvent.AskDrop -> if (st.canEdit) dropLabel(st, event.kind, event.id)?.let { open(LogEditor.Drop(event.kind, event.id, it.ifEmpty { null })) }
            is LogEvent.PutBack -> putBack(st, event.kind)
            LogEvent.EditWeather, LogEvent.Preview -> Unit // the route opens the weather form and the PDF viewer
            LogEvent.OpenSend -> openSend(st, confirmed = false)
            LogEvent.SendAnyway -> if (openEditor() == LogEditor.SendEmpty) openSend(st, confirmed = true)
            LogEvent.SendEmail -> send(email = true)
            LogEvent.SendShare -> send(email = false)
            is LogEvent.Link -> link?.onEvent(event.event)
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
                { s -> s.copy(delays = s.delays + LogDelay(reason, e.note.trim(), id = UUID.randomUUID().toString())) }
            }
            // by key, not position: a delay added or removed on another phone meanwhile must not shift the target (R09d)
            is LogEditor.RemoveDelay -> { s -> LogEdits.removeDelay(s, e.key) }
            is LogEditor.Drop -> { s -> s.drop(e.kind, e.id) }
            LogEditor.PickDay, is LogEditor.Send, LogEditor.SendEmpty -> return
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

    /** Opens the Send sheet; a day with nothing logged asks first ([confirmed] once the person said Send anyway). */
    private fun openSend(st: LogUiState, confirmed: Boolean) {
        val sender = d.sender ?: return
        if (!st.canSend) return
        sendFailed.value = false
        if (!confirmed && st.nothingLogged) {
            open(LogEditor.SendEmpty)
            return
        }
        viewModelScope.launch { open(LogEditor.Send(sender.recipients(d.jobId))) }
    }

    /**
     * Makes the PDF from the saved log and hands it to an email app or the share sheet, then marks the log Sent with the
     * time, who it went to (none for the share sheet) and the PDF (AC-F33). The sheet stays open with an error when the
     * PDF could not be made or nothing took it. Sending again replaces the last send.
     */
    private fun send(email: Boolean) {
        val sender = d.sender ?: return
        val log = current ?: return
        if (openEditor() !is LogEditor.Send || sending.value || !state.value.canSend) return
        sending.value = true
        sendFailed.value = false
        viewModelScope.launch {
            val pdf = sender.make(log)
            val byEmail = email && pdf != null && pdf.recipients.isNotEmpty()
            if (pdf != null && sender.open(pdf, byEmail)) {
                // Close first: the saved log's Sent emission must never show with the sheet still open, and a late
                // close must not shut a sheet opened again after it (Send again).
                close()
                d.logs.markSent(log.id, if (byEmail) pdf.recipients else emptyList(), pdf.file.path)
            } else {
                sendFailed.value = true
            }
            sending.value = false
        }
    }

    private fun goTo(target: LocalDate) {
        saved[DAY] = minOf(target, today.value).toString()
        close()
    }

    /**
     * Picks up a new calendar day. Someone looking at today follows it to the new today, unless a sheet is open (their
     * typing belongs to the day it was opened on); a past day stays put and Next now reaches the new day.
     */
    private fun checkToday() {
        val was = today.value
        val now = d.today()
        if (now <= was) return
        // the day first: a moment with the new today and the old day would flash "Next" on for a frame
        if (saved.get<String>(DAY) == was.toString() && openEditor() == null) saved[DAY] = now.toString()
        today.value = now
    }

    private fun open(e: LogEditor) {
        saved[EDITOR] = json.encodeToString(LogEditor.serializer(), e)
    }

    private fun close() {
        hoursInvalid.value = false
        sendFailed.value = false
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

    /** Takes the first delay with [key] off the day; nothing changes when it is already gone. */
    fun removeDelay(s: DailyLogSections, key: String): DailyLogSections {
        val at = s.delays.indexOfFirst { it.key == key }
        return if (at < 0) s else s.copy(delays = s.delays.filterIndexed { i, _ -> i != at })
    }

    fun text(s: DailyLogSections, section: LogTextSection, text: String): DailyLogSections = when (section) {
        // one entry per paragraph, as the sheet shows them
        LogTextSection.WORK_DONE -> {
            val lines = text.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotEmpty() }
            // Save with nothing changed keeps the section following notes and memos (R09)
            if (lines == s.workDone.value) s else s.copy(workDone = LogSection(lines, edited = true))
        }
        LogTextSection.VISITORS -> s.copy(visitors = text.trim())
        LogTextSection.SAFETY -> s.copy(safety = text.trim())
        LogTextSection.CLIENT_NOTES -> s.copy(clientNotes = text.trim())
    }

    /**
     * Sets one person's time; zero takes them off the day. The crew section is then fixed as typed, so nobody stays
     * marked "on site now" on a list that no longer follows the clock.
     */
    fun hours(s: DailyLogSections, memberId: String, minutes: Int): DailyLogSections {
        // the same time saved again, or zero for someone not on the list, is no edit: the crew keeps following the clock (R09)
        val current = s.crew.value.firstOrNull { it.memberId == memberId }
        if (if (current == null) minutes == 0 else current.minutes == minutes) return s
        return hoursEdited(s, memberId, minutes)
    }

    private fun hoursEdited(s: DailyLogSections, memberId: String, minutes: Int): DailyLogSections = s.copy(
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
