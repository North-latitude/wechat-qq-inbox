package com.local.unifiedinbox.util

import android.content.Context
import android.os.PowerManager

object BatteryOptimization {
    fun isIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
