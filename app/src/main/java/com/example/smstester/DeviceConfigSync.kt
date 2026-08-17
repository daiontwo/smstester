package com.antteam.smstester

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/**
 * Синхронизация персональной конфигурации устройства с Firebase Realtime Database.
 *
 * Структура:
 * devices/{ANDROID_ID}/meta
 * devices/{ANDROID_ID}/config
 */
object DeviceConfigSync {

    private const val DATABASE_URL =
        "https://smstester-29fb6-default-rtdb.europe-west1.firebasedatabase.app"

    data class RemoteDeviceConfig(
        val phone: String? = null,
        val message: String? = null,
        val sum: String? = null,
        val delayInMs: Boolean? = null,
        val autoReply: Boolean? = null,
        val schedules: List<ScheduleConfig>? = null,
        val version: Long? = null
    )

    data class RemoteCommand(
        val commandId: String,
        val running: Boolean?,
        val action: String?,
        val targetPhone: String?
    )

    class Subscription internal constructor(
        private val reference: DatabaseReference,
        private val listener: ValueEventListener
    ) {
        fun stop() {
            reference.removeEventListener(listener)
        }
    }

    private fun database(): FirebaseDatabase =
        FirebaseDatabase.getInstance(DATABASE_URL)

    /**
     * Создаёт/обновляет запись устройства, чтобы оно появилось в админке.
     */
    fun registerDevice(context: Context, deviceId: String) {
        if (deviceId.isBlank()) return

        val appVersion = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName.orEmpty()
        }.getOrDefault("")

        val meta = mapOf<String, Any>(
            "deviceId" to deviceId,
            "lastSeen" to ServerValue.TIMESTAMP,
            "appVersion" to appVersion
        )

        database()
            .getReference("devices")
            .child(deviceId)
            .child("meta")
            .updateChildren(meta)
    }

    /**
     * Слушает конкретную конфигурацию этого устройства.
     * Отсутствующие поля не меняют локальные значения приложения.
     */
    fun listen(
        deviceId: String,
        onConfig: (RemoteDeviceConfig) -> Unit,
        onError: (String) -> Unit = {}
    ): Subscription? {
        if (deviceId.isBlank()) return null

        val reference = database()
            .getReference("devices")
            .child(deviceId)
            .child("config")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val schedulesSnapshot = snapshot.child("schedules")
                val schedules = if (schedulesSnapshot.exists()) {
                    schedulesSnapshot.children.mapIndexedNotNull { index, item ->
                        val minute = item.child("minute").asStringOrNull()
                        val second = item.child("second").asStringOrNull()
                        val interval = item.child("interval").asStringOrNull()
                        val count = item.child("count").asStringOrNull()

                        if (
                            minute == null ||
                            second == null ||
                            interval == null ||
                            count == null
                        ) {
                            null
                        } else {
                            ScheduleConfig(
                                id = item.child("id").getValue(Long::class.java)
                                    ?: (index + 1).toLong(),
                                minute = minute,
                                second = second,
                                interval = interval,
                                count = count
                            )
                        }
                    }
                        .take(MAX_SCHEDULES)
                        .takeIf { it.isNotEmpty() }
                } else {
                    null
                }

                onConfig(
                    RemoteDeviceConfig(
                        phone = snapshot.child("phone").asStringOrNull(),
                        message = snapshot.child("message").asStringOrNull(),
                        sum = snapshot.child("sum").asStringOrNull(),
                        delayInMs = snapshot.child("delayInMs")
                            .getValue(Boolean::class.java),
                        autoReply = snapshot.child("autoReply")
                            .getValue(Boolean::class.java),
                        schedules = schedules,
                        version = snapshot.child("version")
                            .getValue(Long::class.java)
                    )
                )
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }

        reference.addValueEventListener(listener)
        return Subscription(reference, listener)
    }

    fun listenCommands(
        deviceId: String,
        onCommand: (RemoteCommand) -> Unit,
        onError: (String) -> Unit = {}
    ): Subscription? {
        if (deviceId.isBlank()) return null
        val reference = database().getReference("devices").child(deviceId).child("command")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val commandId = snapshot.child("commandId").value?.toString().orEmpty()
                val running = snapshot.child("running").getValue(Boolean::class.java)
                val action = snapshot.child("action").value?.toString()
                val targetPhone = snapshot.child("targetPhone").value?.toString()
                if (commandId.isNotBlank() && (running != null || !action.isNullOrBlank())) {
                    onCommand(RemoteCommand(commandId, running, action, targetPhone))
                }
            }
            override fun onCancelled(error: DatabaseError) = onError(error.message)
        }
        reference.addValueEventListener(listener)
        return Subscription(reference, listener)
    }

    fun reportPhoneNumber(context: Context, phoneNumber: String) {
        val deviceId = LicenseManager.getDeviceId(context)
        if (deviceId.isBlank()) return
        database().getReference("devices").child(deviceId).child("meta").updateChildren(
            mapOf(
                "phoneNumber" to phoneNumber,
                "phoneNumberUpdatedAt" to ServerValue.TIMESTAMP
            )
        )
    }

    fun reportPhoneVerification(context: Context, verified: Boolean, error: String = "") {
        val deviceId = LicenseManager.getDeviceId(context)
        if (deviceId.isBlank()) return
        database().getReference("devices").child(deviceId).child("meta").updateChildren(
            mapOf(
                "phoneVerificationStatus" to if (verified) "verified" else "failed",
                "phoneVerifiedAt" to ServerValue.TIMESTAMP,
                "phoneVerificationError" to error
            )
        )
    }

    fun reportRuntime(deviceId: String, commandId: String, running: Boolean, error: String = "") {
        if (deviceId.isBlank()) return
        database().getReference("devices").child(deviceId).child("runtime").setValue(
            mapOf(
                "commandId" to commandId,
                "running" to running,
                "error" to error,
                "updatedAt" to ServerValue.TIMESTAMP
            )
        )
    }

    private fun DataSnapshot.asStringOrNull(): String? {
        if (!exists()) return null
        return value?.toString()
    }
}
