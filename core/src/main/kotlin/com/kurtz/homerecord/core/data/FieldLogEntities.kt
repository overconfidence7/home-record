package com.kurtz.homerecord.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "company")
data class CompanyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val trade: String? = null,
    val logoPath: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    /** otRule, breakRule, requireGps, punchDistanceFt, photoOnPunch. */
    val settingsJson: String = "{}",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.FIELD_LOG,
)

@Entity(tableName = "member", indices = [Index("companyId")])
data class MemberEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val userId: String? = null,
    val name: String,
    val role: String = "WORKER",
    val rateCents: Long? = null,
    val fcmToken: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.FIELD_LOG,
)

@Entity(tableName = "client", indices = [Index("companyId")])
data class ClientEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.FIELD_LOG,
)

@Entity(tableName = "job", indices = [Index("companyId"), Index("homeId")])
data class JobEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val homeId: String? = null,
    val clientId: String? = null,
    val name: String,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val status: String = "ACTIVE",
    val phasesJson: String = "[]",
    val startDate: String? = null,
    val endDate: String? = null,
    val budgetHours: Int? = null,
    val officeEmailsJson: String = "[]",
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.FIELD_LOG,
)

@Entity(tableName = "job_crew", indices = [Index("jobId"), Index("memberId")])
data class JobCrewEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val memberId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.FIELD_LOG,
)

@Entity(tableName = "punch", indices = [Index("companyId"), Index("memberId"), Index("jobId")])
data class PunchEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val memberId: String,
    val jobId: String? = null,
    val kind: String,
    val at: Long,
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracyM: Float? = null,
    val farFromJob: Boolean = false,
    val reason: String? = null,
    val clockedInBy: String? = null,
    val note: String? = null,
    val editsJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.FIELD_LOG,
)

@Entity(tableName = "shift", indices = [Index("memberId")])
data class ShiftEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val jobId: String? = null,
    val startAt: Long,
    val endAt: Long?,
    val breakMinutes: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.FIELD_LOG,
)

@Entity(tableName = "timesheet_approval", indices = [Index("memberId")])
data class TimesheetApprovalEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val weekStart: String,
    val approvedBy: String,
    val at: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.FIELD_LOG,
)

@Entity(tableName = "job_file", indices = [Index("jobId")])
data class JobFileEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val folder: String = "",
    val name: String,
    val path: String? = null,
    val r2Key: String? = null,
    val mime: String,
    val size: Long = 0,
    val version: Int = 1,
    val parentFileId: String? = null,
    val author: String? = null,
    val offline: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.FIELD_LOG,
)

@Entity(tableName = "note", indices = [Index("jobId")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val day: String,
    val text: String = "",
    val voicePath: String? = null,
    val transcript: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.FIELD_LOG,
)

@Entity(tableName = "daily_log", indices = [Index("jobId", "day")])
data class DailyLogEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val day: String,
    val sectionsJson: String = "{}",
    val weatherJson: String? = null,
    val status: String = "DRAFT",
    val sentAt: Long? = null,
    val recipientsJson: String = "[]",
    val pdfPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.FIELD_LOG,
)

@Entity(tableName = "task", indices = [Index("jobId")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val title: String,
    val assigneeId: String? = null,
    val due: String? = null,
    val roomId: String? = null,
    val photoId: String? = null,
    val note: String? = null,
    val done: Boolean = false,
    val punch: Boolean = false,
    val planPinJson: String? = null,
    val fileId: String? = null,
    val page: Int? = null,
    val x: Float? = null,
    val y: Float? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.FIELD_LOG,
)

@Entity(tableName = "change_order", indices = [Index("jobId")])
data class ChangeOrderEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val title: String,
    val description: String? = null,
    val linesJson: String = "[]",
    val totalCents: Long = 0,
    val clientName: String? = null,
    val status: String = "DRAFT",
    val signaturePath: String? = null,
    val signedAt: Long? = null,
    val signedBy: String? = null,
    val pdfPath: String? = null,
    val shareToken: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.FIELD_LOG,
)

@Entity(tableName = "assignment", indices = [Index("jobId"), Index("memberId")])
data class AssignmentEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val memberId: String,
    val date: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.FIELD_LOG,
)

@Entity(tableName = "material", indices = [Index("jobId")])
data class MaterialEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val vendor: String? = null,
    val amountCents: Long = 0,
    val category: String? = null,
    val date: String,
    val receiptPath: String? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.FIELD_LOG,
)

@Entity(tableName = "share_link", indices = [Index("jobId")])
data class ShareLinkEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val jobId: String,
    val token: String,
    val revoked: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.FIELD_LOG,
)

@Entity(tableName = "outbox", indices = [Index("sent")])
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val tenantKind: String,
    val tenantId: String,
    val entity: String,
    val entityId: String,
    val op: String,
    val payload: String,
    val updatedAt: Long,
    val deviceId: String,
    val sent: Boolean = false,
    val serverSeq: Long? = null,
)

/** How far this device has pulled one tenant; `serverSeq` counts per tenant on the server (AC-B20). */
@Entity(tableName = "sync_cursor", primaryKeys = ["tenantKind", "tenantId"])
data class SyncCursorEntity(
    val tenantKind: String,
    val tenantId: String,
    val lastServerSeq: Long = 0,
)

/**
 * The row as the server last confirmed it; a push carries only the fields that differ from it (field-level merge).
 * [tenant] (`kind:id`) lets a tenant's rows be dropped when the membership ends; empty for rows from before v2.
 */
@Entity(tableName = "sync_base", indices = [Index("tenant")])
data class SyncBaseEntity(
    @PrimaryKey val key: String,
    val rowJson: String,
    @ColumnInfo(defaultValue = "") val tenant: String = "",
)

/** One file waiting to go to R2 (AC-F54); survives process death, one row per entity/filename so a retry never uploads twice. */
@Entity(tableName = "media_upload", indices = [Index("state"), Index(value = ["entity", "entityId", "filename"], unique = true)])
data class MediaUploadEntity(
    @PrimaryKey val id: String,
    val tenantKind: String,
    val tenantId: String,
    val entity: String,
    val entityId: String,
    val filename: String,
    val path: String,
    val mime: String,
    /** queued, uploading, done, failed. */
    val state: String = "queued",
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val key: String? = null,
    val putUrl: String? = null,
    val putExpiresAt: Long = 0,
    val createdAt: Long,
)
