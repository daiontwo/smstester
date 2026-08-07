package com.antteam.smstester

import kotlinx.coroutines.flow.MutableStateFlow

data class IncomingSms(val from: String = "", val text: String = "")

object SmsStore {
    val lastIncoming = MutableStateFlow(IncomingSms())
    var autoReplyEnabled: Boolean = false
    var autoReplyKeyword: String = "8464"
    var autoReplyText: String = "да"
}
