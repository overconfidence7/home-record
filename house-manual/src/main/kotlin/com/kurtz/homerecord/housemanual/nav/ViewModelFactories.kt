package com.kurtz.homerecord.housemanual.nav

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras

/** Builds a [ViewModelProvider.Factory] around one creation lambda (manual DI, no Hilt). */
inline fun <reified VM : ViewModel> simpleFactory(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }

/** Like [simpleFactory] but hands the creation lambda a [SavedStateHandle] so form input survives process death. */
inline fun <reified VM : ViewModel> savedStateFactory(crossinline create: (SavedStateHandle) -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            create(extras.createSavedStateHandle()) as T
    }
