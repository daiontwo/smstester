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
    val phone: String = "7878",
    val message: String = "",
    val summ: String = "14700",
    val darkTheme: Boolean = false,
    val schedules: List<ScheduleConfig> = listOf(
        ScheduleConfig(id = 1L)
    ),
    val delayInMs: Boolean = false,
    val autoReply: Boolean = false,
    val keyword: String = "8464",
    val replyText: String = "да",
    val autoReplyCount: String = "1" // теперь это лимит входящих SMS
)

object SettingsDataStore {

    private val PHONE = stringPreferencesKey("phone")
    private val MESSAGE = stringPreferencesKey("message")
    private val SUMM = stringPreferencesKey("summ")
    private val DARK_THEME = booleanPreferencesKey("dark_theme")

    private val SCHEDULES_JSON = stringPreferencesKey("schedules_json")
    private val DELAY_IN_MS = booleanPreferencesKey("delay_in_ms")

    private val AUTO_REPLY = booleanPreferencesKey("auto_reply")
    private val KEYWORD = stringPreferencesKey("keyword")
    private val REPLY_TEXT = stringPreferencesKey("reply_text")
    private val AUTO_REPLY_COUNT = stringPreferencesKey("auto_reply_count")

    /*
     * Старые ключи оставлены только для автоматической миграции
     * с версии приложения, где было одно расписание.
     */
    private val LEGACY_SCHEDULE_MINUTE =
        stringPreferencesKey("schedule_minute")

    private val LEGACY_SCHEDULE_SECOND =
        stringPreferencesKey("schedule_second")

    private val LEGACY_SMS_DELAY =
        stringPreferencesKey("sms_delay")

    private val LEGACY_SEND_LIMIT =
        stringPreferencesKey("send_limit")

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
            preferences[SUMM] = settings.summ
            preferences[DARK_THEME] = settings.darkTheme

            preferences[SCHEDULES_JSON] =
                schedulesToJson(settings.schedules)

            preferences[DELAY_IN_MS] = settings.delayInMs

            preferences[AUTO_REPLY] = settings.autoReply
            preferences[KEYWORD] = settings.keyword
            preferences[REPLY_TEXT] = settings.replyText
            preferences[AUTO_REPLY_COUNT] = settings.autoReplyCount
        }
    }

    private fun Preferences.toAppSettings(): AppSettings {

        val savedSchedules =
            this[SCHEDULES_JSON]
                ?.let(::schedulesFromJson)
                .orEmpty()

        /*
         * Если нового списка ещё нет, берём старые 4 поля
         * и превращаем их в первое расписание.
         */
        val schedules =
            if (savedSchedules.isNotEmpty()) {

                savedSchedules.take(MAX_SCHEDULES)

            } else {

                listOf(
                    ScheduleConfig(
                        id = 1L,
                        minute =
                            this[LEGACY_SCHEDULE_MINUTE]
                                ?: "59",
                        second =
                            this[LEGACY_SCHEDULE_SECOND]
                                ?: "52",
                        interval =
                            this[LEGACY_SMS_DELAY]
                                ?: "1",
                        count =
                            this[LEGACY_SEND_LIMIT]
                                ?: "5"
                    )
                )
            }

        return AppSettings(
            phone =
                this[PHONE]
                    ?.ifBlank { "7878" }
                    ?: "7878",

            message =
                this[MESSAGE]
                    ?: "",

            summ =
                this[SUMM]
                    ?: "14700",

            darkTheme =
                this[DARK_THEME]
                    ?: false,

            schedules = schedules,

            delayInMs =
                this[DELAY_IN_MS]
                    ?: false,

            autoReply =
                this[AUTO_REPLY]
                    ?: false,

            keyword =
                this[KEYWORD]
                    ?: "8464",

            replyText =
                this[REPLY_TEXT]
                    ?: "да",

            autoReplyCount =
                this[AUTO_REPLY_COUNT]
                    ?.ifBlank { "1" }
                    ?: "1"
        )
    }
}
