package io.github.beomhui.drivecompanion.core.handover

import io.github.beomhui.drivecompanion.core.vehicle.SimulatedVehicleContextSource
import org.junit.Assert.*
import org.junit.Test

/** docs/demo-script.md의 장면 순서와 화면 문구를 그대로 따라간다. 대본을 고치면 이 테스트도 같이 고친다. */
class DemoScriptTest {
    private val fleet = Fleet.SAMPLE
    private val kim = fleet.drivers[0]
    private val lee = fleet.drivers[1]
    private val park = fleet.drivers[2]
    private val hanbit = fleet.places[0]
    private val hq = fleet.places[1]

    private var now = 0L
    private var ids = 0
    private val store = InMemoryHandoverStore()
    private fun service() = HandoverService(fleet, store, clock = { now += 1000; now }, newId = { "id-${++ids}" })

    /** 내비 경로와 앱의 목적지 상태. 앱처럼 새 세션을 시작할 때 이미 설정된 내비 목적지부터 가져온다. */
    private val navi = SimulatedVehicleContextSource()
    private val destinations = DestinationTracker(fleet)
    private fun HandoverService.begin(driver: Driver) = startSession(driver.id).also {
        if (destinations.onSession(it.id)) destinations.onNavigation(navi.currentDestination())
    }

    private fun Briefing.texts(items: List<BriefingItem>) = items.map { it.note.text }

    @Test fun `demo script scenes hold`() {
        // 촬영 전 · 김하늘로 시작해 2건을 미리 넣고 반납하지 않는다.
        val first = service()
        first.begin(kim)
        val sample = first.createNote(NoteDraft(
            "sample", NoteAnchor.ITEM, NoteKind.TASK,
            "트렁크의 샘플 박스 2개는 한빛상사 3층 구매팀 전달용이에요.", targetPlaceId = hanbit.id,
        ))
        val cable = first.createNote(NoteDraft(
            "cable", NoteAnchor.PLACE, NoteKind.REFERENCE,
            "충전 케이블은 본사 주차장 B2 관리실 선반에 있어요.", targetPlaceId = hq.id,
        ))

        // 장면 1 · 와이퍼만 화면에서 직접 입력하고 반납한다.
        val wiper = first.createNote(NoteDraft(
            "wiper", NoteAnchor.VEHICLE, NoteKind.TASK,
            "운전석 와이퍼에서 끽 소리가 나요. 정비 예약이 필요해요.", importance = Importance.HIGH,
        ))
        first.returnVehicle()

        // 장면 2 · 장면 2 직전에 내비 경로를 한빛상사로 정하고, B(이도윤)가 요약을 받는다.
        navi.setDestination("한빛상사")
        first.begin(lee)
        assertEquals(DestinationSource.NAVIGATION, destinations.destination?.source)
        val b = first.briefing(destinations.destination?.place)
        assertEquals(
            "이번 목적지 한빛상사에서 할 일이 1건 있어요. 지난 사용 이후 새로 생기거나 바뀐 인계가 3건 있어요. 그중 1건은 중요해요.",
            b.summary,
        )
        assertEquals(listOf(sample.text), b.texts(b.atDestination))
        assertEquals("중요한 일이 먼저", listOf(wiper.text, cable.text), b.texts(b.attention))
        first.acknowledge(wiper.id)
        first.acknowledge(cable.id)
        assertEquals("확인 ≠ 해결", TaskState.OPEN, first.state().notes.single { it.id == wiper.id }.taskState)

        // 장면 3 · 주차 후 "도착했어요", 샘플 박스 처리, 케이블 위치 수정, 반납, 경로 취소.
        destinations.confirmArrival()
        assertEquals(hanbit, destinations.takeArrival(parked = true))
        first.resolve(sample.id, "구매팀 김OO님께 전달")
        first.editNote(cable.id, CABLE_MOVED, Importance.NORMAL, hq.id)
        assertEquals("반납 대화상자: 진행 중인 일 1건", 1, first.state().notes.count { it.taskState == TaskState.OPEN })
        first.returnVehicle()
        assertEquals("취소하지 않으면 경로가 남는다", "한빛상사", navi.currentDestination())
        navi.setDestination(null)

        // 장면 4 · 앱을 다시 켜고 C(박서연)로 시작한다.
        val restarted = service()
        restarted.begin(park)
        assertNull("경로를 취소했으니 C에게 한빛상사가 남지 않는다", destinations.destination)
        val c = restarted.briefing(destinations.destination?.place)
        assertEquals("지난 사용 이후 새로 생기거나 바뀐 인계가 2건 있어요. 그중 1건은 중요해요.", c.summary)
        assertEquals("B의 확인은 C에게 넘어가지 않는다", listOf(wiper.text, CABLE_MOVED), c.texts(c.attention))
        assertTrue(c.attention.all { it.reason == BriefingReason.NEW })
        val done = restarted.recentlyDone().single()
        assertEquals(sample.id, done.note.id)
        assertEquals(lee, done.actor)
        assertEquals("구매팀 김OO님께 전달", done.resolution.comment)
        restarted.returnVehicle()

        // 장면 5 (선택) · A가 다시 타면 고친 케이블만 '내용 바뀜', 와이퍼는 계속 남은 중요한 일.
        restarted.begin(kim)
        val a = restarted.briefing()
        assertEquals("지난 사용 이후 새로 생기거나 바뀐 인계가 1건 있어요. 아직 끝나지 않은 중요한 일이 1건 남아 있어요.", a.summary)
        assertEquals(BriefingReason.UPDATED, a.attention.single().reason)
        assertEquals(CABLE_MOVED, a.attention.single().note.text)
        assertEquals(listOf(wiper.text), a.texts(a.carriedOver))
    }

    private companion object {
        const val CABLE_MOVED = "충전 케이블은 본사 주차장 B1 경비실로 옮겼어요."
    }
}
