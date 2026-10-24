package com.local.unifiedinbox.data

import android.content.Context
import android.os.Build
import com.local.unifiedinbox.BuildConfig
import com.local.unifiedinbox.R
import java.net.URI
import java.util.Locale
import java.util.UUID

data class CollectorConfig(
    val baseUrl: String,
    val token: String,
    val deviceId: String,
    val deviceName: String
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && token.isNotBlank()
}

enum class ConfigProblem {
    URL_REQUIRED,
    URL_INVALID,
    URL_HTTPS_REQUIRED,
    URL_QUERY_FORBIDDEN,
    TOKEN_REQUIRED
}

data class ConfigSaveResult(
    val ok: Boolean,
    val problem: ConfigProblem? = null,
    val config: CollectorConfig? = null
)

sealed class BaseUrlResult {
    data class Valid(val baseUrl: String) : BaseUrlResult()
    data class Invalid(val problem: ConfigProblem) : BaseUrlResult()
}

class ConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun snapshot(): CollectorConfig = synchronized(lock) {
        readLocked()
    }

    fun save(baseUrl: String, token: String, deviceName: String): ConfigSaveResult {
        val normalized = when (val result = normalizeBaseUrl(baseUrl)) {
            is BaseUrlResult.Invalid -> return ConfigSaveResult(
                ok = false,
                problem = result.problem
            )

            is BaseUrlResult.Valid -> result.baseUrl
        }

        return synchronized(lock) {
            val existing = readLocked()
            val effectiveToken = token.trim().ifEmpty { existing.token }
            if (effectiveToken.isBlank()) {
                ConfigSaveResult(ok = false, problem = ConfigProblem.TOKEN_REQUIRED)
            } else {
                val effectiveDeviceName = deviceName.trim().ifEmpty {
                    defaultDeviceName()
                }.take(MAX_DEVICE_NAME_LENGTH)

                prefs.edit()
                    .putString(KEY_BASE_URL, normalized)
                    .putString(KEY_TOKEN, effectiveToken)
                    .putString(KEY_DEVICE_ID, existing.deviceId)
                    .putString(KEY_DEVICE_NAME, effectiveDeviceName)
                    .apply()

                ConfigSaveResult(
                    ok = true,
                    config = CollectorConfig(
                        baseUrl = normalized,
                        token = effectiveToken,
                        deviceId = existing.deviceId,
                        deviceName = effectiveDeviceName
                    )
                )
            }
        }
    }

    fun hasToken(): Boolean = synchronized(lock) {
        !prefs.getString(KEY_TOKEN, null).isNullOrBlank()
    }

    /** Keep device identity, but stop all computer sync/upload attempts. */
    fun clearServerConfig() = synchronized(lock) {
        prefs.edit()
            .remove(KEY_BASE_URL)
            .remove(KEY_TOKEN)
            .apply()
    }

    fun normalizeBaseUrl(raw: String): BaseUrlResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return BaseUrlResult.Invalid(ConfigProblem.URL_REQUIRED)
        }
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return BaseUrlResult.Invalid(ConfigProblem.URL_INVALID)
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "https" && scheme != "http") {
            return BaseUrlResult.Invalid(ConfigProblem.URL_INVALID)
        }
        if (scheme == "http" && !BuildConfig.DEBUG) {
            return BaseUrlResult.Invalid(ConfigProblem.URL_HTTPS_REQUIRED)
        }
        if (uri.host.isNullOrBlank() || uri.userInfo != null) {
            return BaseUrlResult.Invalid(ConfigProblem.URL_INVALID)
        }
        if (uri.query != null || uri.fragment != null) {
            return BaseUrlResult.Invalid(ConfigProblem.URL_QUERY_FORBIDDEN)
        }
        val normalized = uri.toString().trimEnd('/')
        if (normalized.isBlank()) {
            return BaseUrlResult.Invalid(ConfigProblem.URL_INVALID)
        }
        return BaseUrlResult.Valid(normalized)
    }

    private fun readLocked(): CollectorConfig {
        val deviceId = ensureDeviceIdLocked()
        return CollectorConfig(
            baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty(),
            token = prefs.getString(KEY_TOKEN, "").orEmpty(),
            deviceId = deviceId,
            deviceName = prefs.getString(KEY_DEVICE_NAME, null)
                ?.takeIf { it.isNotBlank() }
                ?: defaultDeviceName()
        )
    }

    private fun ensureDeviceIdLocked(): String {
        val stored = prefs.getString(KEY_DEVICE_ID, null)
        if (stored != null && isValidUuid(stored)) return stored
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    private fun isValidUuid(value: String): Boolean = try {
        UUID.fromString(value)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun defaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val combined = listOf(manufacturer, model)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return combined.ifEmpty {
            appContext.getString(R.string.main_device_name_default)
        }.take(MAX_DEVICE_NAME_LENGTH)
    }

    companion object {
        private const val PREFS_NAME = "inbox_config"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val MAX_DEVICE_NAME_LENGTH = 100
    }
}
