package io.github.beomhui.drivecompanion.core

/** STOPPED_IN_TRAFFIC must never grant the permissions of PARKED. */
enum class MotionState { UNKNOWN, PARKED, MOVING, STOPPED_IN_TRAFFIC }
enum class AudioState { UNKNOWN, AVAILABLE, NAVIGATION, TRANSIENT_LOSS, LOST }
enum class SpeechMode { DEFER, BRIEF, CONVERSATION }

data class DrivingContext(
    val motion: MotionState = MotionState.UNKNOWN,
    val audio: AudioState = AudioState.UNKNOWN,
    val interactionRestricted: Boolean = true,
    val maneuverImminent: Boolean = false,
)

/** An app policy only. It does not request audio focus or inspect real vehicle state. */
object SpeechPolicy {
    fun mode(context: DrivingContext): SpeechMode = when {
        context.audio != AudioState.AVAILABLE -> SpeechMode.DEFER
        context.motion == MotionState.UNKNOWN -> SpeechMode.DEFER
        context.maneuverImminent -> SpeechMode.DEFER
        context.interactionRestricted -> SpeechMode.BRIEF
        context.motion == MotionState.PARKED -> SpeechMode.CONVERSATION
        else -> SpeechMode.BRIEF
    }
}
