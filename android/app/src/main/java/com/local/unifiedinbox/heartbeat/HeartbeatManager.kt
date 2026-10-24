package com.local.unifiedinbox.heartbeat

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.local.unifiedinbox.BuildConfig
import com.local.unifiedinbox.R
import com.local.unifiedinbox.collector.CollectorState
import com.local.unifiedinbox.data.CollectorRepository
import com.local.unifiedinbox.data.ConfigStore
import com.local.unifiedinbox.data.MetricsStore
import com.local.unifiedinbox.data.RuleStore
import com.local.unifiedinbox.data.StatusStore
import com.local.unifiedinbox.upload.ApiClient
import com.local.unifiedinbox.upload.AuthException
import com.local.unifiedinbox.upload.PermanentException
import com.local.unifiedinbox.upload.RetryableException
import com.local.unifiedinbox.util.BatteryOptimization
import com.local.unifiedinbox.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class HeartbeatManager(
    context: Context,
    private val appScope: CoroutineScope,
    private val configStore: ConfigStore,
    private val ruleStore: RuleStore,
    private val repository: CollectorRepository,
    private val metricsStore: MetricsStore,
    private val statusStore: StatusStore,
    private val apiClient: ApiClient
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val heartbeatMutex = Mutex()
    private val scheduleLock = Any()

    @Volatile
    private var running = false

    @Volatile
    private var generation = 0L

    private val tick = object : Runnable {
        override fun run() {
            val tickGeneration = generation
            if (!running) return
            if (!CollectorState.isConnected()) {
                running = false
                return
            }
            triggerNow()
            if (running && tickGeneration == generation) {
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MILLIS)
            }
        }
    }

    fun onListenerConnected() {
        synchronized(scheduleLock) {
            if (!configStore.snapshot().isConfigured) {
                running = false
                generation++
                handler.removeCallbacks(tick)
                return
            }
            running = true
            generation++
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    fun onListenerDisconnected() {
        synchronized(scheduleLock) {
            running = false
            generation++
            handler.removeCallbacks(tick)
        }
        // Best effort: tell the server immediately that the listener is gone, instead of
        // waiting for the 180-second offline window. If the process/network is already gone,
        // the heartbeat simply fails and the server falls back to last-seen age.
        appScope.launch {
            heartbeatMutex.withLock {
                sendHeartbeat(listenerConnected = false)
            }
        }
    }

    fun onConfigChanged() {
        if (!CollectorState.isConnected()) return
        if (!configStore.snapshot().isConfigured) {
            onListenerDisconnected()
            return
        }
        if (!running) {
            onListenerConnected()
        } else {
            triggerNow()
        }
    }

    fun onRulesChanged() {
        onConfigChanged()
    }

    fun triggerNow() {
        if (!running || !CollectorState.isConnected()) return
        appScope.launch {
            heartbeatMutex.withLock {
                sendHeartbeat()
            }
        }
    }

    private suspend fun sendHeartbeat(listenerConnected: Boolean = CollectorState.isConnected()) {
        if (!listenerConnected && running) return
        val config = configStore.snapshot()
        if (!config.isConfigured) {
            statusStore.setHeartbeatError(
                appContext.getString(R.string.status_not_configured)
            )
            return
        }

        try {
            val payload = withContext(Dispatchers.IO) {
                buildPayload(config, listenerConnected)
            }
            apiClient.sendHeartbeat(
                baseUrl = config.baseUrl,
                token = config.token,
                payload = payload
            )
            statusStore.clearHeartbeatError()
        } catch (exception: AuthException) {
            statusStore.setHeartbeatError(
                appContext.getString(R.string.status_auth_failed)
            )
        } catch (exception: RetryableException) {
            statusStore.setHeartbeatError(
                appContext.getString(R.string.status_retryable)
            )
        } catch (exception: PermanentException) {
            statusStore.setHeartbeatError(
                appContext.getString(R.string.status_permanent)
            )
        } catch (_: Exception) {
            statusStore.setHeartbeatError(
                appContext.getString(R.string.status_heartbeat_failed)
            )
        }
    }

    private fun buildPayload(
        config: com.local.unifiedinbox.data.CollectorConfig,
        listenerConnected: Boolean
    ): JSONObject {
        val counters = metricsStore.snapshot()
        val rules = ruleStore.all()
        val lastNotificationAt = metricsStore.lastNotificationAt()

        return JSONObject().apply {
            put("device_id", config.deviceId)
            put("app_version", BuildConfig.VERSION_NAME)
            put("listener_connected", listenerConnected)
            if (lastNotificationAt == null) {
                put("last_notification_at", JSONObject.NULL)
            } else {
                put("last_notification_at", TimeFormat.isoUtc(lastNotificationAt))
            }
            put("queue_size", repository.pendingCount())
            put("battery_optimization_ignored", BatteryOptimization.isIgnored(appContext))
            put(
                "counters",
                JSONObject().apply {
                    put("notifications_seen", counters.notificationsSeen)
                    put("matched", counters.matched)
                    put("skipped_no_rule", counters.skippedNoRule)
                    put("duplicates", counters.duplicates)
                    put("parse_errors", counters.parseErrors)
                    put("summary_skipped", counters.summarySkipped)
                }
            )
            put(
                "rules",
                JSONArray().apply {
                    for (rule in rules) {
                        put(
                            JSONObject().apply {
                                put("id", rule.id)
                                put("type", rule.type.apiValue)
                                put("enabled", rule.enabled)
                            }
                        )
                    }
                }
            )
        }
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MILLIS = 60_000L
    }
}
