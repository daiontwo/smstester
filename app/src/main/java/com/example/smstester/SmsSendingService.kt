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

        fun isRunning(context: android.content.Context): Boolean {
            return context
                .getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_RUNNING, false)
        }
        private const val CHANNEL_ID = "sms_sending_service"
        private const val NOTIFICATION_ID = 1001
        private const val ALARM_REQUEST_CODE = 2001

        const val ACTION_START =
            "com.antteam.smstester.START_SMS_SERVICE"

        const val ACTION_STOP =
            "com.antteam.smstester.STOP_SMS_SERVICE"

        const val ACTION_ALARM =
            "com.antteam.smstester.ACTION_SMS_ALARM"

        const val EXTRA_PHONE = "phone"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_SUM = "sum"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_SECOND = "second"
        const val EXTRA_LIMIT = "limit"
        const val EXTRA_DELAY = "delay"
        const val EXTRA_DELAY_IN_MS = "delay_in_ms"
    }

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var sendingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }


    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START -> {
                startAsForeground()
                saveRunningState(true)

                if (!isValidConfig(intent)) {
                    stopEverything()
                    return START_NOT_STICKY
                }

                // Если пользователь повторно нажал Start,
                // старый alarm заменится новым.
                scheduleNextAlarm(intent)
            }

            ACTION_ALARM -> {
                startAsForeground()
                saveRunningState(true)

                if (!isValidConfig(intent)) {
                    stopEverything()
                    return START_NOT_STICKY
                }

                // Сразу планируем следующий час.
                // Даже если процесс позже будет убит во время пачки,
                // следующий alarm уже зарегистрирован в Android.
                scheduleNextAlarm(intent)

                sendBatch(intent)
            }

            ACTION_STOP -> {
                stopEverything()
            }
        }

        return START_NOT_STICKY
    }

    private fun sendBatch(intent: Intent) {

        // Защита от двух одновременно запущенных пачек.
        sendingJob?.cancel()

        val phone =
            intent.getStringExtra(EXTRA_PHONE).orEmpty()

        val message =
            intent.getStringExtra(EXTRA_MESSAGE).orEmpty()

        val sum =
            intent.getStringExtra(EXTRA_SUM).orEmpty()

        val limit =
            intent.getIntExtra(EXTRA_LIMIT, 5)

        val delayValue =
            intent.getLongExtra(EXTRA_DELAY, 1L)

        val delayInMs =
            intent.getBooleanExtra(
                EXTRA_DELAY_IN_MS,
                false
            )

        val delayMillis =
            if (delayInMs) {
                delayValue
            } else {
                delayValue * 1000L
            }

        sendingJob = serviceScope.launch {

            repeat(limit) { index ->

                ensureActive()

                SmsSender.send(
                    phone,
                    "$message $sum"
                )

                if (
                    index < limit - 1 &&
                    delayMillis > 0
                ) {
                    delay(delayMillis)
                }
            }

            sendingJob = null
        }
    }

    private fun scheduleNextAlarm(
        sourceIntent: Intent
    ) {

        val minute =
            sourceIntent.getIntExtra(
                EXTRA_MINUTE,
                59
            )

        val second =
            sourceIntent.getIntExtra(
                EXTRA_SECOND,
                52
            )

        val now = Calendar.getInstance()

        val next = Calendar.getInstance().apply {

            set(
                Calendar.MINUTE,
                minute
            )

            set(
                Calendar.SECOND,
                second
            )

            set(
                Calendar.MILLISECOND,
                0
            )

            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.HOUR_OF_DAY, 1)
            }
        }

        val alarmIntent =
            Intent(
                this,
                SmsAlarmReceiver::class.java
            ).apply {

                action = ACTION_ALARM

                putExtra(
                    EXTRA_PHONE,
                    sourceIntent.getStringExtra(EXTRA_PHONE)
                )

                putExtra(
                    EXTRA_MESSAGE,
                    sourceIntent.getStringExtra(EXTRA_MESSAGE)
                )

                putExtra(
                    EXTRA_SUM,
                    sourceIntent.getStringExtra(EXTRA_SUM)
                )

                putExtra(
                    EXTRA_MINUTE,
                    minute
                )

                putExtra(
                    EXTRA_SECOND,
                    second
                )

                putExtra(
                    EXTRA_LIMIT,
                    sourceIntent.getIntExtra(
                        EXTRA_LIMIT,
                        5
                    )
                )

                putExtra(
                    EXTRA_DELAY,
                    sourceIntent.getLongExtra(
                        EXTRA_DELAY,
                        1L
                    )
                )

                putExtra(
                    EXTRA_DELAY_IN_MS,
                    sourceIntent.getBooleanExtra(
                        EXTRA_DELAY_IN_MS,
                        false
                    )
                )
            }

        val pendingIntent =
            PendingIntent.getBroadcast(
                this,
                ALARM_REQUEST_CODE,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val alarmManager =
            getSystemService(
                ALARM_SERVICE
            ) as AlarmManager

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            // Не пытаемся вызвать exact alarm без разрешения.
            // MainActivity должна запросить этот доступ.
            return
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.timeInMillis,
            pendingIntent
        )
    }

    private fun cancelAlarm() {

        val alarmIntent =
            Intent(
                this,
                SmsAlarmReceiver::class.java
            ).apply {
                action = ACTION_ALARM
            }

        val pendingIntent =
            PendingIntent.getBroadcast(
                this,
                ALARM_REQUEST_CODE,
                alarmIntent,
                PendingIntent.FLAG_NO_CREATE or
                        PendingIntent.FLAG_IMMUTABLE
            )

        if (pendingIntent != null) {

            val alarmManager =
                getSystemService(
                    ALARM_SERVICE
                ) as AlarmManager

            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun isValidConfig(
        intent: Intent
    ): Boolean {

        val phone =
            intent.getStringExtra(EXTRA_PHONE).orEmpty()

        val message =
            intent.getStringExtra(EXTRA_MESSAGE).orEmpty()

        val sum =
            intent.getStringExtra(EXTRA_SUM).orEmpty()

        val minute =
            intent.getIntExtra(EXTRA_MINUTE, -1)

        val second =
            intent.getIntExtra(EXTRA_SECOND, -1)

        val limit =
            intent.getIntExtra(EXTRA_LIMIT, 0)

        val delayValue =
            intent.getLongExtra(EXTRA_DELAY, -1)

        return phone.isNotBlank() &&
                message.isNotBlank() &&
                sum.isNotBlank() &&
                minute in 0..59 &&
                second in 0..59 &&
                limit > 0 &&
                delayValue >= 0
    }

    private fun saveRunningState(running: Boolean) {
        getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )
            .edit()
            .putBoolean(KEY_RUNNING, running)
            .apply()
    }

    private fun stopEverything() {

        sendingJob?.cancel()
        sendingJob = null

        cancelAlarm()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground() {

        val openAppIntent =
            Intent(
                this,
                MainActivity::class.java
            )

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_email
                )
                .setContentTitle("SMS Tester")
                .setContentText(
                    "Автоматическая отправка активна"
                )
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build()

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {

            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "SMS отправка",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description =
                        "Фоновая автоматическая отправка SMS"
                }

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(
                channel
            )
        }
    }

    override fun onDestroy() {

        sendingJob?.cancel()
        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}