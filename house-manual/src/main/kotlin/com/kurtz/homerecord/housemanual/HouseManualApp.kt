package com.kurtz.homerecord.housemanual

import android.app.Application

class HouseManualApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
