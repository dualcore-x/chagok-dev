package io.github.beomhui.drivecompanion.core.speech

sealed interface ListenEvent {
    data class Partial(val text: String) : ListenEvent
    data class Final(val text: String) : ListenEvent
    data class Error(val reason: String) : ListenEvent
    data object Cancelled : ListenEvent
}

/** INTERRUPTED는 끝까지 읽지 못했다는 뜻이다. 다시 읽을지는 상황을 다시 판단해 앱이 정한다. */
enum class SpeakResult { COMPLETED, INTERRUPTED, ERROR }

/**
 * 음성 입출력 포트. STT는 텍스트 변환까지만 맡고, 저장·유형 판단은 앱이 한다.
 * 콜백은 메인 스레드에서 온다. Final·Error·Cancelled와 SpeakResult는 요청마다 한 번만 온다.
 */
interface SpeechPort {
    fun listen(onEvent: (ListenEvent) -> Unit)
    fun speak(text: String, onResult: (SpeakResult) -> Unit)
    fun stop()
    fun release()
}

/** Pleos 음성 서비스가 없는 기기에서 화면을 개발할 때 쓴다. */
class SimulatedSpeechPort(private val heard: String = "시뮬레이션 음성 입력") : SpeechPort {
    override fun listen(onEvent: (ListenEvent) -> Unit) = onEvent(ListenEvent.Final(heard))
    override fun speak(text: String, onResult: (SpeakResult) -> Unit) = onResult(SpeakResult.COMPLETED)
    override fun stop() = Unit
    override fun release() = Unit
}
