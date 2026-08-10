package com.antteam.smstester

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.Calendar

class SmsSendingService : Service() {

    companion object {
        private const val PREFS_NAME = "sms_service_state"
        private const val KEY_RUNNING = "running"
        private const val KEY_PHONE = "phone"
        private const val KEY_MESSAGE = "message"
        private const val KEY_SUM = "sum"
        private const val KEY_SCHEDULES = "schedules"
        private const val KEY_DELAY_IN_MS = "delay_in_ms"

        private const val CHANNEL_ID = "sms_sending_service"
        private const val NOTIFICATION_ID = 1001
        private const val ALARM_REQUEST_CODE_BASE = 2000

        const val ACTION_START = "com.antteam.smstester.START_SMS_SERVICE"
        const val ACTION_UPDATE = "com.antteam.smstester.UPDATE_SMS_SERVICE"
        const val ACTION_STOP = "com.antteam.smstester.STOP_SMS_SERVICE"
        const val ACTION_ALARM = "com.antteam.smstester.ACTION_SMS_ALARM"

        const val EXTRA_PHONE = "phone"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_SUM = "sum"
        const val EXTRA_SCHEDULES_JSON = "schedules_json"
        const val EXTRA_DELAY_IN_MS = "delay_in_ms"
        const val EXTRA_SCHEDULE_ID = "schedule_id"

        fun isRunning(context: android.content.Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_RUNNING, false)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sendingJobs = mutableMapOf<Long, Job>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startAsForeground()
                val config = configFromIntent(intent)
                if (config == null) {
                    stopEverything()
                    return START_NOT_STICKY
                }
                cancelAlarms(loadConfig()?.schedules.orEmpty())
                saveConfig(config)
                saveRunningState(true)
                scheduleAll(config)
            }

            ACTION_UPDATE -> {
                startAsForeground()
                val config = configFromIntent(intent)
                if (config == null) return START_NOT_STICKY
                // Сначала отменяем alarms старой конфигурации, включая удалённые блоки.
                cancelAlarms(loadConfig()?.schedules.orEmpty())
                saveConfig(config)
                saveRunningState(true)
                scheduleAll(config)
            }

            ACTION_ALARM -> {
                startAsForeground()
                val config = loadConfig() ?: run {
                    stopEverything()
                    return START_NOT_STICKY
                }
                if (!isRunning(this)) {
                    stopEverything()
                    return START_NOT_STICKY
                }

                val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
                val schedule = config.schedules.firstOrNull { it.id == scheduleId }
                    ?: return START_NOT_STICKY

                // Сразу ставим следующий запуск именно этого расписания на следующий час.
                scheduleAlarm(config, schedule)
                sendBatch(config, schedule)
            }

            ACTION_STOP -> stopEverything()
        }
        return START_NOT_STICKY
    }

    private data class ServiceConfig(
        val phone: String,
        val message: String,
        val sum: String,
        val schedules: List<ScheduleConfig>,
        val delayInMs: Boolean
    )

    private fun configFromIntent(intent: Intent): ServiceConfig? {
        val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty()
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val sum = intent.getStringExtra(EXTRA_SUM).orEmpty()
        val schedules = schedulesFromJson(intent.getStringExtra(EXTRA_SCHEDULES_JSON))
        val delayInMs = intent.getBooleanExtra(EXTRA_DELAY_IN_MS, false)

        if (phone.isBlank() || message.isBlank() || sum.isBlank()) return null
        if (schedules.isEmpty() || schedules.any { !it.isValidSchedule() }) return null

        return ServiceConfig(phone, message, sum, schedules, delayInMs)
    }

    private fun sendBatch(config: ServiceConfig, schedule: ScheduleConfig) {
        sendingJobs[schedule.id]?.cancel()

        val count = schedule.count.toIntOrNull() ?: return
        val delayValue = schedule.interval.toLongOrNull() ?: return
        val delayMillis = if (config.delayInMs) delayValue else delayValue * 1000L

        sendingJobs[schedule.id] = serviceScope.launch {
            try {
                repeat(count) { index ->
                    ensureActive()
                    SmsSender.send(config.phone, "${config.message} ${config.sum}")
                    if (index < count - 1 && delayMillis > 0) delay(delayMillis)
                }
            } finally {
                sendingJobs.remove(schedule.id)
            }
        }
    }

    private fun scheduleAll(config: ServiceConfig) {
        config.schedules.forEach { scheduleAlarm(config, it) }
    }

    private fun scheduleAlarm(config: ServiceConfig, schedule: ScheduleConfig) {
        val minute = schedule.minute.toIntOrNull() ?: return
        val second = schedule.second.toIntOrNull() ?: return

        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.HOUR_OF_DAY, 1)
        }

        val alarmIntent = Intent(this, SmsAlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra(EXTRA_SCHEDULE_ID, schedule.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCodeFor(schedule.id),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.timeInMillis,
            pendingIntent
        )
    }

    private fun cancelAlarms(schedules: List<ScheduleConfig>) {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        schedules.forEach { schedule ->
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCodeFor(schedule.id),
                Intent(this, SmsAlarmReceiver::class.java).apply { action = ACTION_ALARM },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun requestCodeFor(id: Long): Int =
        ALARM_REQUEST_CODE_BASE + (kotlin.math.abs(id.hashCode()) % 100000)

    private fun saveConfig(config: ServiceConfig) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_PHONE, config.phone)
            .putString(KEY_MESSAGE, config.message)
            .putString(KEY_SUM, config.sum)
            .putString(KEY_SCHEDULES, schedulesToJson(config.schedules))
            .putBoolean(KEY_DELAY_IN_MS, config.delayInMs)
            .apply()
    }

    private fun loadConfig(): ServiceConfig? {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val phone = prefs.getString(KEY_PHONE, "").orEmpty()
        val message = prefs.getString(KEY_MESSAGE, "").orEmpty()
        val sum = prefs.getString(KEY_SUM, "").orEmpty()
        val schedules = schedulesFromJson(prefs.getString(KEY_SCHEDULES, null))
        val delayInMs = prefs.getBoolean(KEY_DELAY_IN_MS, false)

        if (phone.isBlank() || message.isBlank() || sum.isBlank()) return null
        if (schedules.isEmpty() || schedules.any { !it.isValidSchedule() }) return null
        return ServiceConfig(phone, message, sum, schedules, delayInMs)
    }

    private fun saveRunningState(running: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putBoolean(KEY_RUNNING, running).apply()
    }

    private fun stopEverything() {
        saveRunningState(false)
        sendingJobs.values.forEach { it.cancel() }
        sendingJobs.clear()
        cancelAlarms(loadConfig()?.schedules.orEmpty())
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground() {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("SMS Tester")
            .setContentText("Автоматическая отправка активна")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS отправка",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновая автоматическая отправка SMS"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        sendingJobs.values.forEach { it.cancel() }
        sendingJobs.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
