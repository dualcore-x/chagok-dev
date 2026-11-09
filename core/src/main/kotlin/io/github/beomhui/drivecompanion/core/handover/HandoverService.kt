package io.github.beomhui.drivecompanion.core.handover

import io.github.beomhui.drivecompanion.core.vehicle.VehicleSnapshot
import java.util.UUID

/** 화면에 그대로 보여줄 수 있는 메시지를 담는다. */
open class HandoverException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ActiveSessionException(val session: DriveSession, val driver: Driver?) :
    HandoverException("${driver?.name ?: "다른 운전자"}님의 사용이 아직 반납되지 않았어요.")

/** id는 화면이 작성 시작 때 한 번 만든다. 같은 id로 다시 저장하면 중복으로 만들지 않는다. */
data class NoteDraft(
    val id: String,
    val anchor: NoteAnchor,
    val kind: NoteKind,
    val text: String,
    val targetPlaceId: String? = null,
    val importance: Importance = Importance.NORMAL,
    val snapshot: VehicleSnapshot? = null,
)

/**
 * 차량 1대의 인계 규칙. 명령마다 저장소에 먼저 저장하고, 저장에 실패하면 상태를 바꾸지 않는다.
 * 확인(acknowledge)과 해결(resolve)은 별개이며, 확인은 운전자별로 남는다.
 */
