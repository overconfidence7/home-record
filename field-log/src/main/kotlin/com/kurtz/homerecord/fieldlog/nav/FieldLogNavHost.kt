package com.kurtz.homerecord.fieldlog.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kurtz.homerecord.fieldlog.AppContainer
import com.kurtz.homerecord.fieldlog.capture.CameraXPhotoCapturer
import com.kurtz.homerecord.fieldlog.capture.CaptureRoute
import com.kurtz.homerecord.fieldlog.capture.CaptureViewModel
import com.kurtz.homerecord.fieldlog.capture.MediaRecorderVoiceRecorder
import com.kurtz.homerecord.fieldlog.entry.EntryRoute
import com.kurtz.homerecord.fieldlog.entry.EntryViewModel
import com.kurtz.homerecord.fieldlog.job.JobRoute
import com.kurtz.homerecord.fieldlog.job.JobViewModel
import com.kurtz.homerecord.fieldlog.jobedit.JobEditRoute
import com.kurtz.homerecord.fieldlog.jobedit.JobEditViewModel
import com.kurtz.homerecord.fieldlog.jobedit.QuickRoom
import com.kurtz.homerecord.fieldlog.jobs.JobsRoute
import com.kurtz.homerecord.fieldlog.jobs.JobsViewModel
import com.kurtz.homerecord.fieldlog.playback.MediaPlayerAudioPlayer

object Routes {
    const val JOBS = "jobs"
    const val JOB_ARG = "jobId"
    const val JOB = "job/{$JOB_ARG}"
    const val CAPTURE = "capture/{$JOB_ARG}"
    const val ENTRY_ARG = "entryId"
    const val ENTRY = "entry/{$ENTRY_ARG}"
    const val JOB_NEW = "jobedit/new"
    const val JOB_EDIT = "jobedit/{$JOB_ARG}"

    fun capture(id: String) = "capture/$id"
    fun entry(id: String) = "entry/$id"
    fun job(id: String) = "job/$id"
    fun jobEdit(id: String) = "jobedit/$id"
}

@Composable
fun FieldLogNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val resources = androidx.compose.ui.platform.LocalResources.current
    NavHost(navController = nav, startDestination = Routes.JOBS) {
        composable(Routes.JOBS) {
            val vm: JobsViewModel = viewModel(factory = remember { simpleFactory { JobsViewModel(container.jobRepository) } })
            JobsRoute(vm, onNewJob = { nav.navigate(Routes.JOB_NEW) }, onOpenJob = { nav.navigate(Routes.job(it)) })
        }
        composable(Routes.JOB_NEW) {
            val vm: JobEditViewModel = viewModel(
                factory = remember {
                    simpleFactory {
                        JobEditViewModel(container.jobRepository, null) { room: QuickRoom -> resources.getString(room.label) }
                    }
                },
            )
            JobEditRoute(vm, onDone = { nav.popBackStack() })
        }
        composable(Routes.JOB_EDIT, arguments = listOf(navArgument(Routes.JOB_ARG) { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString(Routes.JOB_ARG)
            val vm: JobEditViewModel = viewModel(
                factory = remember(id) {
                    simpleFactory {
                        JobEditViewModel(container.jobRepository, id) { room: QuickRoom -> resources.getString(room.label) }
                    }
                },
            )
            JobEditRoute(vm, onDone = { nav.popBackStack() })
        }
        composable(Routes.JOB, arguments = listOf(navArgument(Routes.JOB_ARG) { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString(Routes.JOB_ARG).orEmpty()
            val vm: JobViewModel = viewModel(factory = remember(id) { simpleFactory { JobViewModel(container.jobRepository, container.logEntryRepository, container.mediaStore, MediaPlayerAudioPlayer(), id) } })
            JobRoute(
                vm,
                onEdit = { nav.navigate(Routes.jobEdit(id)) },
                onCapture = { nav.navigate(Routes.capture(id)) },
                onOpenEntry = { nav.navigate(Routes.entry(it)) },
            )
        }
        composable(Routes.ENTRY, arguments = listOf(navArgument(Routes.ENTRY_ARG) { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString(Routes.ENTRY_ARG).orEmpty()
            val vm: EntryViewModel = viewModel(
                factory = remember(id) {
                    simpleFactory {
                        EntryViewModel(container.logEntryRepository, container.jobRepository, container.mediaStore, MediaPlayerAudioPlayer(), id)
                    }
                },
            )
            EntryRoute(vm, onDone = { nav.popBackStack() })
        }
        composable(Routes.CAPTURE, arguments = listOf(navArgument(Routes.JOB_ARG) { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString(Routes.JOB_ARG).orEmpty()
            val context = androidx.compose.ui.platform.LocalContext.current
            val capturer = remember { CameraXPhotoCapturer(context.applicationContext) }
            val recorder = remember { MediaRecorderVoiceRecorder(context.applicationContext) }
            val vm: CaptureViewModel = viewModel(
                factory = remember(id) {
                    simpleFactory { CaptureViewModel(id, container.logEntryRepository, container.mediaStore, capturer, recorder) }
                },
            )
            CaptureRoute(vm, capturer, container.shutterKeys, onBack = { nav.popBackStack() })
        }
    }
}
