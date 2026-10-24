package com.local.unifiedinbox.upload

import android.content.Context
import com.local.unifiedinbox.R
import com.local.unifiedinbox.data.CollectorRepository
import com.local.unifiedinbox.data.ConfigStore
import com.local.unifiedinbox.data.StatusStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class UploadTerminal {
    SUCCESS,
    NO_PENDING,
    NOT_CONFIGURED,
    AUTH_REQUIRED,
    RETRY_SCHEDULED
}

data class UploadRunResult(
    val terminal: UploadTerminal,
    val uploaded: Int = 0,
    val dead: Int = 0,
    val detail: String? = null
)

class UploadCoordinator(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val repository: CollectorRepository,
    private val configStore: ConfigStore,
    private val statusStore: StatusStore,
    private val apiClient: ApiClient
) {
    private val uploadMutex = Mutex()
    private val appContext = context.applicationContext

    /**
     * Fire-and-forget upload used by the notification listener. WorkManager remains the durable
     * fallback if this process is killed before the request completes.
     */
    fun requestUpload() {
        appScope.launch {
            try {
                uploadPending()
            } catch (_: Exception) {
                statusStore.setLastUploadResult(
                    appContext.getString(R.string.status_unexpected)
                )
            }
        }
    }

    suspend fun uploadPending(): UploadRunResult = uploadMutex.withLock {
        val config = configStore.snapshot()
        if (!config.isConfigured) {
            val message = appContext.getString(R.string.status_not_configured)
            statusStore.setLastUploadResult(message)
            return@withLock UploadRunResult(
                terminal = UploadTerminal.NOT_CONFIGURED,
                detail = message
            )
        }

        var uploaded = 0
        var dead = 0
        while (true) {
            val batch = repository.fetchPending(CollectorRepository.MAX_BATCH_SIZE)
            if (batch.isEmpty()) {
                if (uploaded == 0 && dead == 0) {
                    return@withLock UploadRunResult(terminal = UploadTerminal.NO_PENDING)
                }
                val statusText = if (dead > 0) {
                    appContext.getString(
                        R.string.status_upload_success_dead,
                        uploaded,
                        dead
                    )
                } else {
                    appContext.getString(R.string.status_upload_success, uploaded)
                }
                statusStore.setLastUploadResult(statusText)
                return@withLock UploadRunResult(
                    terminal = UploadTerminal.SUCCESS,
                    uploaded = uploaded,
                    dead = dead
                )
            }

            val clientIds = batch.map { it.clientId }
            try {
                apiClient.sendBatch(
                    baseUrl = config.baseUrl,
                    token = config.token,
                    deviceId = config.deviceId,
                    items = batch
                )
                repository.markUploaded(clientIds)
                uploaded += batch.size
            } catch (exception: AuthException) {
                repository.markPendingFailure(clientIds, exception.message.orEmpty())
                val message = appContext.getString(R.string.status_auth_failed)
                statusStore.setLastUploadResult(message)
                return@withLock UploadRunResult(
                    terminal = UploadTerminal.AUTH_REQUIRED,
                    uploaded = uploaded,
                    dead = dead,
                    detail = message
                )
            } catch (exception: PermanentException) {
                repository.markDead(clientIds, exception.message.orEmpty())
                dead += batch.size
            } catch (exception: RetryableException) {
                repository.markPendingFailure(clientIds, exception.message.orEmpty())
                val message = appContext.getString(R.string.status_retryable)
                statusStore.setLastUploadResult(message)
                UploadScheduler.enqueueRetry(appContext)
                return@withLock UploadRunResult(
                    terminal = UploadTerminal.RETRY_SCHEDULED,
                    uploaded = uploaded,
                    dead = dead,
                    detail = message
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                repository.markPendingFailure(clientIds, "上传异常")
                val message = appContext.getString(R.string.status_unexpected)
                statusStore.setLastUploadResult(message)
                UploadScheduler.enqueueRetry(appContext)
                return@withLock UploadRunResult(
                    terminal = UploadTerminal.RETRY_SCHEDULED,
                    uploaded = uploaded,
                    dead = dead,
                    detail = message
                )
            }
        }
        @Suppress("UNREACHABLE_CODE")
        return@withLock UploadRunResult(terminal = UploadTerminal.SUCCESS)
    }
}
