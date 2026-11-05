package com.kurtz.homerecord.core.bundle

import com.kurtz.homerecord.core.data.ImportLedgerEntity

/** The ledger rows of a home as the planner's base: type → id → `updatedAt` as last imported (AC-C47). */
fun List<ImportLedgerEntity>.toImportBase(): Map<String, Map<String, Long>> =
    groupBy { it.type }.mapValues { (_, rows) -> rows.associate { it.recordId to it.updatedAt } }

/** What to store in the ledger once [this] plan is applied to [homeId], so importing the same bundle again is a no-op. */
fun ImportPlan.ledgerRows(homeId: String): List<ImportLedgerEntity> =
    ledger().flatMap { (type, ids) -> ids.map { (id, updatedAt) -> ImportLedgerEntity(type, id, homeId, updatedAt) } }
