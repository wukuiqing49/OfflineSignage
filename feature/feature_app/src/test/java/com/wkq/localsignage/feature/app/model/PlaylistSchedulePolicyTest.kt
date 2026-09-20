package com.wkq.localsignage.feature.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSchedulePolicyTest {
    @Test
    fun matchesWithinSameDayWindow() {
        val schedule = PlaylistSchedule("morning", "playlist", setOf(2), 9 * 60, 12 * 60)

        assertTrue(PlaylistSchedulePolicy.matches(schedule, 2, 10 * 60))
        assertFalse(PlaylistSchedulePolicy.matches(schedule, 2, 12 * 60))
        assertFalse(PlaylistSchedulePolicy.matches(schedule, 3, 10 * 60))
    }

    @Test
    fun carriesCrossMidnightWindowIntoFollowingDay() {
        val schedule = PlaylistSchedule("night", "playlist", setOf(2), 22 * 60, 6 * 60)

        assertTrue(PlaylistSchedulePolicy.matches(schedule, 2, 23 * 60))
        assertTrue(PlaylistSchedulePolicy.matches(schedule, 3, 5 * 60 + 30))
        assertFalse(PlaylistSchedulePolicy.matches(schedule, 3, 6 * 60))
    }

    @Test
    fun selectsHighestPriorityThenNewestSchedule() {
        val older = PlaylistSchedule("older", "one", setOf(2), 0, 0, priority = 3, updatedAt = 1)
        val newer = PlaylistSchedule("newer", "two", setOf(2), 0, 0, priority = 3, updatedAt = 2)
        val lower = PlaylistSchedule("lower", "three", setOf(2), 0, 0, priority = 1, updatedAt = 99)

        assertEquals("newer", PlaylistSchedulePolicy.active(listOf(older, newer, lower), 2, 300)?.id)
    }
}
