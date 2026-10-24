package com.local.unifiedinbox.upload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.local.unifiedinbox.AppGraph
import com.local.unifiedinbox.R
import kotlinx.coroutines.CancellationException

class UploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        AppGraph.initialize(applicationContext)
        return try {
            when (AppGraph.uploadCoordinator.uploadPending().terminal) {
                UploadTerminal.NOT_CONFIGURED,
                UploadTerminal.AUTH_REQUIRED -> Result.failure()

                UploadTerminal.RETRY_SCHEDULED -> Result.retry()

                UploadTerminal.SUCCESS,
                UploadTerminal.NO_PENDING -> Result.success()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            AppGraph.statusStore.setLastUploadResult(
                applicationContext.getString(R.string.status_unexpected)
            )
            Result.retry()
        }
    }
}
