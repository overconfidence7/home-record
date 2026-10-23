package com.kurtz.homerecord.coreui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.kurtz.homerecord.coreui.components.EmptyStatePreview
import com.kurtz.homerecord.coreui.components.ErrorStatePreview
import com.kurtz.homerecord.coreui.components.LoadingStatePreview
import com.kurtz.homerecord.coreui.components.RecordListItemPreview

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "200% font", fontScale = 2f, showBackground = true)
annotation class ThemeScreenshots

@PreviewTest
@ThemeScreenshots
@Composable
fun RecordListItemScreenshot() = RecordListItemPreview()

@PreviewTest
@ThemeScreenshots
@Composable
fun EmptyStateScreenshot() = EmptyStatePreview()

@PreviewTest
@ThemeScreenshots
@Composable
fun LoadingStateScreenshot() = LoadingStatePreview()

@PreviewTest
@ThemeScreenshots
@Composable
fun ErrorStateScreenshot() = ErrorStatePreview()

@PreviewTest
@ThemeScreenshots
@Composable
fun SignInEmailScreenshot() = com.kurtz.homerecord.coreui.components.SignInEmailPreview()

@PreviewTest
@ThemeScreenshots
@Composable
fun SignInCodeScreenshot() = com.kurtz.homerecord.coreui.components.SignInCodePreview()

@PreviewTest
@ThemeScreenshots
@Composable
fun SyncStatusChipScreenshot() = com.kurtz.homerecord.coreui.components.SyncStatusChipPreview()

@PreviewTest
@ThemeScreenshots
@Composable
fun WarrantyStatusLineScreenshot() = com.kurtz.homerecord.coreui.components.WarrantyStatusLinePreview()
