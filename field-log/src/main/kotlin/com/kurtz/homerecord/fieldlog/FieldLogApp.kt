package com.kurtz.homerecord.fieldlog

import android.app.Application

class FieldLogApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
