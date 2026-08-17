package com.antteam.smstester

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

object UssdNumberManager {
    private const val USSD_CODE = "*110*10#"
    private const val PREFS = "ussd_phone_number"
    private const val KEY_PENDING_UNTIL = "pendingUntil"
    private const val WAIT_MILLIS = 5 * 60 * 1000L

    fun request(context: Context, onResult: (String?) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onResult("Нет разрешения CALL_PHONE")
            return
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PENDING_UNTIL, System.currentTimeMillis() + WAIT_MILLIS)
            .apply()

        val telephony = context.getSystemService(TelephonyManager::class.java)
        try {
            telephony.sendUssdRequest(
                USSD_CODE,
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager,
                        request: String,
                        response: CharSequence
                    ) = onResult(null)

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager,
                        request: String,
                        failureCode: Int
                    ) = onResult("USSD завершился с ошибкой $failureCode")
                },
                Handler(Looper.getMainLooper())
            )
        } catch (error: Exception) {
            onResult(error.message ?: "Не удалось выполнить USSD")
        }
    }

    fun consumePhoneNumber(context: Context, sender: String, text: String): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_PENDING_UNTIL, 0L) < System.currentTimeMillis()) return null

        val looksLikeBeeline = sender.contains("beeline", ignoreCase = true) ||
            text.contains("ваш номер", ignoreCase = true) ||
            text.contains("номер телефона", ignoreCase = true)
        if (!looksLikeBeeline) return null

        val match = Regex("(?:\\+7|8)(?:[\\s()\\-]*\\d){10}").find(text) ?: return null
        val digits = match.value.filter(Char::isDigit)
        if (digits.length != 11) return null

        val normalized = "+7" + digits.takeLast(10)
        prefs.edit().remove(KEY_PENDING_UNTIL).apply()
        return normalized
    }
}
