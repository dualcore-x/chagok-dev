package io.github.beomhui.drivecompanion.core.handover

import org.junit.Assert.*
import org.junit.Test

class DestinationTest {
    private val fleet = Fleet.SAMPLE
    private val hanbit = fleet.places[0]
    private val hq = fleet.places[1]

    private var now = 0L
    private var ids = 0
    private fun service() =
        HandoverService(fleet, InMemoryHandoverStore(), clock = { now += 1000; now }, newId = { "id-${++ids}" })

    /** 시연 데이터와 같은 상태에서 B가 사용을 시작한다. */
    private fun seeded(): HandoverService = service().apply {
        DemoScenario.seed(this, null) { "id-${++ids}" }
        startSession(fleet.drivers[1].id)
    }

    @Test fun `navigation labels resolve to registered places`() {
        assertEquals(hanbit, fleet.placeFor("한빛상사 3층 구매팀"))
        assertEquals(hanbit, fleet.placeFor("hanbit trading"))
        assertEquals("더 길게 일치한 장소가 이긴다", hanbit, fleet.placeFor("한빛상사 본사"))
        assertEquals(hq, fleet.placeFor("본사 주차장 B2"))
        assertEquals("이름은 가운데 있어도 찾는다", hanbit, fleet.placeFor("(주)한빛상사"))
        assertEquals(hanbit, fleet.placeFor("Hanbit Trading Co."))
        assertEquals(hq, fleet.placeFor("본사"))
        assertEquals(hq, fleet.placeFor("본사 정문"))
        assertNull("짧은 별칭은 맨 앞에 올 때만 쓴다", fleet.placeFor("서울은행 본사"))
        assertNull(fleet.placeFor("서울역"))
        assertNull(fleet.placeFor("  "))
        assertNull(fleet.placeFor(null))
    }

    @Test fun `ambiguous labels do not pick a place`() {
        val twins = fleet.copy(places = listOf(Place("a", "물류센터"), Place("b", "제2물류", aliases = listOf("물류센터"))))
        assertNull(twins.placeFor("물류센터"))
    }

    @Test fun `destination work comes first without repeating in other sections`() {
        val service = seeded()
        val brief = service.briefing(hanbit)
        assertEquals(listOf("트렁크의 샘플 박스 2개는 한빛상사 3층 구매팀 전달용이에요."), brief.atDestination.map { it.note.text })
        val rest = (brief.attention + brief.carriedOver + brief.others).map { it.note.id }
        assertFalse(brief.atDestination.single().note.id in rest)
        assertEquals(2, rest.size)
        assertEquals(
            "이번 목적지 한빛상사에서 할 일이 1건 있어요. 지난 사용 이후 새로 생기거나 바뀐 인계가 3건 있어요. 그중 1건은 중요해요.",
            brief.summary,
        )
        assertEquals(
            "이번 목적지 본사 주차장에 참고할 정보가 1건 있어요. 지난 사용 이후 새로 생기거나 바뀐 인계가 3건 있어요. 그중 1건은 중요해요.",
            service.briefing(hq).summary,
        )
        assertTrue("목적지가 없으면 기존 요약과 같다", service.briefing().atDestination.isEmpty())
    }

    @Test fun `finished destination work drops out`() {
        val service = seeded()
        val sample = service.briefing(hanbit).atDestination.single().note
        service.resolve(sample.id, "구매팀 전달")
        val brief = service.briefing(hanbit)
        assertTrue(brief.atDestination.isEmpty())
        assertFalse(brief.summary.contains("이번 목적지"))
    }

    @Test fun `parking alone is not arrival`() {
        val tracker = DestinationTracker(fleet).apply { onSession("s1") }
        tracker.onNavigation("한빛상사")
        assertNull("목적지 설정 + 주차만으로는 안내하지 않는다", tracker.takeArrival(parked = true))
        tracker.onArrived("한빛상사")
        assertNull("도착 신호가 와도 주행 중이면 기다린다", tracker.takeArrival(parked = false))
        assertNull("주차 미확인도 기다린다", tracker.takeArrival(parked = null))
        assertEquals(hanbit, tracker.takeArrival(parked = true))
        assertNull("같은 세션·장소는 한 번만", tracker.takeArrival(parked = true))
    }

    @Test fun `route ending on arrival keeps the arrival until parked`() {
        val tracker = DestinationTracker(fleet).apply { onSession("s1") }
        tracker.onNavigation("한빛상사")
        tracker.onArrived("한빛상사")
        tracker.onNavigation(null)
        assertEquals("내비가 도착하며 경로를 끝내도 목적지를 지우지 않는다", hanbit, tracker.destination?.place)
        assertNull(tracker.takeArrival(parked = false))
        assertEquals(hanbit, tracker.takeArrival(parked = true))

        val manual = DestinationTracker(fleet).apply { onSession("s1") }
        manual.onNavigation("본사")
        manual.confirmArrival()
        manual.onNavigation(null)
        assertEquals("직접 확인한 도착도 같다", hq, manual.takeArrival(parked = true))

        val cancelled = DestinationTracker(fleet).apply { onSession("s1") }
        cancelled.onNavigation("한빛상사")
        cancelled.onNavigation(null)
        assertNull("도착 전 경로 취소는 목적지를 지운다", cancelled.destination)
    }

