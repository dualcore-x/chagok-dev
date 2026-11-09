package io.github.beomhui.drivecompanion.core.handover

import io.github.beomhui.drivecompanion.core.vehicle.VehicleSnapshot
import kotlinx.serialization.Serializable

enum class NoteAnchor { VEHICLE, PLACE, ITEM }
enum class NoteKind { TASK, REFERENCE }
enum class Importance { NORMAL, HIGH }
enum class TaskState { OPEN, DONE }
enum class ReferenceState { ACTIVE, ARCHIVED }
enum class ResolutionAction { DONE, REOPEN, ARCHIVE }

/** TASK는 taskState만, REFERENCE는 referenceState만 쓴다. */
@Serializable
data class Note(
    val id: String,
    val vehicleId: String,
    val authorId: String,
    val createdAt: Long,
    /** 본문·중요도·대상 장소 수정과 재열기 때만 바뀐다. 완료·보관·전달 기록으로는 바뀌지 않는다. */
    val contentUpdatedAt: Long,
    val anchor: NoteAnchor,
    val kind: NoteKind,
    val text: String,
    val targetPlaceId: String? = null,
    val importance: Importance = Importance.NORMAL,
    val taskState: TaskState? = null,
    val referenceState: ReferenceState? = null,
    val snapshot: VehicleSnapshot? = null,
) {
    init {
        when (kind) {
            NoteKind.TASK -> require(taskState != null && referenceState == null)
            NoteKind.REFERENCE -> require(referenceState != null && taskState == null)
        }
        require(anchor != NoteAnchor.PLACE || targetPlaceId != null)
    }

    val isActive: Boolean get() = taskState == TaskState.OPEN || referenceState == ReferenceState.ACTIVE
}

/** 운전 세션마다 하나. 화면에 보여준 것(presented)과 사용자가 확인한 것(acknowledged)을 나눈다. */
@Serializable
data class Receipt(
    val noteId: String,
    val driverId: String,
    val sessionId: String,
    val presentedAt: Long? = null,
    val acknowledgedAt: Long? = null,
)

@Serializable
data class Resolution(
    val id: String,
    val noteId: String,
    val actorId: String,
    val action: ResolutionAction,
    val performedAt: Long,
    val comment: String? = null,
)

@Serializable
data class DriveSession(
    val id: String,
    val vehicleId: String,
    val driverId: String,
    val startedAt: Long,
    val returnedAt: Long? = null,
    /** 앞 사람이 반납하지 않아 다음 운전자가 대신 종료한 경우. */
    val endedByTakeover: Boolean = false,
)

@Serializable
data class HandoverState(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val notes: List<Note> = emptyList(),
    val receipts: List<Receipt> = emptyList(),
    val resolutions: List<Resolution> = emptyList(),
    val sessions: List<DriveSession> = emptyList(),
) {
    companion object { const val CURRENT_SCHEMA = 1 }
}

data class Vehicle(val id: String, val name: String)
data class Driver(val id: String, val name: String)
/** aliases는 내비 목적지 이름이 등록 이름과 다를 때 쓴다. */
data class Place(val id: String, val name: String, val aliases: List<String> = emptyList())

/** 1차 범위는 차량 1대·운전자 3명. 관리자 웹이 붙기 전까지 앱에 고정한다. */
data class Fleet(val vehicle: Vehicle, val drivers: List<Driver>, val places: List<Place>) {
    fun driver(id: String) = drivers.firstOrNull { it.id == id }
    fun place(id: String?) = id?.let { placeId -> places.firstOrNull { it.id == placeId } }

    /**
     * 내비 목적지 이름을 등록 장소로 바꾼다. 공백·대소문자를 무시하고, 이름은 들어 있기만 하면, 별칭은 맨 앞에 올 때만
     * 같은 장소로 본다. "본사" 같은 짧은 별칭이 "OO은행 본사"에 붙지 않게 하려는 것이다.
     * 가장 길게 일치한 장소를 고르고, 서로 다른 장소가 똑같이 일치하면 잘못 안내하지 않도록 null을 준다.
     */
    fun placeFor(label: String?): Place? {
        val target = label?.normalized()?.takeIf { it.isNotEmpty() } ?: return null
        val matches = places.mapNotNull { place ->
            val name = place.name.normalized().takeIf { it.isNotEmpty() && target.contains(it) }
            val aliases = place.aliases.map { it.normalized() }.filter { it.isNotEmpty() && target.startsWith(it) }
            (listOfNotNull(name) + aliases).maxOfOrNull { it.length }?.let { place to it }
        }
        val best = matches.maxOfOrNull { it.second } ?: return null
        return matches.filter { it.second == best }.singleOrNull()?.first
    }

    private fun String.normalized() = filterNot { it.isWhitespace() }.lowercase()

    companion object {
        val SAMPLE = Fleet(
            vehicle = Vehicle("vehicle-1", "공용 1호차"),
            drivers = listOf(Driver("driver-a", "김하늘"), Driver("driver-b", "이도윤"), Driver("driver-c", "박서연")),
            places = listOf(
                Place("place-hanbit", "한빛상사", aliases = listOf("Hanbit Trading")),
                Place("place-hq", "본사 주차장", aliases = listOf("본사")),
            ),
        )
    }
}
