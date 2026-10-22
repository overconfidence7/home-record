package com.kurtz.homerecord.core.model

/** The fields of a job the jobs list sorts and groups on. Dates are ISO `yyyy-MM-dd`. */
data class JobSummary(
    val id: String,
    val name: String,
    val status: JobStatus,
    val startDate: String? = null,
    val endDate: String? = null,
    /** Scheduled days (assignment dates) for the job, ISO, any order. */
    val scheduledDays: List<String> = emptyList(),
)

enum class JobStatus { ACTIVE, UPCOMING, DONE, ARCHIVED }

data class JobGroups(
    val active: List<JobSummary>,
    val upcoming: List<JobSummary>,
    val done: List<JobSummary>,
    val archived: List<JobSummary>,
)

/** Groups and orders the jobs list (AC-F6). */
object JobOrdering {
    fun group(jobs: List<JobSummary>, today: String, showArchived: Boolean = false): JobGroups {
        val byName = compareBy<JobSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        // Active: next scheduled day (today or later) first; jobs with none go last, then by name.
        val active = jobs.filter { it.status == JobStatus.ACTIVE }
            .sortedWith(
                compareBy<JobSummary, String?>(nullsLast()) { nextDay(it, today) }.then(byName),
            )
        val upcoming = jobs.filter { it.status == JobStatus.UPCOMING }
            .sortedWith(compareBy<JobSummary, String?>(nullsLast()) { it.startDate }.then(byName))
        val done = jobs.filter { it.status == JobStatus.DONE }
            .sortedWith(
                compareByDescending<JobSummary, String?>(nullsFirstLast()) { it.endDate }.then(byName),
            )
        val archived = if (showArchived) {
            jobs.filter { it.status == JobStatus.ARCHIVED }.sortedWith(byName)
        } else {
            emptyList()
        }
        return JobGroups(active, upcoming, done, archived)
    }

    fun nextDay(job: JobSummary, today: String): String? =
        job.scheduledDays.filter { it >= today }.minOrNull()

    // A missing end date sorts after every dated job in the Done list (newest first, undated last).
    private fun nullsFirstLast(): Comparator<String?> = Comparator { a, b ->
        when {
            a == null && b == null -> 0
            a == null -> -1 // reversed by compareByDescending, so null ends up last
            b == null -> 1
            else -> a.compareTo(b)
        }
    }

    private fun nullsLast(): Comparator<String?> = Comparator { a, b ->
        when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            else -> a.compareTo(b)
        }
    }
}