    @Test fun `arrival records whether navigation or the driver said so`() {
        val tracker = DestinationTracker(fleet).apply { onSession("s1") }
        tracker.onNavigation("한빛상사")
        tracker.confirmArrival()
        assertEquals(ArrivalEvidence.DRIVER, tracker.arrivedBy)
        tracker.onArrived("한빛상사")
        assertEquals("내비 신호가 더 강한 근거", ArrivalEvidence.NAVIGATION, tracker.arrivedBy)
        tracker.confirmArrival()
        assertEquals(ArrivalEvidence.NAVIGATION, tracker.arrivedBy)
        tracker.onNavigation("본사")
        assertNull("목적지가 바뀌면 근거도 버린다", tracker.arrivedBy)
    }

    @Test fun `arrival evidence must match the current destination`() {
        val tracker = DestinationTracker(fleet).apply { onSession("s1") }
        tracker.onNavigation("한빛상사")
        tracker.onArrived("본사 주차장")
        assertNull("다른 곳의 도착 신호는 무시한다", tracker.takeArrival(parked = true))

        tracker.onArrived(null)
        tracker.onNavigation("본사")
        assertFalse("목적지가 바뀌면 이전 도착 근거를 버린다", tracker.arrived)
        assertNull(tracker.takeArrival(parked = true))

        tracker.onNavigation("서울역")
        assertNull(tracker.destination?.place)
        tracker.confirmArrival()
        assertFalse("등록되지 않은 장소는 도착 처리하지 않는다", tracker.arrived)
    }

    @Test fun `manual destination works without navigation and navigation takes over`() {
        val tracker = DestinationTracker(fleet).apply { onSession("s1") }
        tracker.choose(hq)
        assertEquals(DestinationSource.USER, tracker.destination?.source)
        tracker.onNavigation(null)
        assertEquals("경로 취소는 직접 고른 목적지를 지우지 않는다", hq, tracker.destination?.place)
        tracker.confirmArrival()
        assertEquals(hq, tracker.takeArrival(parked = true))

        tracker.onNavigation("한빛상사")
        assertEquals(DestinationSource.NAVIGATION, tracker.destination?.source)
        tracker.onNavigation(null)
        assertNull(tracker.destination)
    }

    @Test fun `a stop on the way does not announce and the real arrival still does`() {
        val tracker = DestinationTracker(fleet).apply { onSession("s1") }
        tracker.onNavigation("한빛상사")
        assertNull("휴게소에 주차", tracker.takeArrival(parked = true))
        assertNull("다시 출발", tracker.takeArrival(parked = false))
        tracker.onArrived("한빛상사")
        assertEquals(hanbit, tracker.takeArrival(parked = true))
    }

    @Test fun `route ending at arrival keeps the announcement until parked`() {
        val tracker = DestinationTracker(fleet).apply { onSession("s1") }
        tracker.onNavigation("한빛상사")
        tracker.onArrived("한빛상사")
        tracker.onNavigation(null)
        assertEquals(hanbit, tracker.takeArrival(parked = true))
    }

    @Test fun `changing destination after arrival evidence waits for the new place`() {
        val tracker = DestinationTracker(fleet).apply { onSession("s1") }
        tracker.onNavigation("한빛상사")
        tracker.onArrived("한빛상사")
        tracker.onNavigation("본사 주차장")
        assertNull("한빛상사 도착 근거로 본사를 안내하지 않는다", tracker.takeArrival(parked = true))
        tracker.onArrived("본사 주차장")
        assertEquals(hq, tracker.takeArrival(parked = true))
    }

    @Test fun `arrival signal without a known destination uses its own label`() {
        val tracker = DestinationTracker(fleet).apply { onSession("s1") }
        tracker.onArrived("한빛상사")
        assertEquals(hanbit, tracker.takeArrival(parked = true))

        val unknown = DestinationTracker(fleet).apply { onSession("s1") }
        unknown.onArrived("서울역")
        assertNull("등록되지 않은 장소는 안내하지 않는다", unknown.takeArrival(parked = true))
        unknown.onArrived(null)
        assertNull(unknown.takeArrival(parked = true))
    }

    @Test fun `a new session forgets the previous driver's trip`() {
        val tracker = DestinationTracker(fleet)
        assertTrue(tracker.onSession("s1"))
        assertFalse(tracker.onSession("s1"))
        tracker.onNavigation("한빛상사")
        tracker.onArrived("한빛상사")
        assertEquals(hanbit, tracker.takeArrival(parked = true))

        assertTrue(tracker.onSession("s2"))
        assertNull(tracker.destination)
        tracker.onNavigation("한빛상사")
        tracker.onArrived("한빛상사")
        assertEquals("새 세션에서는 다시 안내한다", hanbit, tracker.takeArrival(parked = true))

        tracker.onSession(null)
        tracker.onNavigation("한빛상사")
        tracker.onArrived("한빛상사")
        assertNull("세션 없이는 안내하지 않는다", tracker.takeArrival(parked = true))
    }
}
