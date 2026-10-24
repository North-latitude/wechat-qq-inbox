package com.local.unifiedinbox

import android.app.Application
import android.content.Context
import com.local.unifiedinbox.data.CollectorRepository
import com.local.unifiedinbox.data.ConfigStore
import com.local.unifiedinbox.data.InboxDatabase
import com.local.unifiedinbox.data.MetricsStore
import com.local.unifiedinbox.data.RuleStore
import com.local.unifiedinbox.data.StatusStore
import com.local.unifiedinbox.heartbeat.HeartbeatManager
import com.local.unifiedinbox.upload.ApiClient
import com.local.unifiedinbox.upload.UploadCoordinator
import com.local.unifiedinbox.upload.UploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppGraph {
    private val initLock = Any()

    @Volatile
    private var initialized = false

    lateinit var application: Application
        private set
    lateinit var appScope: CoroutineScope
        private set
    lateinit var database: InboxDatabase
        private set
    lateinit var metricsStore: MetricsStore
        private set
    lateinit var repository: CollectorRepository
        private set
    lateinit var configStore: ConfigStore
        private set
    lateinit var ruleStore: RuleStore
        private set
    lateinit var statusStore: StatusStore
        private set
    lateinit var apiClient: ApiClient
        private set
    lateinit var uploadCoordinator: UploadCoordinator
        private set
    lateinit var heartbeatManager: HeartbeatManager
        private set

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            val app = context.applicationContext as Application
            application = app
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            database = InboxDatabase(app)
            metricsStore = MetricsStore(database)
            repository = CollectorRepository(app, database, metricsStore)
            configStore = ConfigStore(app)
            ruleStore = RuleStore(app)
            statusStore = StatusStore(app)
            apiClient = ApiClient()
            uploadCoordinator = UploadCoordinator(
                context = app,
                appScope = appScope,
                repository = repository,
                configStore = configStore,
                statusStore = statusStore,
                apiClient = apiClient
            )
            heartbeatManager = HeartbeatManager(
                context = app,
                appScope = appScope,
                configStore = configStore,
                ruleStore = ruleStore,
                repository = repository,
                metricsStore = metricsStore,
                statusStore = statusStore,
                apiClient = apiClient
            )
            initialized = true
            appScope.launch {
                try {
                    repository.prune(System.currentTimeMillis())
                    if (repository.pendingCount() > 0) {
                        UploadScheduler.enqueueRetry(app)
                    }
                } catch (_: Exception) {
                    // Initialization must never be blocked by a transient local storage error.
                }
            }
        }
    }

    fun isInitialized(): Boolean = initialized
}
