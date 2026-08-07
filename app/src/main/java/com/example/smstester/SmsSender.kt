package com.antteam.smstester

import android.telephony.SmsManager

object SmsSender {
    fun send(phone: String, text: String) {
        if (phone.isBlank() || text.isBlank()) return
        SmsManager.getDefault().sendTextMessage(phone, null, text, null, null)
    }
}
