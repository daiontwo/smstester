package com.antteam.smstester

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val from = messages.first().originatingAddress.orEmpty()
        val text = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        SmsStore.lastIncoming.value = IncomingSms(from, text)

        if (SmsStore.autoReplyEnabled &&
            from == SmsStore.autoReplyKeyword
        ) {
            SmsSender.send(from, SmsStore.autoReplyText)
        }
    }
}
