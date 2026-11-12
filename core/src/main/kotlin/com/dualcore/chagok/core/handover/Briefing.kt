package com.dualcore.chagok.core.handover

/**
 * NEW: 이 운전자가 아직 확인하지 않음. UPDATED: 확인 이후 내용이 바뀜.
 * CARRIED_OVER: 확인했지만 아직 끝나지 않은 중요한 일. ACKNOWLEDGED: 그 밖의 진행 중 항목.
 */
enum class BriefingReason { NEW, UPDATED, CARRIED_OVER, ACKNOWLEDGED }

data class BriefingItem(val note: Note, val reason: BriefingReason, val author: Driver?, val place: Place?) {
    val needsAcknowledgement: Boolean get() = reason == BriefingReason.NEW || reason == BriefingReason.UPDATED
}

/** atDestination은 이번 목적지와 연결된 인계다. 다른 목록과 겹치지 않고 맨 앞에 온다. */
data class Briefing(
    val driver: Driver,
    val attention: List<BriefingItem>,
    val carriedOver: List<BriefingItem>,
    val others: List<BriefingItem>,
    val destination: Place? = null,
    val atDestination: List<BriefingItem> = emptyList(),
) {
    val summary: String get() {
        val all = atDestination + attention + carriedOver
        val fresh = all.filter { it.needsAcknowledgement }
        val carried = all.count { it.reason == BriefingReason.CARRIED_OVER }
        val parts = mutableListOf<String>()
        if (destination != null && atDestination.isNotEmpty()) {
            val tasks = atDestination.count { it.note.kind == NoteKind.TASK }
            val references = atDestination.size - tasks
            parts += when {
                tasks > 0 && references > 0 -> "이번 목적지 ${destination.name}에서 할 일 ${tasks}건, 참고할 정보 ${references}건이 있어요."
                tasks > 0 -> "이번 목적지 ${destination.name}에서 할 일이 ${tasks}건 있어요."
                else -> "이번 목적지 ${destination.name}에 참고할 정보가 ${references}건 있어요."
            }
        }
        if (fresh.isNotEmpty()) {
            val important = fresh.count { it.note.importance == Importance.HIGH }
            parts += "지난 사용 이후 새로 생기거나 바뀐 인계가 ${fresh.size}건 있어요." +
                if (important > 0) " 그중 ${important}건은 중요해요." else ""
        }
        if (carried > 0) parts += "아직 끝나지 않은 중요한 일이 ${carried}건 남아 있어요."
        return parts.joinToString(" ").ifEmpty { "새로 확인할 인계가 없어요." }
    }
}

data class DoneEntry(val note: Note, val resolution: Resolution, val actor: Driver?)

/** 운전자의 가장 최근 확인 시각. 세션이 달라도 같은 운전자의 확인은 이어진다. */
fun HandoverState.lastAcknowledgedAt(noteId: String, driverId: String): Long? =
    receipts.filter { it.noteId == noteId && it.driverId == driverId }.mapNotNull { it.acknowledgedAt }.maxOrNull()

object BriefingBuilder {
    fun build(state: HandoverState, fleet: Fleet, driver: Driver, destination: Place? = null): Briefing {
        val items = state.notes
            .filter { it.vehicleId == fleet.vehicle.id && it.isActive }
            .map { note ->
                val lastAck = state.lastAcknowledgedAt(note.id, driver.id)
                val reason = when {
                    lastAck == null -> BriefingReason.NEW
                    note.contentUpdatedAt > lastAck -> BriefingReason.UPDATED
                    note.kind == NoteKind.TASK && note.importance == Importance.HIGH -> BriefingReason.CARRIED_OVER
                    else -> BriefingReason.ACKNOWLEDGED
                }
                BriefingItem(note, reason, fleet.driver(note.authorId), fleet.place(note.targetPlaceId))
            }
        val newestFirst = compareByDescending<BriefingItem> { it.note.contentUpdatedAt }
        val (here, rest) = items.partition { destination != null && it.note.targetPlaceId == destination.id }
        return Briefing(
            driver = driver,
            attention = rest.filter { it.needsAcknowledgement }
                .sortedWith(compareByDescending<BriefingItem> { it.note.importance == Importance.HIGH }.then(newestFirst)),
            carriedOver = rest.filter { it.reason == BriefingReason.CARRIED_OVER }.sortedBy { it.note.createdAt },
            others = rest.filter { it.reason == BriefingReason.ACKNOWLEDGED }
                .sortedWith(compareBy<BriefingItem> { it.note.kind }.then(newestFirst)),
            destination = destination,
            // 목적지에서는 할 일이 먼저, 그다음 중요한 것, 최근 것 순.
            atDestination = here.sortedWith(
                compareBy<BriefingItem> { it.note.kind }
                    .thenByDescending { it.note.importance == Importance.HIGH }
                    .then(newestFirst),
            ),
        )
    }
}
