package com.antteam.smstester

import kotlinx.coroutines.flow.MutableStateFlow

data class IncomingSms(
    val from: String = "",
    val text: String = ""
)

object SmsStore {

    val lastIncoming =
        MutableStateFlow(
            IncomingSms()
        )

    var autoReplyEnabled: Boolean =
        false

    var autoReplyKeyword: String =
        "8464"

    var autoReplyText: String =
        "да"

    // Максимум входящих SMS, на которые можно ответить.
    var autoReplyLimit: Int =
        1

    // Сколько подходящих входящих SMS уже получили ответ.
    var autoReplyUsed: Int =
        0

    fun resetAutoReplyCounter() {
        autoReplyUsed = 0
    }

    fun canAutoReply(): Boolean {
        return autoReplyUsed < autoReplyLimit
    }

    fun markAutoReplyUsed() {
        autoReplyUsed++
    }
}
