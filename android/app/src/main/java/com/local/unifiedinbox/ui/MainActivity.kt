package com.local.unifiedinbox.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.local.unifiedinbox.AppGraph
import com.local.unifiedinbox.R
import com.local.unifiedinbox.collector.CollectorState
import com.local.unifiedinbox.data.ConfigProblem
import com.local.unifiedinbox.upload.AuthException
import com.local.unifiedinbox.upload.PermanentException
import com.local.unifiedinbox.upload.RetryableException
import com.local.unifiedinbox.upload.UploadTerminal
import com.local.unifiedinbox.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var tvListenerAccess: TextView
    private lateinit var tvListenerService: TextView
    private lateinit var tvBackendStatus: TextView
    private lateinit var tvLastUpload: TextView
    private lateinit var tvLastUploadTime: TextView
    private lateinit var tvHeartbeatError: TextView
    private lateinit var tvQueue: TextView
    private lateinit var tvToday: TextView
    private lateinit var tvTestResult: TextView
    private lateinit var tilToken: TextInputLayout
    private lateinit var etBackendUrl: TextInputEditText
    private lateinit var etToken: TextInputEditText
    private lateinit var etDeviceName: TextInputEditText
    private lateinit var btnTestConnection: MaterialButton
    private lateinit var btnRetryUpload: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        AppGraph.initialize(this)
        setContentView(R.layout.activity_main)
        bindViews()
        prefillConfig()
        bindActions()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun bindViews() {
        tvListenerAccess = findViewById(R.id.tvListenerAccess)
        tvListenerService = findViewById(R.id.tvListenerService)
        tvBackendStatus = findViewById(R.id.tvBackendStatus)
        tvLastUpload = findViewById(R.id.tvLastUpload)
        tvLastUploadTime = findViewById(R.id.tvLastUploadTime)
        tvHeartbeatError = findViewById(R.id.tvHeartbeatError)
        tvQueue = findViewById(R.id.tvQueue)
        tvToday = findViewById(R.id.tvToday)
        tvTestResult = findViewById(R.id.tvTestResult)
        tilToken = findViewById(R.id.tilToken)
        etBackendUrl = findViewById(R.id.etBackendUrl)
        etToken = findViewById(R.id.etToken)
        etDeviceName = findViewById(R.id.etDeviceName)
        btnTestConnection = findViewById(R.id.btnTestConnection)
        btnRetryUpload = findViewById(R.id.btnRetryUpload)
    }

    private fun prefillConfig() {
        val config = AppGraph.configStore.snapshot()
        etBackendUrl.setText(config.baseUrl)
        etDeviceName.setText(config.deviceName)
        etToken.setText("")
        updateTokenHint()
    }

    private fun updateTokenHint() {
        tilToken.hint = getString(
            if (AppGraph.configStore.hasToken()) {
                R.string.main_token_saved_hint
            } else {
                R.string.main_token_hint
            }
        )
    }

    private fun bindActions() {
        findViewById<MaterialButton>(R.id.btnListenerSettings).setOnClickListener {
            openListenerSettings()
        }
        findViewById<MaterialButton>(R.id.btnRules).setOnClickListener {
            startActivity(Intent(this, RulesActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnHitLog).setOnClickListener {
            startActivity(Intent(this, HitLogActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnKeepAlive).setOnClickListener {
            startActivity(Intent(this, KeepAliveActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnSaveConfig).setOnClickListener {
            saveConfig(showFeedback = true)
        }
        btnTestConnection.setOnClickListener {
            testConnection()
        }
        findViewById<MaterialButton>(R.id.btnDisableSync).setOnClickListener {
            confirmDisableSync()
        }
        btnRetryUpload.setOnClickListener {
            retryUpload()
        }
    }

    private fun refreshStatus() {
        val accessEnabled = NotificationManagerCompat
            .getEnabledListenerPackages(this)
            .contains(packageName)
        tvListenerAccess.text = getString(
            if (accessEnabled) R.string.main_listener_access_yes else R.string.main_listener_access_no
        )
        tvListenerService.text = getString(
            if (CollectorState.isConnected()) {
                R.string.main_listener_connected
            } else {
                R.string.main_listener_disconnected
            }
        )

        val config = AppGraph.configStore.snapshot()
        tvBackendStatus.text = if (config.isConfigured) {
            getString(R.string.main_backend_configured, config.baseUrl)
        } else {
            getString(R.string.main_backend_not_configured)
        }

        val lastUpload = AppGraph.statusStore.lastUploadResult()
        tvLastUpload.text = if (lastUpload.isNullOrBlank()) {
            getString(R.string.main_last_upload_none)
        } else {
            getString(R.string.main_last_upload, lastUpload)
        }
        val lastUploadAt = AppGraph.statusStore.lastUploadAt()
        if (lastUploadAt > 0) {
            tvLastUploadTime.text = getString(
                R.string.main_last_upload_time,
                TimeFormat.local(lastUploadAt)
            )
            tvLastUploadTime.visibility = View.VISIBLE
        } else {
            tvLastUploadTime.text = ""
            tvLastUploadTime.visibility = View.GONE
        }

        val heartbeatError = AppGraph.statusStore.lastHeartbeatError()
        if (heartbeatError.isNullOrBlank()) {
            tvHeartbeatError.visibility = View.GONE
        } else {
            tvHeartbeatError.text = getString(
                R.string.main_last_heartbeat_error,
                heartbeatError
            )
            tvHeartbeatError.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val counts = AppGraph.repository.outboxCounts()
                val counters = AppGraph.metricsStore.snapshot()
                counts to counters
            }
            val counts = snapshot.first
            val counters = snapshot.second
            tvQueue.text = getString(
                R.string.main_queue,
                counts.pending,
                counts.dead
            )
            tvToday.text = getString(
                R.string.main_today_counters,
                counters.notificationsSeen,
                counters.matched,
                counters.skippedNoRule,
                counters.duplicates,
                counters.parseErrors,
                counters.summarySkipped
            )
        }
    }

    private fun saveConfig(showFeedback: Boolean): Boolean {
        val result = AppGraph.configStore.save(
            baseUrl = etBackendUrl.text?.toString().orEmpty(),
            token = etToken.text?.toString().orEmpty(),
            deviceName = etDeviceName.text?.toString().orEmpty()
        )
        if (!result.ok) {
            val message = configProblemText(result.problem)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return false
        }

        val config = result.config ?: AppGraph.configStore.snapshot()
        etBackendUrl.setText(config.baseUrl)
        etDeviceName.setText(config.deviceName)
        etToken.setText("")
        updateTokenHint()
        AppGraph.heartbeatManager.onConfigChanged()
        refreshStatus()
        if (showFeedback) {
            Toast.makeText(this, R.string.main_save_success, Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun testConnection() {
        if (!saveConfig(showFeedback = false)) return
        val config = AppGraph.configStore.snapshot()
        btnTestConnection.isEnabled = false
        tvTestResult.text = getString(R.string.main_testing)

        lifecycleScope.launch {
            val outcome = try {
                AppGraph.apiClient.ping(config.baseUrl, config.token)
            } catch (exception: Exception) {
                tvTestResult.text = getString(
                    R.string.main_test_failed,
                    networkErrorText(exception)
                )
                btnTestConnection.isEnabled = true
                return@launch
            }

            tvTestResult.text = outcome.serverTime?.let {
                getString(R.string.main_test_success, it)
            } ?: getString(R.string.main_test_success_no_time)
            btnTestConnection.isEnabled = true
            AppGraph.heartbeatManager.onConfigChanged()
        }
    }

    private fun retryUpload() {
        btnRetryUpload.isEnabled = false
        Toast.makeText(this, R.string.main_retry_started, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = try {
                AppGraph.uploadCoordinator.uploadPending()
            } catch (exception: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.main_upload_failed, networkErrorText(exception)),
                    Toast.LENGTH_LONG
                ).show()
                btnRetryUpload.isEnabled = true
                refreshStatus()
                return@launch
            }
            val message = when (result.terminal) {
                UploadTerminal.SUCCESS -> getString(
                    R.string.main_upload_success,
                    result.uploaded,
                    result.dead
                )
                UploadTerminal.NO_PENDING -> getString(R.string.main_upload_no_pending)
                UploadTerminal.NOT_CONFIGURED -> getString(R.string.main_upload_not_configured)
                UploadTerminal.AUTH_REQUIRED -> getString(R.string.main_upload_auth_failed)
                UploadTerminal.RETRY_SCHEDULED -> getString(R.string.main_upload_retry)
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            btnRetryUpload.isEnabled = true
            refreshStatus()
        }
    }

    private fun configProblemText(problem: ConfigProblem?): String = when (problem) {
        ConfigProblem.URL_REQUIRED -> getString(R.string.main_url_required)
        ConfigProblem.URL_HTTPS_REQUIRED -> getString(R.string.main_url_https_required)
        ConfigProblem.URL_QUERY_FORBIDDEN -> getString(R.string.main_url_no_query)
        ConfigProblem.URL_INVALID -> getString(R.string.main_url_invalid)
        ConfigProblem.TOKEN_REQUIRED -> getString(R.string.main_token_required)
        null -> getString(R.string.common_unknown_error)
    }

    private fun networkErrorText(exception: Exception): String = when (exception) {
        is AuthException -> getString(R.string.status_auth_failed)
        is PermanentException -> getString(R.string.status_permanent)
        is RetryableException -> getString(R.string.status_retryable)
        else -> getString(R.string.status_unexpected)
    }

    private fun openListenerSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, R.string.keep_alive_settings_unavailable, Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun confirmDisableSync() {
        if (!AppGraph.configStore.hasToken()) {
            Toast.makeText(this, R.string.main_backend_not_configured, Toast.LENGTH_SHORT).show()
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.main_disable_sync_title)
            .setMessage(R.string.main_disable_sync_message)
            .setNegativeButton(R.string.rules_cancel, null)
            .setPositiveButton(R.string.main_disable_sync) { _, _ ->
                AppGraph.configStore.clearServerConfig()
                prefillConfig()
                updateTokenHint()
                AppGraph.heartbeatManager.onConfigChanged()
                refreshStatus()
                Toast.makeText(this, R.string.main_backend_not_configured, Toast.LENGTH_SHORT)
                    .show()
            }
            .show()
    }
}
