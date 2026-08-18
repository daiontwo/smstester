package com.antteam.smstester

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle

class SystemPermissionCoordinator(
    private val activity: ComponentActivity
) {
    private var sequenceStarted = false
    private var permissionRequestRunning = false
    private var continueSetupAfterPermissionRequest = false

    private val permissionsLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionRequestRunning = false
        if (continueSetupAfterPermissionRequest) {
            continueSetupAfterPermissionRequest = false
            checkBatteryOptimization()
        }
    }

    private val batterySettingsLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkExactAlarmPermission()
    }

    private val exactAlarmSettingsLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    fun requestRequiredPermissions() {
        // Цепочка системных окон запускается один раз за запуск Activity.
        // Раньше onResume повторно открывал Settings после каждого возврата.
        if (sequenceStarted) return
        sequenceStarted = true

        val permissions = missingCriticalPermissions().toMutableList().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                addIfMissing(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isEmpty()) {
            checkBatteryOptimization()
        } else {
            permissionRequestRunning = true
            continueSetupAfterPermissionRequest = true
            permissionsLauncher.launch(permissions.toTypedArray())
        }
    }

    /** Повторная проверка перед операциями с USSD и SMS. */
    fun requestCriticalPermissions() {
        if (permissionRequestRunning) return
        val permissions = missingCriticalPermissions()
        if (permissions.isEmpty()) return
        // Системный диалог нельзя запускать, когда Activity находится в фоне.
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        permissionRequestRunning = true
        continueSetupAfterPermissionRequest = false
        permissionsLauncher.launch(permissions.toTypedArray())
    }

    private fun missingCriticalPermissions(): List<String> = buildList {
        addIfMissing(Manifest.permission.SEND_SMS)
        addIfMissing(Manifest.permission.RECEIVE_SMS)
        addIfMissing(Manifest.permission.CALL_PHONE)
    }

    private fun MutableList<String>.addIfMissing(permission: String) {
        if (
            ContextCompat.checkSelfPermission(activity, permission) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            add(permission)
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val powerManager = activity.getSystemService(PowerManager::class.java)
        if (powerManager.isIgnoringBatteryOptimizations(activity.packageName)) {
            checkExactAlarmPermission()
            return
        }

        val packageUri = Uri.parse("package:${activity.packageName}")
        val requestIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            packageUri
        )
        val fallbackIntent = Intent(
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        )
        launchSettings(batterySettingsLauncher, requestIntent, fallbackIntent)
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val alarmManager = activity.getSystemService(AlarmManager::class.java)
        if (alarmManager.canScheduleExactAlarms()) return

        val requestIntent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${activity.packageName}")
        )
        val fallbackIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        launchSettings(exactAlarmSettingsLauncher, requestIntent, fallbackIntent)
    }

    private fun launchSettings(
        launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
        primary: Intent,
        fallback: Intent
    ) {
        try {
            launcher.launch(primary)
        } catch (_: Exception) {
            launcher.launch(fallback)
        }
    }
}
