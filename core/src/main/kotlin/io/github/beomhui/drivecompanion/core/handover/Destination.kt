package io.github.beomhui.drivecompanion.core.handover

/** NAVIGATION: 내비가 알려준 목적지. USER: 내비 값이 없을 때 운전자가 직접 고른 목적지. */
enum class DestinationSource { NAVIGATION, USER }

/** 도착 근거. NAVIGATION: 내비 도착 신호. DRIVER: 운전자가 "도착했어요"로 직접 확인. 화면·영상에서 둘을 나눠 보여준다. */
enum class ArrivalEvidence { NAVIGATION, DRIVER }

/** place가 null이면 등록되지 않은 장소다. 이름만 보여주고 인계는 연결하지 않는다. */
data class Destination(val label: String, val place: Place?, val source: DestinationSource)

/**
 * 이번 운행의 목적지와 도착 여부. 저장하지 않고 세션 동안만 기억한다.
 *
 * 주차 ≠ 도착. 도착 근거(내비 도착 신호 또는 운전자의 직접 확인)가 현재 목적지와 맞고 주차가 확인됐을 때만
 * 도착 안내를 내보내며, 같은 세션·장소에서는 한 번만 내보낸다. 목적지가 바뀌면 이전 도착 근거는 버린다.
 */
class DestinationTracker(private val fleet: Fleet) {
    var destination: Destination? = null
        private set
    var arrivedBy: ArrivalEvidence? = null
        private set
    val arrived: Boolean get() = arrivedBy != null

    private var sessionId: String? = null
    private val announced = mutableSetOf<String>()

    /** 세션이 바뀌면 true. 앞 운전자의 목적지와 도착 기록은 이어받지 않는다. */
    fun onSession(id: String?): Boolean {
        if (id == sessionId) return false
        sessionId = id
        destination = null
        arrivedBy = null
        announced.clear()
        return true
    }

    /**
     * 내비 목적지가 오면 직접 고른 목적지보다 우선한다. null은 경로 종료로 보고 내비 목적지만 지운다.
     * 도착 근거가 있으면 지우지 않는다. 도착하며 경로가 끝날 수 있고(실차 미확인), 지우면 주차 뒤 도착 안내를 못 띄운다.
     */
    fun onNavigation(label: String?) {
        val name = label?.trim()?.takeIf { it.isNotEmpty() }
        if (name == null) {
            if (destination?.source == DestinationSource.NAVIGATION && !arrived) change(null)
            return
        }
        change(Destination(name, fleet.placeFor(name), DestinationSource.NAVIGATION))
    }

    fun choose(place: Place?) = change(place?.let { Destination(it.name, it, DestinationSource.USER) })

    /**
     * 내비 도착 신호. 현재 목적지와 다른 곳이면 목적지 변경 중으로 보고 무시한다. 이름이 없으면 현재 목적지로 본다.
     * 목적지를 아직 못 받았으면(내비 화면에서 시작한 경로) 신호의 이름을 목적지로 삼는다.
     */
    fun onArrived(label: String?) {
        if (destination == null && label != null) onNavigation(label)
        val target = destination?.place ?: return
        if (label != null && fleet.placeFor(label)?.id != target.id) return
        // 내비 신호가 운전자 확인보다 강한 근거라 덮어쓴다.
        arrivedBy = ArrivalEvidence.NAVIGATION
    }

    fun confirmArrival() {
        if (destination?.place != null && arrivedBy == null) arrivedBy = ArrivalEvidence.DRIVER
    }

    /** 지금 안내할 도착 장소. 한 번 돌려준 장소는 같은 세션에서 다시 돌려주지 않는다. */
    fun takeArrival(parked: Boolean?): Place? {
        val place = destination?.place ?: return null
        if (!arrived || parked != true || sessionId == null || !announced.add(place.id)) return null
        return place
    }

    private fun change(next: Destination?) {
        if (next?.place?.id != destination?.place?.id) arrivedBy = null
        destination = next
    }
}
