package com.kurtz.homerecord.fieldlog.settings

import com.kurtz.homerecord.core.photo.FileStorage
import com.kurtz.homerecord.core.units.UnitSystem

/**
 * FL-Settings (T065a): the units lengths show in (AC-C4), whether this phone lets Field Log post notifications
 * (AC-F36, AC-F42), and what job files hold on this phone with Free up (AC-F20). [storage] is null while it is counted.
 */
data class SettingsUiState(
    val units: UnitSystem = UnitSystem.IMPERIAL,
    val notificationsOn: Boolean = true,
    val storage: FileStorage? = null,
)

sealed interface SettingsEvent {
    data class SetUnits(val system: UnitSystem) : SettingsEvent
    data object OpenNotificationSettings : SettingsEvent
    data object FreeUp : SettingsEvent
    data object Back : SettingsEvent
}
