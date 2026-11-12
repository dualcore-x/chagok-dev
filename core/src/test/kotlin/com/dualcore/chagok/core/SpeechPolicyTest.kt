package com.dualcore.chagok.core

import org.junit.Assert.*
import org.junit.Test

class SpeechPolicyTest {
    private val parked = DrivingContext(MotionState.PARKED, AudioState.AVAILABLE, false)

    @Test fun `navigation and focus loss override even parked conversation`() {
        listOf(AudioState.NAVIGATION, AudioState.TRANSIENT_LOSS, AudioState.LOST, AudioState.UNKNOWN).forEach {
            assertEquals(SpeechMode.DEFER, SpeechPolicy.mode(parked.copy(audio = it)))
        }
    }
    @Test fun `unknown state defers and congestion is not parking`() {
        assertEquals(SpeechMode.DEFER, SpeechPolicy.mode(DrivingContext()))
        assertEquals(SpeechMode.DEFER, SpeechPolicy.mode(parked.copy(motion = MotionState.UNKNOWN)))
        assertEquals(SpeechMode.BRIEF, SpeechPolicy.mode(parked.copy(motion = MotionState.STOPPED_IN_TRAFFIC)))
        assertEquals(SpeechMode.BRIEF, SpeechPolicy.mode(parked.copy(motion = MotionState.MOVING)))
    }
    @Test fun `maneuver defers and restrictions limit even parked conversation`() {
        assertEquals(SpeechMode.DEFER, SpeechPolicy.mode(parked.copy(maneuverImminent = true)))
        assertEquals(SpeechMode.BRIEF, SpeechPolicy.mode(parked.copy(interactionRestricted = true)))
        assertEquals(SpeechMode.CONVERSATION, SpeechPolicy.mode(parked))
    }
}
