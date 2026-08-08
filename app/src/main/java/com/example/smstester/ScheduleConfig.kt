package com.antteam.smstester

import org.json.JSONArray
import org.json.JSONObject

const val MAX_SCHEDULES = 5

data class ScheduleConfig(
    val id: Long,
    val minute: String = "59",
    val second: String = "52",
    val interval: String = "1",
    val count: String = "5"
)

fun ScheduleConfig.isValidSchedule(): Boolean {
    val minuteValue = minute.toIntOrNull()
    val secondValue = second.toIntOrNull()
    val intervalValue = interval.toLongOrNull()
    val countValue = count.toIntOrNull()

    return minuteValue != null &&
        minuteValue in 0..59 &&
        secondValue != null &&
        secondValue in 0..59 &&
        intervalValue != null &&
        intervalValue >= 0 &&
        countValue != null &&
        countValue >= 1
}

fun schedulesToJson(schedules: List<ScheduleConfig>): String {
    val array = JSONArray()

    schedules.forEach { schedule ->
        array.put(
            JSONObject().apply {
                put("id", schedule.id)
                put("minute", schedule.minute)
                put("second", schedule.second)
                put("interval", schedule.interval)
                put("count", schedule.count)
            }
        )
    }

    return array.toString()
}

fun schedulesFromJson(json: String): List<ScheduleConfig> {
    if (json.isBlank()) {
        return emptyList()
    }

    return try {
        val array = JSONArray(json)

        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)

                add(
                    ScheduleConfig(
                        id = item.optLong("id", index.toLong() + 1L),
                        minute = item.optString("minute", "59"),
                        second = item.optString("second", "52"),
                        interval = item.optString("interval", "1"),
                        count = item.optString("count", "5")
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
