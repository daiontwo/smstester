package com.antteam.smstester

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!LicenseManager.getSavedToken(context).isNullOrBlank()) {
            // Некоторые прошивки временно запрещают запуск FGS сразу после boot.
            // В таком случае сервис запустится при следующем открытии приложения.
            runCatching { RemoteCommandService.start(context) }
        }
    }
}
