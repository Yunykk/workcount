package com.workcount.app

import android.app.Application
import android.content.Context
import com.workcount.app.di.AppContainer

class WorkCountApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as WorkCountApplication).appContainer
