package com.kurtz.homerecord.housemanual

import android.app.Application
import com.kurtz.homerecord.housemanual.reminders.ReminderWorker

class HouseManualApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderWorker.schedule(this)
    }
}
