package com.wkq.localsignage.feature.app.model

/** Local weekly schedule decision model. Weekday values follow Calendar: Sunday = 1 through Saturday = 7. */
data class PlaylistSchedule(
    val id: String,
    val playlistId: String,
    val weekdays: Set<Int>,
    val startMinute: Int,
    val endMinute: Int,
    val priority: Int = 0,
    val enabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

object PlaylistSchedulePolicy {
    private const val MINUTES_PER_DAY = 24 * 60

    fun normalizeWeekdays(values: Collection<Int>): Set<Int> = values.filter { it in 1..7 }.toSet()

    fun normalizeMinute(value: Int): Int = value.coerceIn(0, MINUTES_PER_DAY - 1)

    fun matches(schedule: PlaylistSchedule, weekday: Int, minuteOfDay: Int): Boolean {
        if (!schedule.enabled || weekday !in 1..7 || schedule.weekdays.isEmpty()) return false
        val minute = normalizeMinute(minuteOfDay)
        val start = normalizeMinute(schedule.startMinute)
        val end = normalizeMinute(schedule.endMinute)
        if (start == end) return weekday in schedule.weekdays
        if (start < end) return weekday in schedule.weekdays && minute in start until end
        if (minute >= start) return weekday in schedule.weekdays
        val previousDay = if (weekday == 1) 7 else weekday - 1
        return minute < end && previousDay in schedule.weekdays
    }

    fun active(schedules: Collection<PlaylistSchedule>, weekday: Int, minuteOfDay: Int): PlaylistSchedule? =
        schedules.filter { matches(it, weekday, minuteOfDay) }
            .maxWithOrNull(compareBy<PlaylistSchedule> { it.priority }.thenBy { it.updatedAt }.thenBy { it.id })
}
