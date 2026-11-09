package io.github.beomhui.drivecompanion.data.local

import android.util.AtomicFile
import io.github.beomhui.drivecompanion.core.handover.HandoverJson
import io.github.beomhui.drivecompanion.core.handover.HandoverState
import io.github.beomhui.drivecompanion.core.handover.HandoverStore
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** 앱 내부 저장소의 JSON 파일 하나. 쓰기 도중 종료돼도 이전 기록이 남도록 AtomicFile로 교체한다. */
class FileHandoverStore(
    private val file: File,
    private val clock: () -> Long = System::currentTimeMillis,
) : HandoverStore {
    private val atomic = AtomicFile(file)

    /** 읽을 수 없는 기록을 옆으로 옮기고 새로 시작했다면 그 파일. 화면에 알린다. */
    var quarantined: File? = null
        private set

    override fun load(): HandoverState {
        val bytes = try {
            atomic.readFully()
        } catch (e: FileNotFoundException) {
            return HandoverState()
        }
        return try {
            HandoverJson.decode(bytes.toString(Charsets.UTF_8))
        } catch (e: IllegalArgumentException) {
            val target = File(file.parentFile, "${file.nameWithoutExtension}.unreadable-${clock()}.json")
            if (!file.renameTo(target)) throw IOException("읽을 수 없는 기록을 옮기지 못했습니다.", e)
            quarantined = target
            HandoverState()
        }
    }

    override fun save(state: HandoverState) {
        val out = atomic.startWrite()
        try {
            out.write(HandoverJson.encode(state).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }
}
