package com.kurtz.homerecord.fieldlog

import android.app.Application
import com.kurtz.homerecord.ui.DebugStrictMode

class FieldLogApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        DebugStrictMode.enableIfDebuggable(this)
        container = AppContainer(this)
    }
}
