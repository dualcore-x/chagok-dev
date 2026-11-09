package io.github.beomhui.drivecompanion.core.handover

import kotlinx.serialization.json.Json

interface HandoverStore {
    fun load(): HandoverState
    fun save(state: HandoverState)
}

class InMemoryHandoverStore(private var state: HandoverState = HandoverState()) : HandoverStore {
    var saveCount = 0
        private set

    override fun load() = state
    override fun save(state: HandoverState) {
        this.state = state
        saveCount++
    }
}

object HandoverJson {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(state: HandoverState): String = json.encodeToString(HandoverState.serializer(), state)

    /** 모델 불변식 위반이나 더 새로운 스키마는 예외로 알린다. 조용히 빈 기록으로 바꾸지 않는다. */
    fun decode(text: String): HandoverState {
        val state = json.decodeFromString(HandoverState.serializer(), text)
        require(state.schemaVersion <= HandoverState.CURRENT_SCHEMA) { "지원하지 않는 기록 형식입니다: ${state.schemaVersion}" }
        return state
    }
}
