package com.wkq.localsignage.feature.app.server

import org.junit.Assert.assertEquals
import org.junit.Test

class FleetCommandRoutesTest {
    @Test
    fun exposesEveryPlaybackCommandUsedByTheWebConsole() {
        assertEquals(
            linkedMapOf(
                "play" to "PLAY",
                "previous" to "PREVIOUS",
                "next" to "NEXT",
                "pause" to "PAUSE",
                "stop" to "STOP",
                "volume" to "VOLUME",
                "mute" to "MUTE",
                "unmute" to "UNMUTE"
            ),
            FLEET_COMMAND_ACTIONS
        )
    }
}
