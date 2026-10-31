package com.kurtz.homerecord.fieldlog.today

/**
 * What Today's camera button does (SPEC FL-Today, AC-F24): [target] is the job the camera opens on straight away;
 * when there is none, [options] are the jobs to choose from, mine today first. No options means no button.
 */
data class TodayCaptureChoice(val target: String? = null, val options: List<TodayJob> = emptyList()) {
    val available: Boolean get() = target != null || options.isNotEmpty()
}

/** One of the company's newest job photos on Today; [imagePath] is "" while it is still on another phone. */
data class TodayPhoto(val id: String, val jobId: String, val imagePath: String, val jobName: String?)

/** Picks the job a photo taken from Today belongs to, so the shot is tagged without a question when the answer is plain. */
object TodayCapture {
    /**
     * The job I am clocked in on wins; then my only job today; then the company's only live job. Otherwise the
     * picker lists my jobs today first and the other live jobs after, in the order given.
     */
    fun choose(clockJobId: String?, myJobs: List<TodayJob>, liveJobs: List<TodayJob>): TodayCaptureChoice {
        if (liveJobs.isEmpty()) return TodayCaptureChoice()
        val live = liveJobs.map { it.id }.toSet()
        val target = when {
            clockJobId != null && clockJobId in live -> clockJobId
            myJobs.size == 1 && myJobs[0].id in live -> myJobs[0].id
            liveJobs.size == 1 -> liveJobs[0].id
            else -> null
        }
        if (target != null) return TodayCaptureChoice(target = target)
        val mine = myJobs.filter { it.id in live }
        val mineIds = mine.map { it.id }.toSet()
        return TodayCaptureChoice(options = mine + liveJobs.filter { it.id !in mineIds })
    }
}
