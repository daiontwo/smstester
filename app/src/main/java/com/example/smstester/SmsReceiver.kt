package com.antteam.smstester

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
            messages
                .first()
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

        val matchesSender =
            from == SmsStore.autoReplyKeyword

        if (
            SmsStore.autoReplyEnabled &&
            matchesSender &&
            SmsStore.canAutoReply()
        ) {

            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {

                try {

                    val licenseActive =
                        LicenseManager.validateDevice(
                            context.applicationContext
                        )

                    if (licenseActive) {

                        SmsSender.send(
                            from,
                            SmsStore.autoReplyText
                        )

                        SmsStore.markAutoReplyUsed()
                        SmsSendingState.pulse()

                        Log.d(
                            "SmsReceiver",
                            "Автоответ отправлен: " +
                                    "${SmsStore.autoReplyUsed}/" +
                                    "${SmsStore.autoReplyLimit}"
                        )

                    } else {

                        Log.w(
                            "SmsReceiver",
                            "Автоответ запрещён: лицензия отключена"
                        )

                        SmsStore.autoReplyEnabled = false
                    }

                } catch (e: Exception) {

                    /*
                     * Если Firebase недоступен —
                     * ничего не отправляем.
                     *
                     * Для лицензирования это безопаснее,
                     * чем разрешать SMS без проверки.
                     */
                    Log.e(
                        "SmsReceiver",
                        "Не удалось проверить лицензию",
                        e
                    )

                } finally {

                    pendingResult.finish()
                }
            }
        } else if (
            SmsStore.autoReplyEnabled &&
            matchesSender &&
            !SmsStore.canAutoReply()
        ) {

            Log.d(
                "SmsReceiver",
                "Лимит автоответов исчерпан: " +
                    "${SmsStore.autoReplyUsed}/" +
                    "${SmsStore.autoReplyLimit}"
            )
        }
    }
}
