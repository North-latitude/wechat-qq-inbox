package com.local.unifiedinbox.boot

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import com.local.unifiedinbox.collector.InboxNotificationListenerService
import com.local.unifiedinbox.upload.UploadScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        UploadScheduler.enqueueRetry(context)
        try {
            // Official platform API: ask the system to rebind the listener after boot.
            NotificationListenerService.requestRebind(
                ComponentName(context, InboxNotificationListenerService::class.java)
            )
        } catch (_: Exception) {
            // The system also rebinds on its own; a failure here is not fatal.
        }
    }
}