class HandoverService(
    val fleet: Fleet,
    private val store: HandoverStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private var state: HandoverState = store.load()

    @Synchronized fun state(): HandoverState = state

    @Synchronized fun activeSession(): DriveSession? = state.activeSession()

    @Synchronized
    fun startSession(driverId: String, takeOver: Boolean = false): DriveSession {
        val driver = fleet.driver(driverId) ?: throw HandoverException("등록되지 않은 운전자예요.")
        val current = state.activeSession()
        if (current != null) {
            if (current.driverId == driver.id) return current
            if (!takeOver) throw ActiveSessionException(current, fleet.driver(current.driverId))
        }
        val now = clock()
        val sessions = state.sessions.map {
            if (it.id == current?.id) it.copy(returnedAt = now, endedByTakeover = true) else it
        }
        val session = DriveSession(newId(), fleet.vehicle.id, driver.id, now)
        commit(state.copy(sessions = sessions + session))
        return session
    }

    @Synchronized
    fun returnVehicle(): DriveSession {
        val session = requireSession()
        val returned = session.copy(returnedAt = clock())
        commit(state.copy(sessions = state.sessions.map { if (it.id == session.id) returned else it }))
        return returned
    }

    @Synchronized
    fun createNote(draft: NoteDraft): Note {
        val session = requireSession()
        val text = validText(draft.text)
        state.notes.firstOrNull { it.id == draft.id }?.let { existing ->
            if (existing.authorId == session.driverId && existing.text == text) return existing
            throw HandoverException("이미 저장된 인계예요.")
        }
        validPlace(draft.anchor, draft.targetPlaceId)
        val now = clock()
        val note = Note(
            id = draft.id,
            vehicleId = fleet.vehicle.id,
            authorId = session.driverId,
            createdAt = now,
            contentUpdatedAt = now,
            anchor = draft.anchor,
            kind = draft.kind,
            text = text,
            targetPlaceId = draft.targetPlaceId,
            importance = draft.importance,
            taskState = if (draft.kind == NoteKind.TASK) TaskState.OPEN else null,
            referenceState = if (draft.kind == NoteKind.REFERENCE) ReferenceState.ACTIVE else null,
            snapshot = draft.snapshot,
        )
        commit(state.copy(notes = state.notes + note).acknowledgedBy(session, note.id, now))
        return note
    }

    @Synchronized
    fun editNote(noteId: String, text: String, importance: Importance, targetPlaceId: String?): Note {
        val session = requireSession()
        val note = requireNote(noteId)
        if (!note.isActive) throw HandoverException("끝났거나 내린 인계는 고칠 수 없어요.")
        val cleaned = validText(text)
        validPlace(note.anchor, targetPlaceId)
        if (cleaned == note.text && importance == note.importance && targetPlaceId == note.targetPlaceId) return note
        val now = clock()
        val edited = note.copy(text = cleaned, importance = importance, targetPlaceId = targetPlaceId, contentUpdatedAt = now)
        commit(state.replace(edited).acknowledgedBy(session, noteId, now))
        return edited
    }

    /** 화면이나 음성으로 전달했다는 기록. 확인으로 취급하지 않는다. */
    @Synchronized
    fun markPresented(noteIds: Collection<String>) {
        val session = requireSession()
        val now = clock()
        var next = state
        noteIds.distinct().forEach { noteId ->
            requireNote(noteId)
            val receipt = next.receipts.firstOrNull { it.noteId == noteId && it.sessionId == session.id }
            if (receipt?.presentedAt == null) {
                next = next.upsertReceipt((receipt ?: Receipt(noteId, session.driverId, session.id)).copy(presentedAt = now))
            }
        }
        if (next != state) commit(next)
    }

    @Synchronized
    fun acknowledge(noteId: String) {
        val session = requireSession()
        val note = requireNote(noteId)
        val last = state.lastAcknowledgedAt(noteId, session.driverId)
        if (last != null && last >= note.contentUpdatedAt) return
        commit(state.acknowledgedBy(session, noteId, clock()))
    }

    @Synchronized
    fun resolve(noteId: String, comment: String? = null): Note {
        val session = requireSession()
        val note = requireNote(noteId)
        if (note.kind != NoteKind.TASK) throw HandoverException("참고 정보는 완료 대신 정보 내리기로 정리해요.")
        if (note.taskState == TaskState.DONE) return note
        val now = clock()
        val done = note.copy(taskState = TaskState.DONE)
        commit(
            state.replace(done)
                .withResolution(noteId, session.driverId, ResolutionAction.DONE, now, validComment(comment))
                .acknowledgedBy(session, noteId, now),
        )
        return done
    }

    /** 다시 연 일은 내용이 바뀐 것으로 보고 모든 운전자에게 다시 알린다. */
    @Synchronized
    fun reopen(noteId: String, comment: String? = null): Note {
        val session = requireSession()
        val note = requireNote(noteId)
        if (note.kind != NoteKind.TASK) throw HandoverException("해야 할 일만 다시 열 수 있어요.")
        if (note.taskState == TaskState.OPEN) return note
        val now = clock()
        val reopened = note.copy(taskState = TaskState.OPEN, contentUpdatedAt = now)
        commit(
            state.replace(reopened)
                .withResolution(noteId, session.driverId, ResolutionAction.REOPEN, now, validComment(comment))
                .acknowledgedBy(session, noteId, now),
        )
        return reopened
    }

    @Synchronized
    fun archive(noteId: String): Note {
        val session = requireSession()
        val note = requireNote(noteId)
        if (note.kind != NoteKind.REFERENCE) throw HandoverException("해야 할 일은 내리지 않고 완료로 처리해요.")
        if (note.referenceState == ReferenceState.ARCHIVED) return note
        val archived = note.copy(referenceState = ReferenceState.ARCHIVED)
        commit(state.replace(archived).withResolution(noteId, session.driverId, ResolutionAction.ARCHIVE, clock(), null))
        return archived
    }

    @Synchronized
    fun briefing(destination: Place? = null): Briefing {
        val session = requireSession()
        val driver = fleet.driver(session.driverId) ?: throw HandoverException("등록되지 않은 운전자예요.")
        return BriefingBuilder.build(state, fleet, driver, destination)
    }

    /** 최근 완료한 일. 다시 열기와 처리 이력 표시에 쓴다. */
    @Synchronized
    fun recentlyDone(limit: Int = 5): List<DoneEntry> = state.notes
        .filter { it.taskState == TaskState.DONE }
        .mapNotNull { note ->
            state.resolutions.lastOrNull { it.noteId == note.id && it.action == ResolutionAction.DONE }
                ?.let { DoneEntry(note, it, fleet.driver(it.actorId)) }
        }
        .sortedByDescending { it.resolution.performedAt }
        .take(limit)

    /** 개발 도구 전용. 저장된 인계 기록을 모두 지운다. */
    @Synchronized
    fun clearAll() = commit(HandoverState())

    private fun commit(next: HandoverState) {
        try {
            store.save(next)
        } catch (e: Exception) {
            throw HandoverException("기록을 저장하지 못했어요. 다시 시도해주세요.", e)
        }
        state = next
    }

    private fun requireSession() = state.activeSession() ?: throw HandoverException("먼저 운전자를 선택해 사용을 시작해주세요.")

    private fun requireNote(noteId: String) = state.notes.firstOrNull { it.id == noteId }
        ?: throw HandoverException("인계를 찾을 수 없어요.")

    private fun validText(text: String): String {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) throw HandoverException("내용을 입력해주세요.")
        if (cleaned.length > MAX_TEXT) throw HandoverException("${MAX_TEXT}자 이내로 줄여주세요.")
        return cleaned
    }

    private fun validComment(comment: String?): String? {
        val cleaned = comment?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (cleaned.length > MAX_COMMENT) throw HandoverException("처리 메모는 ${MAX_COMMENT}자 이내로 줄여주세요.")
        return cleaned
    }

    private fun validPlace(anchor: NoteAnchor, placeId: String?) {
        if (placeId != null && fleet.place(placeId) == null) throw HandoverException("등록되지 않은 장소예요.")
        if (anchor == NoteAnchor.PLACE && placeId == null) throw HandoverException("장소를 선택해주세요.")
    }

    private fun HandoverState.activeSession() =
        sessions.lastOrNull { it.vehicleId == fleet.vehicle.id && it.returnedAt == null }

    private fun HandoverState.replace(note: Note) = copy(notes = notes.map { if (it.id == note.id) note else it })

    private fun HandoverState.upsertReceipt(receipt: Receipt): HandoverState {
        val exists = receipts.any { it.noteId == receipt.noteId && it.sessionId == receipt.sessionId }
        return copy(
            receipts = if (exists) {
                receipts.map { if (it.noteId == receipt.noteId && it.sessionId == receipt.sessionId) receipt else it }
            } else {
                receipts + receipt
            },
        )
    }

    private fun HandoverState.acknowledgedBy(session: DriveSession, noteId: String, at: Long): HandoverState {
        val receipt = receipts.firstOrNull { it.noteId == noteId && it.sessionId == session.id }
            ?: Receipt(noteId, session.driverId, session.id)
        return upsertReceipt(receipt.copy(presentedAt = receipt.presentedAt ?: at, acknowledgedAt = at))
    }

    private fun HandoverState.withResolution(
        noteId: String, actorId: String, action: ResolutionAction, at: Long, comment: String?,
    ) = copy(resolutions = resolutions + Resolution(newId(), noteId, actorId, action, at, comment))

    companion object {
        const val MAX_TEXT = 300
        const val MAX_COMMENT = 200
    }
}
