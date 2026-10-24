package com.local.unifiedinbox.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.local.unifiedinbox.R
import com.local.unifiedinbox.util.BatteryOptimization

class KeepAliveActivity : AppCompatActivity() {

    private lateinit var tvBatteryStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_keep_alive)
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)

        findViewById<MaterialButton>(R.id.btnBatterySettings).setOnClickListener {
            openSystemSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        findViewById<MaterialButton>(R.id.btnAppDetails).setOnClickListener {
            openAppDetails()
        }
        findViewById<MaterialButton>(R.id.btnNotificationAccess).setOnClickListener {
            openSystemSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<MaterialButton>(R.id.btnOemXiaomi).setOnClickListener {
            openFirst(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            )
        }
        findViewById<MaterialButton>(R.id.btnOemHuawei).setOnClickListener {
            openFirst(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                ),
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            )
        }
        findViewById<MaterialButton>(R.id.btnOemOppo).setOnClickListener {
            openFirst(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                ),
                ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                ),
                ComponentName(
                    "com.coloros.oppoguardelf",
                    "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
                )
            )
        }
        findViewById<MaterialButton>(R.id.btnOemVivo).setOnClickListener {
            openFirst(
                ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                ),
                ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                )
            )
        }
        findViewById<MaterialButton>(R.id.btnOemMeizu).setOnClickListener {
            openFirst(
                ComponentName(
                    "com.meizu.safe",
                    "com.meizu.safe.permission.SmartBGActivity"
                ),
                ComponentName(
                    "com.meizu.safe",
                    "com.meizu.safe.security.SHOW_APPSEC"
                )
            )
        }
        findViewById<MaterialButton>(R.id.btnOemSamsung).setOnClickListener {
            openFirst(
                ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                ),
                ComponentName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        tvBatteryStatus.text = getString(
            if (BatteryOptimization.isIgnored(this)) {
                R.string.keep_alive_status_ignored
            } else {
                R.string.keep_alive_status_not_ignored
            }
        )
    }

    private fun openSystemSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.keep_alive_settings_unavailable, Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun openAppDetails() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )
        openSystemSettings(intent)
    }

    private fun openFirst(vararg components: ComponentName) {
        for (component in components) {
            val intent = Intent().apply {
                setComponent(component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
                // Try the next vendor-specific component, then fall back to app details.
            }
        }
        Toast.makeText(this, R.string.keep_alive_oem_unavailable, Toast.LENGTH_LONG).show()
        openAppDetails()
    }
}
