package com.kurtz.homerecord.housemanual

import android.app.Application
import androidx.work.Configuration
import com.kurtz.homerecord.housemanual.reminders.ReminderWorker

class HouseManualApp : Application(), Configuration.Provider {
    // On-demand WorkManager init: also works where the startup initializer does not run (unit tests).
    override val workManagerConfiguration: Configuration = Configuration.Builder().build()

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderWorker.schedule(this)
    }
}
