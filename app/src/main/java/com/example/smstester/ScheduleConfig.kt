package com.antteam.smstester

import org.json.JSONArray
import org.json.JSONObject

const val MAX_SCHEDULES = 10

data class ScheduleConfig(
    val id: Long,
    val minute: String = "59",
    val second: String = "52",
    val interval: String = "1",
    val count: String = "5"
) {
    fun isValidSchedule(): Boolean {
        val m = minute.toIntOrNull()
        val s = second.toIntOrNull()
        val d = interval.toLongOrNull()
        val c = count.toIntOrNull()
        return m != null && m in 0..59 &&
            s != null && s in 0..59 &&
            d != null && d >= 0L &&
            c != null && c > 0
    }
}

fun schedulesToJson(schedules: List<ScheduleConfig>): String {
    val array = JSONArray()
    schedules.take(MAX_SCHEDULES).forEach { schedule ->
        array.put(
            JSONObject()
                .put("id", schedule.id)
                .put("minute", schedule.minute)
                .put("second", schedule.second)
                .put("interval", schedule.interval)
                .put("count", schedule.count)
        )
    }
    return array.toString()
}

fun schedulesFromJson(json: String?): List<ScheduleConfig> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                add(
                    ScheduleConfig(
                        id = obj.optLong("id", index.toLong() + 1L),
                        minute = obj.optString("minute", "59"),
                        second = obj.optString("second", "52"),
                        interval = obj.optString("interval", "1"),
                        count = obj.optString("count", "5")
                    )
                )
            }
        }.take(MAX_SCHEDULES)
    }.getOrDefault(emptyList())
}
