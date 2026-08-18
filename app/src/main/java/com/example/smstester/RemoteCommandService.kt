package com.antteam.smstester

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Постоянно слушает команды, которые должны работать без открытого интерфейса.
 */
class RemoteCommandService : Service() {
    companion object {
        private const val CHANNEL_ID = "remote_commands"
        private const val NOTIFICATION_ID = 1002
        private const val PREFS = "remote_commands"
        private const val KEY_LAST_BACKGROUND_COMMAND_ID = "lastBackgroundCommandId"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RemoteCommandService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RemoteCommandService::class.java))
        }
    }

    private var subscription: DeviceConfigSync.Subscription? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("SMS Tester")
                .setContentText("Ожидание удалённых команд")
                .setOngoing(true)
                .setSilent(true)
                .build()
        )
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (subscription == null) startListening()
        return START_STICKY
    }

    private fun startListening() {
        if (LicenseManager.getSavedToken(this).isNullOrBlank()) {
            stopSelf()
            return
        }

        val deviceId = LicenseManager.getDeviceId(this)
        subscription = DeviceConfigSync.listenCommands(
            deviceId = deviceId,
            onCommand = { command -> handleCommand(deviceId, command) },
            onError = { error ->
                DeviceConfigSync.reportRuntime(
                    deviceId,
                    "background-listener",
                    SmsSendingService.isRunning(this),
                    "Ошибка Firebase: $error"
                )
            }
        )
    }

    private fun handleCommand(deviceId: String, command: DeviceConfigSync.RemoteCommand) {
        if (command.action != "GET_PHONE" && command.action != "VERIFY_PHONE") return

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_BACKGROUND_COMMAND_ID, "") == command.commandId) return

        // Запоминаем команду до запуска операции, чтобы повторное событие Firebase
        // не выполнило платный USSD/SMS-запрос второй раз.
        prefs.edit().putString(KEY_LAST_BACKGROUND_COMMAND_ID, command.commandId).apply()

        when (command.action) {
            "GET_PHONE" -> getPhone(deviceId, command.commandId)
            "VERIFY_PHONE" -> verifyPhone(deviceId, command)
        }
    }

    private fun getPhone(deviceId: String, commandId: String) {
        val missing = missingPermissions(Manifest.permission.CALL_PHONE, Manifest.permission.RECEIVE_SMS)
        if (missing.isNotEmpty()) {
            report(deviceId, commandId, "Нет разрешений ${missing.joinToString()}")
            return
        }
        UssdNumberManager.request(this) { error -> report(deviceId, commandId, error.orEmpty()) }
    }

    private fun verifyPhone(deviceId: String, command: DeviceConfigSync.RemoteCommand) {
        val missing = missingPermissions(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
        if (missing.isNotEmpty()) {
            val error = "Нет разрешений ${missing.joinToString()}"
            report(deviceId, command.commandId, error)
            DeviceConfigSync.reportPhoneVerification(this, false, error)
            return
        }

        val error = PhoneVerificationManager.start(this, command.targetPhone.orEmpty())
        report(deviceId, command.commandId, error.orEmpty())
        if (error != null) DeviceConfigSync.reportPhoneVerification(this, false, error)
    }

    private fun missingPermissions(vararg permissions: String): List<String> =
        permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.map { it.substringAfterLast('.') }

    private fun report(deviceId: String, commandId: String, error: String) {
        DeviceConfigSync.reportRuntime(
            deviceId,
            commandId,
            SmsSendingService.isRunning(this),
            error
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Удалённые команды",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Получение команд проверки и определения номера" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        subscription?.stop()
        subscription = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
