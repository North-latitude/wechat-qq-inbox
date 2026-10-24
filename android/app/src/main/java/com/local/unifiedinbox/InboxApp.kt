package com.local.unifiedinbox

import android.app.Application

class InboxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.initialize(this)
    }
}
