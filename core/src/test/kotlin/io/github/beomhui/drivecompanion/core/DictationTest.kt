package io.github.beomhui.drivecompanion.core

import io.github.beomhui.drivecompanion.core.speech.Dictation
import io.github.beomhui.drivecompanion.core.speech.appendHeard
import org.junit.Assert.*
import org.junit.Test

class DictationTest {
    @Test fun `heard words go after what was typed`() {
        assertEquals(Dictation("트렁크에 샘플 박스 2개가 있어요", added = true, clipped = false), appendHeard("", " 트렁크에 샘플 박스 2개가 있어요 ", 300))
        assertEquals("충전 케이블은 B1 경비실에 있어요", appendHeard("충전 케이블은 ", "B1 경비실에 있어요", 300).text)
    }

    @Test fun `silence leaves the text alone`() {
        assertEquals(Dictation("와이퍼 소리", added = false, clipped = false), appendHeard("와이퍼 소리", "  ", 300))
    }

    @Test fun `over the limit keeps typed text and clips the rest`() {
        val result = appendHeard("가".repeat(8), "나다라마", 10)
        assertEquals("가".repeat(8) + " 나", result.text)
        assertTrue(result.added && result.clipped)
        assertFalse("이미 꽉 차면 붙이지 못한다", appendHeard("가".repeat(10), "나", 10).added)
    }
}
