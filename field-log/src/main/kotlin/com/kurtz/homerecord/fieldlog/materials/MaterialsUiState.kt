package com.kurtz.homerecord.fieldlog.materials

import com.kurtz.homerecord.core.material.JobMaterialGroup
import com.kurtz.homerecord.core.material.MaterialCategory
import kotlinx.serialization.Serializable

/** FL-Materials list (AC-F44): receipts by job with a total per job and for all jobs. */
data class MaterialsUiState(
    val loading: Boolean = true,
    val groups: List<JobMaterialGroup> = emptyList(),
    val exportFailed: Boolean = false,
) {
    val totalCents: Long get() = groups.sumOf { it.totalCents }
}

sealed interface MaterialsEvent {
    data object Add : MaterialsEvent
    data class Open(val id: String) : MaterialsEvent
    data object Export : MaterialsEvent
    data object DismissExportFailed : MaterialsEvent
}

data class JobOption(val id: String, val name: String)

/** The form's typing, kept in SavedStateHandle so a receipt survives process death. */
@Serializable
data class MaterialFormDraft(
    val jobId: String? = null,
    val vendor: String = "",
    val amount: String = "",
    val category: MaterialCategory = MaterialCategory.MATERIALS,
    /** ISO yyyy-MM-dd. */
    val date: String = "",
    val note: String = "",
)

data class MaterialFormUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val isNew: Boolean = true,
    val draft: MaterialFormDraft = MaterialFormDraft(),
    val initial: MaterialFormDraft = MaterialFormDraft(),
    val jobs: List<JobOption> = emptyList(),
    /** Set after a Save with a missing job or amount, so the errors only show once the person tried. */
    val showErrors: Boolean = false,
    val pickingJob: Boolean = false,
    val confirmDelete: Boolean = false,
    val saveFailed: Boolean = false,
    /** Saved or deleted: the route closes the form. */
    val done: Boolean = false,
) {
    val dirty: Boolean get() = draft != initial
    val jobName: String? get() = jobs.firstOrNull { it.id == draft.jobId }?.name
    val jobMissing: Boolean get() = jobName == null
    val amountCents: Long? get() = com.kurtz.homerecord.core.units.Money.parseCents(draft.amount)?.takeIf { it > 0 }
    val amountMissing: Boolean get() = amountCents == null
}

sealed interface MaterialFormEvent {
    data object PickJob : MaterialFormEvent
    data object DismissJob : MaterialFormEvent
    data class Job(val id: String) : MaterialFormEvent
    data class Vendor(val text: String) : MaterialFormEvent
    data class Amount(val text: String) : MaterialFormEvent
    data class Category(val category: MaterialCategory) : MaterialFormEvent
    data class Date(val iso: String) : MaterialFormEvent
    data class Note(val text: String) : MaterialFormEvent
    data object Save : MaterialFormEvent
    data object AskDelete : MaterialFormEvent
    data object DismissDelete : MaterialFormEvent
    data object Delete : MaterialFormEvent
    data object Retry : MaterialFormEvent
}
