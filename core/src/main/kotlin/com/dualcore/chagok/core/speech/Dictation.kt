package com.dualcore.chagok.core.speech

/** 받아쓴 말을 입력 중인 글에 붙인 결과. clipped면 글자 수 제한 때문에 뒷부분을 잘랐다. */
data class Dictation(val text: String, val added: Boolean, val clipped: Boolean)

/** 받아쓴 말은 이미 쓴 글 뒤에 붙인다. 쓴 글은 지우지 않고, 넘치는 부분만 자른다. */
fun appendHeard(current: String, heard: String, max: Int): Dictation {
    val words = heard.trim()
    if (words.isEmpty()) return Dictation(current, added = false, clipped = false)
    val base = current.trimEnd()
    val joined = if (base.isEmpty()) words else "$base $words"
    val text = joined.take(max)
    return Dictation(text, added = text.length > base.length, clipped = text.length < joined.length)
}
