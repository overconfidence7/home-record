package com.kurtz.homerecord.housemanual.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Builds a [ViewModelProvider.Factory] around one creation lambda (manual DI, no Hilt). */
inline fun <reified VM : ViewModel> simpleFactory(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
