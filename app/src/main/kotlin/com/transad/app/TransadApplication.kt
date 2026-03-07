package com.transad.app

import android.app.Application

class TransadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.transad.app.api.ApiClient.init(applicationContext)
    }
}
