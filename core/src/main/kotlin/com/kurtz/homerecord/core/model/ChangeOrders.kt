package com.kurtz.homerecord.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Money on change orders in whole cents (AC-F37). Quantities are kept in thousandths so "2.5 hours" or "0.125 sq ft"
 * stay exact; a line's amount rounds half up to the cent once, and the total is the sum of the rounded lines, so the
 * total always equals what the lines show. Typed prices are parsed by [com.kurtz.homerecord.core.units.Money].
 */
object MoneyMath {
    /** Largest quantity accepted: 1,000,000. */
    const val MAX_QTY_MILLI: Long = 1_000_000_000L

    /** One line: [qtyMilli] (quantity × 1000) times [unitCents], rounded half up to the cent. */
    fun lineCents(qtyMilli: Long, unitCents: Long): Long =
        BigDecimal.valueOf(qtyMilli).multiply(BigDecimal.valueOf(unitCents))
            .divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP).longValueExact()

    /** The order's total: the sum of its rounded lines. */
    fun totalCents(lines: List<ChangeOrderLineItem>): Long = lines.sumOf { lineCents(it.qtyMilli, it.unitCents) }

    /** A typed quantity ("3", "2.5", "0.125") in thousandths; null when blank, zero, negative, over three decimals or too big. */
    fun parseQuantity(text: String): Long? {
        val clean = text.trim().replace(",", "")
        if (clean.isEmpty() || !QUANTITY.matches(clean)) return null
        val milli = BigDecimal(clean).movePointRight(3)
        if (milli.stripTrailingZeros().scale() > 0) return null
        val value = milli.setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        return value.takeIf { it in 1..MAX_QTY_MILLI }
    }

    /** Thousandths back to the shortest plain number: 2500 → "2.5", 3000 → "3". */
    fun formatQuantity(qtyMilli: Long): String = BigDecimal.valueOf(qtyMilli, 3).stripTrailingZeros().toPlainString()

    private val QUANTITY = Regex("""\d{1,7}(\.\d{0,3})?|\.\d{1,3}""")
}

/** One priced line of a change order: what, how many (thousandths) and the unit price in cents. */
@Serializable
data class ChangeOrderLineItem(
    val id: String,
    val description: String,
    val qtyMilli: Long = 1000,
    val unitCents: Long = 0,
) {
    val amountCents: Long get() = MoneyMath.lineCents(qtyMilli, unitCents)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val LIST = ListSerializer(serializer())

        /** The stored `linesJson`; an unreadable value reads as no lines rather than failing the screen. */
        fun decode(text: String?): List<ChangeOrderLineItem> =
            text?.let { runCatching { json.decodeFromString(LIST, it) }.getOrNull() } ?: emptyList()

        fun encode(lines: List<ChangeOrderLineItem>): String = json.encodeToString(LIST, lines)
    }
}

enum class ChangeOrderStatus {
    DRAFT, SIGNED, DECLINED;

    companion object {
        fun of(text: String?): ChangeOrderStatus = entries.firstOrNull { it.name == text } ?: DRAFT
    }
}

/** A change order on a job (AC-F37); Signed ones are read only (AC-F38). */
data class JobChangeOrder(
    val id: String,
    val jobId: String,
    val title: String,
    val description: String?,
    val lines: List<ChangeOrderLineItem>,
    val totalCents: Long,
    val clientName: String?,
    val status: ChangeOrderStatus,
    val signedAt: Long?,
    val signedBy: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** The job photos on it, in the order they were added (T056b). */
    val photoIds: List<String> = emptyList(),
    /** The signature image (PNG in filesDir/media) once signed on the phone (AC-F38). */
    val signaturePath: String? = null,
)

/** `change_order.photoIdsJson`: a JSON array of photo ids; an unreadable value reads as no photos. */
object ChangeOrderPhotos {
    private val json = Json { ignoreUnknownKeys = true }
    private val LIST = ListSerializer(String.serializer())

    fun decode(text: String?): List<String> =
        text?.let { runCatching { json.decodeFromString(LIST, it) }.getOrNull() }.orEmpty()

    fun encode(ids: List<String>): String = json.encodeToString(LIST, ids)
}
