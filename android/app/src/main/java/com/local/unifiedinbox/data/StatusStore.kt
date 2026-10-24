package com.local.unifiedinbox.data

import android.content.Context

class StatusStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun setLastUploadResult(text: String, atMillis: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_LAST_UPLOAD_RESULT, text)
            .putLong(KEY_LAST_UPLOAD_AT, atMillis)
            .apply()
    }

    @Synchronized
    fun lastUploadResult(): String? =
        prefs.getString(KEY_LAST_UPLOAD_RESULT, null)

    @Synchronized
    fun lastUploadAt(): Long =
        prefs.getLong(KEY_LAST_UPLOAD_AT, 0L)

    @Synchronized
    fun setHeartbeatError(text: String, atMillis: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_LAST_HEARTBEAT_ERROR, text)
            .putLong(KEY_LAST_HEARTBEAT_AT, atMillis)
            .apply()
    }

    @Synchronized
    fun clearHeartbeatError(atMillis: Long = System.currentTimeMillis()) {
        prefs.edit()
            .remove(KEY_LAST_HEARTBEAT_ERROR)
            .putLong(KEY_LAST_HEARTBEAT_AT, atMillis)
            .apply()
    }

    @Synchronized
    fun lastHeartbeatError(): String? =
        prefs.getString(KEY_LAST_HEARTBEAT_ERROR, null)

    companion object {
        private const val PREFS_NAME = "inbox_status"
        private const val KEY_LAST_UPLOAD_RESULT = "last_upload_result"
        private const val KEY_LAST_UPLOAD_AT = "last_upload_at"
        private const val KEY_LAST_HEARTBEAT_ERROR = "last_heartbeat_error"
        private const val KEY_LAST_HEARTBEAT_AT = "last_heartbeat_at"
    }
}
