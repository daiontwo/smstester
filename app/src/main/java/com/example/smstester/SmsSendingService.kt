package com.antteam.smstester

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

object SmsSendingState {

    val running =
        MutableStateFlow(false)

    val sendingPulse =
        MutableStateFlow(0)

    fun pulse() {
        sendingPulse.update {
            it + 1
        }
    }
}

class SmsSendingService : Service() {

    companion object {

        const val ACTION_START =
            "com.antteam.smstester.action.START"

        const val ACTION_UPDATE =
            "com.antteam.smstester.action.UPDATE"

        const val ACTION_STOP =
            "com.antteam.smstester.action.STOP"

        const val EXTRA_PHONE = "phone"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_SUMM = "summ"

        const val EXTRA_SCHEDULES_JSON =
            "schedules_json"

        const val EXTRA_DELAY_MS =
            "delay_ms"

        private const val CHANNEL_ID =
            "sms_sending_channel"

        private const val NOTIFICATION_ID =
            7878
    }

    private data class CommonConfig(
        val phone: String,
        val message: String,
        val summ: String,
        val delayInMs: Boolean
    )

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Default
        )

    /*
     * Каждый id расписания имеет собственную Job.
     * Поэтому все расписания работают независимо.
     */
    private val scheduleJobs =
        mutableMapOf<Long, Job>()

    private val activeSchedules =
        mutableMapOf<Long, ScheduleConfig>()

    private var commonConfig: CommonConfig? =
        null

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

                SmsSendingState.running.value =
                    true

                applyConfiguration(
                    intent = intent,
                    forceRestartAll = true
                )
            }

            ACTION_UPDATE -> {

                if (SmsSendingState.running.value) {

                    applyConfiguration(
                        intent = intent,
                        forceRestartAll = false
                    )
                }
            }

            ACTION_STOP -> {
                stopSending()
            }
        }

        return START_STICKY
    }

    private fun applyConfiguration(
        intent: Intent,
        forceRestartAll: Boolean
    ) {

        val newCommon =
            CommonConfig(
                phone =
                    intent
                        .getStringExtra(EXTRA_PHONE)
                        .orEmpty(),

                message =
                    intent
                        .getStringExtra(EXTRA_MESSAGE)
                        .orEmpty(),

                summ =
                    intent
                        .getStringExtra(EXTRA_SUMM)
                        .orEmpty(),

                delayInMs =
                    intent.getBooleanExtra(
                        EXTRA_DELAY_MS,
                        false
                    )
            )

        val schedules =
            schedulesFromJson(
                intent
                    .getStringExtra(
                        EXTRA_SCHEDULES_JSON
                    )
                    .orEmpty()
            )
                .filter {
                    it.isValidSchedule()
                }
                .take(MAX_SCHEDULES)

        val commonChanged =
            commonConfig != newCommon

        if (
            forceRestartAll ||
            commonChanged
        ) {

            scheduleJobs.values
                .forEach {
                    it.cancel()
                }

            scheduleJobs.clear()
            activeSchedules.clear()
        }

        commonConfig = newCommon

        val newSchedulesById =
            schedules.associateBy {
                it.id
            }

        /*
         * Удалили блок в интерфейсе:
         * отменяем только его Job.
         */
        val removedIds =
            activeSchedules.keys -
                newSchedulesById.keys

        removedIds.forEach { id ->

            scheduleJobs
                .remove(id)
                ?.cancel()

            activeSchedules.remove(id)

            Log.d(
                "SmsSendingService",
                "Удалено расписание id=$id"
            )
        }

        /*
         * Новое или изменённое расписание:
         * перезапускаем только его.
         *
         * Неизменённые расписания продолжают ждать
         * своё время без перезапуска.
         */
        schedules.forEach { schedule ->

            val previous =
                activeSchedules[schedule.id]

            if (
                previous == null ||
                previous != schedule
            ) {

                scheduleJobs
                    .remove(schedule.id)
                    ?.cancel()

                activeSchedules[schedule.id] =
                    schedule

                scheduleJobs[schedule.id] =
                    launchSchedule(
                        schedule = schedule,
                        common = newCommon
                    )
            }
        }

        updateNotification(
            activeSchedules.size
        )
    }

    private fun launchSchedule(
        schedule: ScheduleConfig,
        common: CommonConfig
    ): Job {

        return serviceScope.launch {

            try {

                while (isActive) {

                    val minute =
                        schedule.minute
                            .toInt()
                            .coerceIn(0, 59)

                    val second =
                        schedule.second
                            .toInt()
                            .coerceIn(0, 59)

                    val count =
                        schedule.count
                            .toInt()
                            .coerceAtLeast(1)

                    val intervalValue =
                        schedule.interval
                            .toLong()
                            .coerceAtLeast(0L)

                    val now =
                        LocalDateTime.now()

                    var next =
                        now
                            .withMinute(minute)
                            .withSecond(second)
                            .withNano(0)

                    if (!next.isAfter(now)) {
                        next =
                            next.plusHours(1)
                    }

                    val waitMs =
                        Duration
                            .between(now, next)
                            .toMillis()
                            .coerceAtLeast(0L)

                    Log.d(
                        "SmsSendingService",
                        "Расписание ${schedule.id}: " +
                            "ожидание до $next"
                    )

                    delay(waitMs)

                    val intervalMs =
                        if (common.delayInMs) {
                            intervalValue
                        } else {
                            intervalValue * 1000L
                        }

                    repeat(count) { index ->

                        ensureActive()

                        SmsSender.send(
                            common.phone,
                            "${common.message} ${common.summ}"
                        )

                        SmsSendingState.pulse()

                        Log.d(
                            "SmsSendingService",
                            "Расписание ${schedule.id}: " +
                                "SMS ${index + 1}/$count"
                        )

                        if (index < count - 1) {
                            delay(intervalMs)
                        }
                    }

                    /*
                     * После серии Job не заканчивается.
                     * Она возвращается к началу while
                     * и ждёт следующий час.
                     */
                }

            } catch (e: CancellationException) {

                Log.d(
                    "SmsSendingService",
                    "Расписание ${schedule.id} остановлено"
                )

                throw e

            } catch (e: Exception) {

                Log.e(
                    "SmsSendingService",
                    "Ошибка расписания ${schedule.id}",
                    e
                )
            }
        }
    }

    private fun stopSending() {

        scheduleJobs.values
            .forEach {
                it.cancel()
            }

        scheduleJobs.clear()
        activeSchedules.clear()

        commonConfig = null

        SmsSendingState.running.value =
            false

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.N
        ) {

            stopForeground(
                STOP_FOREGROUND_REMOVE
            )

        } else {

            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    private fun startAsForeground() {

        val notification =
            buildNotification(
                scheduleCount = 0
            )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun updateNotification(
        scheduleCount: Int
    ) {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(scheduleCount)
        )
    }

    private fun buildNotification(
        scheduleCount: Int
    ): Notification {

        val openIntent =
            Intent(
                this,
                MainActivity::class.java
            )

        val openPendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val stopIntent =
            Intent(
                this,
                SmsSendingService::class.java
            ).apply {
                action = ACTION_STOP
            }

        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val builder =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                Notification.Builder(
                    this,
                    CHANNEL_ID
                )

            } else {

                Notification.Builder(this)
            }

        val text =
            if (scheduleCount > 0) {
                "Активных расписаний: $scheduleCount"
            } else {
                "Запуск расписаний"
            }

        return builder
            .setSmallIcon(
                android.R.drawable.ic_dialog_email
            )
            .setContentTitle(
                "SMS Tester работает"
            )
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_delete,
                "Стоп",
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "SMS Tester",
                NotificationManager
                    .IMPORTANCE_LOW
            ).apply {
                description =
                    "Фоновая работа SMS Tester"
            }

        getSystemService(
            NotificationManager::class.java
        ).createNotificationChannel(channel)
    }

    override fun onDestroy() {

        scheduleJobs.values
            .forEach {
                it.cancel()
            }

        scheduleJobs.clear()
        activeSchedules.clear()

        SmsSendingState.running.value =
            false

        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}
