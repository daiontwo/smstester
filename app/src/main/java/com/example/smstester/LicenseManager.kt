package com.antteam.smstester

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

object LicenseManager {

    private const val PREFS = "license_prefs"
    private const val KEY_TOKEN = "license_token"

    private val functions =
        FirebaseFunctions.getInstance(
            "europe-west1"
        )

    fun normalizeToken(value: String): String = value
        .uppercase()
        .replace(Regex("[^A-Z0-9]"), "")
        .trim()

    fun getDeviceId(context: Context): String {

        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown-device"
    }

    fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    fun saveToken(
        context: Context,
        token: String
    ) {

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_TOKEN,
                token
            )
            .apply()
    }

    fun getSavedToken(
        context: Context
    ): String? {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_TOKEN,
                null
            )
    }

    fun clearToken(
        context: Context
    ) {

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(KEY_TOKEN)
            .apply()
    }

    suspend fun activateDevice(
        context: Context,
        token: String
    ): Boolean {

        val data =
            hashMapOf(
                "token" to normalizeToken(token),
                "deviceId" to getDeviceId(context),
                "deviceName" to getDeviceName()
            )

        val result =
            functions
                .getHttpsCallable(
                    "activateDevice"
                )
                .call(data)
                .await()

        val response =
            result.data as? Map<*, *>
                ?: return false

        val status =
            response["status"]
                ?.toString()

        if (
            status == "ACTIVE"
        ) {

            saveToken(
                context,
                normalizeToken(token)
            )

            return true
        }

        return false
    }

    suspend fun validateDevice(
        context: Context
    ): Boolean {

        val token =
            getSavedToken(context)
                ?: return false

        val data =
            hashMapOf(
                "token" to token,
                "deviceId" to getDeviceId(context)
            )

        val result =
            functions
                .getHttpsCallable(
                    "validateDevice"
                )
                .call(data)
                .await()

        val response =
            result.data as? Map<*, *>
                ?: throw IllegalStateException("INVALID_VALIDATE_RESPONSE")

        return when (response["active"]) {
            true -> true
            false -> false
            else -> throw IllegalStateException("INVALID_VALIDATE_RESPONSE")
        }
    }
}
