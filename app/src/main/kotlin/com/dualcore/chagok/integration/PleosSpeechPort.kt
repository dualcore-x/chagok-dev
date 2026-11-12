package com.dualcore.chagok.integration

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dualcore.chagok.core.speech.ListenEvent
import com.dualcore.chagok.core.speech.SpeakResult
import com.dualcore.chagok.core.speech.SpeechPort
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitializeListener
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitializeListener

/**
 * Pleos STT·TTS ON_DEVICE 어댑터. 앱 등록과 네트워크가 필요 없다.
 * 재생·인식 전에 오디오 포커스를 요청하고, 포커스를 잃으면 바로 멈춘다. 자동으로 이어 읽지 않는다.
 * 초기화에 실패하면 다음 요청 때 다시 만든다. 호출은 메인 스레드에서 한다.
 */
class PleosSpeechPort(context: Context) : SpeechPort {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setOnAudioFocusChangeListener(::onFocusChange, main)
        .build()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var stt: SpeechToText? = null
    private var sttReady = false
    private var speaking: ((SpeakResult) -> Unit)? = null
    private var listening: ((ListenEvent) -> Unit)? = null
    private val listenTimeout = Runnable { stop() }

    override fun speak(text: String, onResult: (SpeakResult) -> Unit) {
        stop()
        if (!requestFocus()) return onResult(SpeakResult.INTERRUPTED)
        speaking = onResult
        withTts(
            onReady = { it.speak(text) },
            onFailed = { finishSpeaking(SpeakResult.ERROR) },
        )
    }

    override fun listen(onEvent: (ListenEvent) -> Unit) {
        stop()
        if (!requestFocus()) return onEvent(ListenEvent.Error("audio_focus"))
        listening = onEvent
        armListenTimeout()
        withStt(
            onReady = { it.request() },
            onFailed = { reason -> finishListening(ListenEvent.Error(reason)) },
        )
    }

    override fun stop() {
        if (speaking != null) {
            tts?.takeIf { ttsReady }?.stop()
            finishSpeaking(SpeakResult.INTERRUPTED)
        }
        if (listening != null) finishListening(ListenEvent.Cancelled)
    }

    override fun release() {
        stop()
        tts?.takeIf { ttsReady }?.run { removeEventListener(ttsListener); release() }
        stt?.takeIf { sttReady }?.run { removeListener(sttListener); release() }
        tts = null
        ttsReady = false
        stt = null
        sttReady = false
    }

    private fun requestFocus(): Boolean {
        val granted = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) Log.w(TAG, "Audio focus denied")
        return granted
    }

    // 끝나면 이유와 상관없이 반납한다. 잠깐 잃은 포커스도 요청은 남아 있어서, 반납하지 않으면 다른 앱이 끝난 뒤 우리에게 되돌아온다.
    private fun abandonFocus() {
        audio.abandonAudioFocusRequest(focus)
    }

    private fun onFocusChange(change: Int) {
        Log.i(TAG, "Audio focus change $change")
        // CAN_DUCK은 다른 소리와 함께 나가도 된다는 뜻이라 멈추지 않는다.
        // 반납은 stop()이 finishSpeaking/finishListening에서 한다. 여기서 또 반납하면 결과 콜백에서 새로 받은 포커스까지 놓친다.
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
    }

    private fun finishSpeaking(result: SpeakResult) {
        val callback = speaking ?: return
        speaking = null
        abandonFocus()
        callback(result)
    }

    private fun finishListening(event: ListenEvent) {
        val callback = listening ?: return
        listening = null
        main.removeCallbacks(listenTimeout)
        stt?.takeIf { sttReady }?.stop()
        abandonFocus()
        callback(event)
    }

    private fun armListenTimeout() {
        main.removeCallbacks(listenTimeout)
        main.postDelayed(listenTimeout, LISTEN_TIMEOUT_MS)
    }

    // addEventListener()를 initialize() 전에 부르면 SDK 안에서 앱이 죽는다. onSuccess에서 등록한다.
    private fun withTts(onReady: (TextToSpeech) -> Unit, onFailed: () -> Unit) {
        tts?.takeIf { ttsReady }?.let { return onReady(it) }
        val created = TextToSpeech(appContext, TtsMode.ON_DEVICE)
        tts = created
        created.initialize(object : TtsInitializeListener {
            override fun onSuccess() = main.post {
                ttsReady = true
                created.addEventListener(ttsListener)
                created.setLocale(Locale.KOREA)
                if (speaking != null) onReady(created)
            }.let { }

            override fun onFailure(errorType: TtsError) = main.post {
                Log.w(TAG, "TTS init failed $errorType")
                tts = null
                onFailed()
            }.let { }
        })
    }

    private fun withStt(onReady: (SpeechToText) -> Unit, onFailed: (String) -> Unit) {
        stt?.takeIf { sttReady }?.let { return onReady(it) }
        val created = SpeechToText(appContext, SttMode.ON_DEVICE)
        stt = created
        created.initialize(object : SttInitializeListener {
            override fun onSuccess() = main.post {
                sttReady = true
                created.addListener(sttListener)
                created.setLocale(Locale.KOREA)
                if (listening != null) onReady(created)
            }.let { }

            override fun onFailure(errorType: SttError) = main.post {
                Log.w(TAG, "STT init failed $errorType")
                stt = null
                onFailed(errorType.name)
            }.let { }
        })
    }

    private val ttsListener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = post { finishSpeaking(SpeakResult.COMPLETED) }
        override fun onStop() = post { finishSpeaking(SpeakResult.INTERRUPTED) }
        override fun onError(errorType: TtsError) = post {
            Log.w(TAG, "TTS error $errorType")
            finishSpeaking(SpeakResult.ERROR)
        }
    }

    private val sttListener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() = Unit

        override fun onUpdated(stt: String, completed: Boolean) = post {
            if (completed) {
                finishListening(ListenEvent.Final(stt))
            } else {
                armListenTimeout()
                listening?.invoke(ListenEvent.Partial(stt))
            }
        }

        override fun onError(errorType: SttError) = post {
            Log.w(TAG, "STT error $errorType")
            finishListening(ListenEvent.Error(errorType.name))
        }
    }

    private fun post(block: () -> Unit) {
        main.post(block)
    }

    private companion object {
        const val TAG = "PleosSpeech"

        /** 말이 없으면 마이크를 닫는다. SDK는 침묵만으로 인식을 끝내지 않는다. */
        const val LISTEN_TIMEOUT_MS = 15_000L
    }
}
