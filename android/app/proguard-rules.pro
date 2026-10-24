# WorkManager and the manifest-declared components are instantiated by name.
-keep class com.local.unifiedinbox.upload.UploadWorker { *; }
-keep class com.local.unifiedinbox.boot.BootReceiver { *; }
-keep class com.local.unifiedinbox.collector.InboxNotificationListenerService { *; }
