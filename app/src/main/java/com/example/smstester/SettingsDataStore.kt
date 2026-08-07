package com.antteam.smstester

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.smsTesterDataStore by preferencesDataStore(
    name = "sms_tester_settings"
)

data class AppSettings(
    val phone: String = "",
    val message: String = "",
    val sendLimitText: String = "5",
    val summ: String = "14700",
    val darkTheme: Boolean = false,
    val scheduleMinute: String = "59",
    val scheduleSecond: String = "52",
    val smsDelayValue: String = "1",
    val delayInMs: Boolean = false,
    val autoReply: Boolean = false,
    val keyword: String = "8464",
    val replyText: String = "да"
)

object SettingsDataStore {

    private val PHONE = stringPreferencesKey("phone")
    private val MESSAGE = stringPreferencesKey("message")
    private val SEND_LIMIT = stringPreferencesKey("send_limit")
    private val SUMM = stringPreferencesKey("summ")

    private val DARK_THEME = booleanPreferencesKey("dark_theme")

    private val SCHEDULE_MINUTE = stringPreferencesKey("schedule_minute")
    private val SCHEDULE_SECOND = stringPreferencesKey("schedule_second")

    private val SMS_DELAY = stringPreferencesKey("sms_delay")
    private val DELAY_IN_MS = booleanPreferencesKey("delay_in_ms")

    private val AUTO_REPLY = booleanPreferencesKey("auto_reply")
    private val KEYWORD = stringPreferencesKey("keyword")
    private val REPLY_TEXT = stringPreferencesKey("reply_text")

    fun settingsFlow(context: Context): Flow<AppSettings> {
        return context.applicationContext.smsTesterDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences.toAppSettings()
            }
    }

    suspend fun save(
        context: Context,
        settings: AppSettings
    ) {
        context.applicationContext.smsTesterDataStore.edit { preferences ->

            preferences[PHONE] = settings.phone
            preferences[MESSAGE] = settings.message
            preferences[SEND_LIMIT] = settings.sendLimitText
            preferences[SUMM] = settings.summ

            preferences[DARK_THEME] = settings.darkTheme

            preferences[SCHEDULE_MINUTE] = settings.scheduleMinute
            preferences[SCHEDULE_SECOND] = settings.scheduleSecond

            preferences[SMS_DELAY] = settings.smsDelayValue
            preferences[DELAY_IN_MS] = settings.delayInMs

            preferences[AUTO_REPLY] = settings.autoReply
            preferences[KEYWORD] = settings.keyword
            preferences[REPLY_TEXT] = settings.replyText
        }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        return AppSettings(
            phone = this[PHONE] ?: "",
            message = this[MESSAGE] ?: "",
            sendLimitText = this[SEND_LIMIT] ?: "5",
            summ = this[SUMM] ?: "14700",
            darkTheme = this[DARK_THEME] ?: false,
            scheduleMinute = this[SCHEDULE_MINUTE] ?: "59",
            scheduleSecond = this[SCHEDULE_SECOND] ?: "52",
            smsDelayValue = this[SMS_DELAY] ?: "1",
            delayInMs = this[DELAY_IN_MS] ?: false,
            autoReply = this[AUTO_REPLY] ?: false,
            keyword = this[KEYWORD] ?: "8464",
            replyText = this[REPLY_TEXT] ?: "да"
        )
    }
}