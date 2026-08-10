package com.antteam.smstester

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        if (
            intent.action !=
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        ) {
            return
        }

        val messages =
            Telephony.Sms.Intents
                .getMessagesFromIntent(intent)

        if (messages.isEmpty()) {
            return
        }

        val from =
            messages.first()
                .originatingAddress
                .orEmpty()

        val text =
            messages.joinToString(
                separator = ""
            ) {
                it.messageBody.orEmpty()
            }

        SmsStore.lastIncoming.value =
            IncomingSms(
                from = from,
                text = text
            )

        /*
         * ---------------------------------------------
         * НЕГАТИВНЫЙ ОТВЕТ ОТ 7878
         * ---------------------------------------------
         */

        val isFailureMessage =
            from.trim() == "7878" &&
                    (
                            text.contains(
                                "операция отклонена",
                                ignoreCase = true
                            ) ||
                                    text.contains("0611")
                            )

        if (isFailureMessage) {

            // Выключаем автоответ
            SmsStore.autoReplyEnabled = false

            // Сохраняем событие ошибки для UI
            SmsStore.failureEvent.value =
                FailureEvent(
                    phone = from,
                    text = text,
                    timeMillis =
                        System.currentTimeMillis()
                )

            // Полностью останавливаем автоматическую отправку.
            // SmsSendingService сам отменит AlarmManager.
            val stopIntent =
                Intent(
                    context,
                    SmsSendingService::class.java
                ).apply {
                    action =
                        SmsSendingService.ACTION_STOP
                }

            context.startService(stopIntent)

            // Никаких других действий с этим SMS
            return
        }

        /*
 * ---------------------------------------------
 * 8464 ПРИШЁЛ, НО АВТООТВЕТ ВЫКЛЮЧЕН
 * ---------------------------------------------
 */

        if (
            from.trim() == "8464" &&
            !SmsStore.autoReplyEnabled
        ) {

            SmsStore.warningEvent.value =
                SmsStore.WarningEvent(
                    phone = from,
                    text = text,
                    timeMillis = System.currentTimeMillis()
                )

            return
        }

        /*
         * ---------------------------------------------
         * ОБЫЧНЫЙ АВТООТВЕТ
         * ---------------------------------------------
         */

        SmsStore.tryAutoReply(from)
    }
}