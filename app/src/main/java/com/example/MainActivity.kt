package com.example

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.net.BackgroundPowerHelper
import com.example.net.BleMeshEngine
import com.example.net.MeshNodeService
import com.example.ui.BlueBlAppScreen
import com.example.ui.BlueBlViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: BlueBlViewModel by viewModels()

    private val prefs by lazy {
        getSharedPreferences("bluebl_prefs", Context.MODE_PRIVATE)
    }

    private val blePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.onPermissionsResult()
        maybePromptBatteryOptimizationOnce()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionsResult()
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            BackgroundPowerHelper.openNotificationSettings(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        MeshNodeService.ensureChannel(this)

        val missingStartup = BleMeshEngine.missingStartupPermissions(this)
        if (missingStartup.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                missingStartup.contains(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                prefs.edit().putBoolean(KEY_NOTIF_ASKED, true).apply()
            }
            blePermissionLauncher.launch(missingStartup)
        } else {
            maybePromptBatteryOptimizationOnce()
        }

        setContent {
            BlueBlAppScreen(
                viewModel = viewModel,
                onRequestBlePermissions = {
                    blePermissionLauncher.launch(BleMeshEngine.missingStartupPermissions(this))
                },
                onRequestNotificationPermission = {
                    requestNotificationPermissionFlow()
                },
                onRequestBatteryAndBackground = {
                    BackgroundPowerHelper.launchBatteryAndBackgroundSettings(this)
                },
                onDisconnectAndExit = {
                    viewModel.disconnectAndShutDown()
                    finishAndRemoveTask()
                }
            )
        }
    }

    private fun requestNotificationPermissionFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (BackgroundPowerHelper.hasNotificationPermission(this)) {
                viewModel.refreshRadioDiagnostics()
                return
            }
            val alreadyAsked = prefs.getBoolean(KEY_NOTIF_ASKED, false)
            if (alreadyAsked && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                BackgroundPowerHelper.openNotificationSettings(this)
            } else {
                prefs.edit().putBoolean(KEY_NOTIF_ASKED, true).apply()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            BackgroundPowerHelper.openNotificationSettings(this)
        }
    }

    private fun maybePromptBatteryOptimizationOnce() {
        if (BackgroundPowerHelper.isIgnoringBatteryOptimizations(this)) return
        if (prefs.getBoolean(KEY_BATTERY_OPT_PROMPTED, false)) return
        prefs.edit().putBoolean(KEY_BATTERY_OPT_PROMPTED, true).apply()
        BackgroundPowerHelper.launchBatteryAndBackgroundSettings(this)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshRadioDiagnostics()
    }

    private companion object {
        const val KEY_BATTERY_OPT_PROMPTED = "battery_opt_prompted"
        const val KEY_NOTIF_ASKED = "notif_permission_asked"
    }
}
