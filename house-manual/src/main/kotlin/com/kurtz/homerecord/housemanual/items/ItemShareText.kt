package com.kurtz.homerecord.housemanual.items

import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.items.ItemDetail
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The plain text the item page's Share action sends (SPEC HM-Item): name; type and room; make, model and serial when
 * known; the warranty line as the page shows it. [strings] is Resources::getString with format args.
 */
object ItemShareText {
    fun build(detail: ItemDetail, strings: (Int, Array<out Any>) -> String, locale: Locale = Locale.getDefault()): String {
        val i = detail.item
        val date = { iso: String ->
            runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }.getOrDefault(iso)
        }
        fun field(label: Int, value: String?) = value?.trim()?.takeIf { it.isNotEmpty() }?.let { strings(R.string.item_share_field, arrayOf(strings(label, emptyArray()), it)) }
        val warranty = when (val s = detail.status) {
            is WarrantyStatus.Unknown -> strings(com.kurtz.homerecord.coreui.R.string.warranty_unknown, emptyArray())
            is WarrantyStatus.Active -> strings(com.kurtz.homerecord.coreui.R.string.warranty_active, arrayOf(date(s.endsOn)))
            is WarrantyStatus.Expiring -> strings(com.kurtz.homerecord.coreui.R.string.warranty_expiring, arrayOf(date(s.endsOn)))
            is WarrantyStatus.Expired -> strings(com.kurtz.homerecord.coreui.R.string.warranty_expired, arrayOf(date(s.endsOn)))
        }
        return listOfNotNull(
            i.name,
            listOf(detail.subtypeLabel, detail.roomName).filter(String::isNotBlank).joinToString(", ").ifBlank { null },
            field(R.string.item_detail_make, i.make),
            field(R.string.item_detail_model, i.model),
            field(R.string.item_detail_serial, i.serial),
            warranty,
        ).joinToString("\n")
    }
}
