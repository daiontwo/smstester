package com.antteam.smstester

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

data class IncomingSms(
    val from: String = "",
    val text: String = ""
)

data class AutoReplyState(
    val sent: Int = 0,
    val limit: Int = 2,
    val completed: Boolean = false
)

data class AutoReplySuccess(
    val phone: String,
    val timeMillis: Long,
    val sent: Int,
    val limit: Int
)

data class FailureEvent(
    val phone: String,
    val text: String,
    val timeMillis: Long
)

object SmsStore {
    // Событие каждой попытки отправки SMS. UI использует его для зелёной вспышки.
    val smsSendEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    fun notifySmsSendAttempt() {
        smsSendEvents.tryEmit(Unit)
    }

    val warningEvent =
        MutableStateFlow<WarningEvent?>(null)

    fun clearWarningEvent() {
        warningEvent.value = null
    }
    val failureEvent =
        MutableStateFlow<FailureEvent?>(null)

    fun clearFailureEvent() {
        failureEvent.value = null
    }

    data class WarningEvent(
        val phone: String,
        val text: String,
        val timeMillis: Long
    )

    val lastIncoming =
        MutableStateFlow(IncomingSms())

    var autoReplyEnabled: Boolean = false
    var autoReplyKeyword: String = "8464"
    var autoReplyText: String = "да"
    private const val AUTO_REPLY_REQUIRED_TEXT = "Перевод в Таджикистан"

    // Сколько входящих SMS нужно обработать
    var autoReplyLimit: Int = 2
        private set

    // Сколько автоответов уже отправлено
    private var autoReplyCount: Int = 0

    @Synchronized
    fun updateAutoReplyLimit(limit: Int) {
        val newLimit = limit.coerceIn(1, 999)
        autoReplyLimit = newLimit

        val completed = autoReplyCount >= newLimit && autoReplyCount > 0

        if (completed) {
            autoReplyEnabled = false
        }

        autoReplyState.value = AutoReplyState(
            sent = autoReplyCount,
            limit = newLimit,
            completed = completed
        )
    }

    // Состояние для интерфейса
    val autoReplyState =
        MutableStateFlow(
            AutoReplyState(
                sent = 0,
                limit = autoReplyLimit,
                completed = false
            )
        )

    // Событие успешного автоответа
    val autoReplySuccess =
        MutableStateFlow<AutoReplySuccess?>(null)

    @Synchronized
    fun tryAutoReply(from: String, incomingText: String): Boolean {

        if (!autoReplyEnabled) {
            return false
        }

        val senderDigits = from.filter(Char::isDigit)
        val expectedDigits = autoReplyKeyword.filter(Char::isDigit)
        if (expectedDigits.isBlank() || !senderDigits.endsWith(expectedDigits)) {
            return false
        }

        if (!incomingText.contains(AUTO_REPLY_REQUIRED_TEXT, ignoreCase = true)) {
            return false
        }

        if (autoReplyLimit <= 0) {
            return false
        }

        if (autoReplyCount >= autoReplyLimit) {
            autoReplyEnabled = false
            return false
        }

        // На одно входящее SMS — один ответ
        SmsSender.send(
            from,
            autoReplyText
        )

        autoReplyCount++

        val completed =
            autoReplyCount >= autoReplyLimit

        if (completed) {
            autoReplyEnabled = false
        }

        autoReplyState.value =
            AutoReplyState(
                sent = autoReplyCount,
                limit = autoReplyLimit,
                completed = completed
            )

        autoReplySuccess.value =
            AutoReplySuccess(
                phone = from,
                timeMillis = System.currentTimeMillis(),
                sent = autoReplyCount,
                limit = autoReplyLimit
            )

        return true
    }

    @Synchronized
    fun resetAutoReplyCounter() {

        autoReplyCount = 0

        autoReplyState.value =
            AutoReplyState(
                sent = 0,
                limit = autoReplyLimit,
                completed = false
            )

        autoReplySuccess.value = null
    }

    fun clearSuccessEvent() {
        autoReplySuccess.value = null
    }
}
