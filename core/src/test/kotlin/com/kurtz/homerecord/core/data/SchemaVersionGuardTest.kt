package com.kurtz.homerecord.core.data

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Testing APKs ship from `build` and install over each other, so a released schema version is frozen (cr:room-schema-v1).
 * Room's KSP step rewrites `schemas/.../<version>.json` on every build; if an entity changes without a version bump,
 * the identity hash of the current version moves and this test fails.
 *
 * To change the schema: bump `version` in [HomeRecordDatabase], add an `AutoMigration` (or a `Migration`) from the
 * previous version, add a case to [HomeRecordMigrationTest], build once, and add the new hash from the new JSON below.
 */
class SchemaVersionGuardTest {
    private val dir = File("schemas/${HomeRecordDatabase::class.java.name}")

    private val schemas: Map<Int, File> by lazy {
        dir.listFiles { f -> f.name.matches(Regex("""\d+\.json""")) }.orEmpty()
            .associateBy { it.nameWithoutExtension.toInt() }
    }

    @Test fun releasedVersionsKeepTheirIdentityHash() {
        for ((version, hash) in RELEASED) {
            val file = schemas[version]
            assertWithMessage("Exported schema $version.json is missing from ${dir.path}; never delete a released schema")
                .that(file).isNotNull()
            assertWithMessage(
                "Schema version $version changed after release. Bump HomeRecordDatabase.version to ${latest() + 1}, " +
                    "add AutoMigration(from = ${latest()}, to = ${latest() + 1}), restore $version.json with git, and record the new hash here",
            ).that(identityHash(file ?: continue)).isEqualTo(hash)
        }
    }

    @Test fun everyExportedVersionIsRecorded() {
        assertWithMessage("Record the identity hash of each new schema version in RELEASED (from its exported JSON)")
            .that(schemas.keys).containsExactlyElementsIn(RELEASED.keys)
    }

    @Test fun versionsHaveNoGaps() {
        assertWithMessage("Schema versions must run 1..latest with no gaps")
            .that(RELEASED.keys.sorted()).isEqualTo((1..latest()).toList())
    }

    private fun latest(): Int = schemas.keys.maxOrNull() ?: 0

    private fun identityHash(file: File): String =
        Regex(""""identityHash"\s*:\s*"([0-9a-f]+)"""").find(file.readText())?.groupValues?.get(1).orEmpty()

    private companion object {
        /** Every schema version that has shipped in a testing APK, with the identity hash Room exported for it. */
        val RELEASED = mapOf(
            1 to "d8a1a3635d06a3eb8df1d303eb091e35",
            2 to "e52acc494d6ecd7d7d4c4063fd890297",
            3 to "135e6f3f74b6a8c92b9dd9fd8b1f2694",
        )
    }
}
