package com.kurtz.homerecord.housemanual

import android.app.Application
import androidx.work.Configuration
import com.kurtz.homerecord.housemanual.reminders.ReminderWorker
import com.kurtz.homerecord.housemanual.widget.EmergencyWidgetProvider
import com.kurtz.homerecord.ui.DebugStrictMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class HouseManualApp : Application(), Configuration.Provider {
    // On-demand WorkManager init: also works where the startup initializer does not run (unit tests).
    override val workManagerConfiguration: Configuration = Configuration.Builder().build()

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        DebugStrictMode.enableIfDebuggable(this)
        container = AppContainer(this)
        ReminderWorker.schedule(this)
        keepWidgetFresh()
    }

    /** AC-87: redraw the emergency widget whenever the shutoff list changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun keepWidgetFresh() {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            container.homeRepository.observeHome()
                .flatMapLatest { home -> home?.let { container.shutoffRepository.observeShutoffs(it.id) } ?: flowOf(emptyList()) }
                .collect { EmergencyWidgetProvider.refresh(this@HouseManualApp, it.size) }
        }
    }
}
