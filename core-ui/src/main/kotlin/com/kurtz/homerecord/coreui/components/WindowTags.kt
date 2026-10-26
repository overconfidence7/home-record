package com.kurtz.homerecord.coreui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/** Dialogs and sheets open their own window, which does not inherit the activity root's setting; QA flows need the ids. */
fun Modifier.windowTestTags(): Modifier = semantics { testTagsAsResourceId = true }
