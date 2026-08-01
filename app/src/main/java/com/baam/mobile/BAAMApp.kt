package com.baam.mobile

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class BAAMApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (com.baam.mobile.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
