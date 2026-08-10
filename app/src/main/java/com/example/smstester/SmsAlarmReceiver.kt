package com.antteam.smstester

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class SmsAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, SmsSendingService::class.java).apply {
            action = SmsSendingService.ACTION_ALARM
            putExtra(
                SmsSendingService.EXTRA_SCHEDULE_ID,
                intent.getLongExtra(SmsSendingService.EXTRA_SCHEDULE_ID, -1L)
            )
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
