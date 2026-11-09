package io.github.beomhui.drivecompanion.core.handover

import io.github.beomhui.drivecompanion.core.vehicle.Availability
import io.github.beomhui.drivecompanion.core.vehicle.DataSource
import io.github.beomhui.drivecompanion.core.vehicle.SimulatedVehicleContextSource
import io.github.beomhui.drivecompanion.core.vehicle.VehicleReading
import io.github.beomhui.drivecompanion.core.vehicle.VehicleSignal
import org.junit.Assert.*
import org.junit.Test

class HandoverServiceTest {
    private val fleet = Fleet.SAMPLE
    private val a = fleet.drivers[0].id
    private val b = fleet.drivers[1].id
    private val c = fleet.drivers[2].id
    private val hanbit = fleet.places[0].id

    private var now = 0L
    private var ids = 0
    private val store = InMemoryHandoverStore()
    private fun service(target: HandoverStore = store) =
        HandoverService(fleet, target, clock = { now += 1000; now }, newId = { "id-${++ids}" })

    private fun task(id: String, text: String, importance: Importance = Importance.NORMAL, place: String? = null) =
        NoteDraft(id, if (place == null) NoteAnchor.VEHICLE else NoteAnchor.ITEM, NoteKind.TASK, text, place, importance)

    private fun HandoverService.reasons() = briefing().let { brief ->
        (brief.atDestination + brief.attention + brief.carriedOver + brief.others).associate { it.note.id to it.reason }
    }

    @Test fun `A to B to C hands over only unfinished work and survives restart`() {
        val first = service()
        first.startSession(a)
        first.createNote(task("wiper", "와이퍼 소음 확인 필요", Importance.HIGH))
        first.createNote(task("sample", "샘플 박스는 한빛상사 전달용", place = hanbit))
        assertTrue("작성자는 자기 인계를 다시 받지 않는다", first.briefing().attention.isEmpty())
        first.returnVehicle()

        val second = service()
        second.startSession(b)
        assertEquals(mapOf("wiper" to BriefingReason.NEW, "sample" to BriefingReason.NEW), second.reasons())
        assertEquals("wiper", second.briefing().attention.first().note.id)
        second.acknowledge("wiper")
        second.acknowledge("sample")
        second.resolve("sample", "구매팀 전달 완료")
        assertEquals(BriefingReason.CARRIED_OVER, second.reasons()["wiper"])
        second.returnVehicle()

        val third = service()
        third.startSession(c)
        assertEquals("B의 확인이 C의 확인이 되지 않는다", mapOf("wiper" to BriefingReason.NEW), third.reasons())
        val done = third.recentlyDone().single()
        assertEquals("sample", done.note.id)
        assertEquals(b, done.resolution.actorId)
        assertEquals("구매팀 전달 완료", done.resolution.comment)
    }

    @Test fun `editing after acknowledgement shows again to others but not to the editor`() {
        val service = service()
        service.startSession(a)
        service.createNote(NoteDraft("ref", NoteAnchor.PLACE, NoteKind.REFERENCE, "케이블은 B2 관리실", hanbit))
        service.returnVehicle()
        service.startSession(b)
        service.acknowledge("ref")
        assertEquals(BriefingReason.ACKNOWLEDGED, service.reasons()["ref"])
        service.returnVehicle()

        service.startSession(c)
        service.editNote("ref", "케이블은 B1 관리실로 옮김", Importance.NORMAL, hanbit)
        assertEquals(BriefingReason.ACKNOWLEDGED, service.reasons()["ref"])
        service.returnVehicle()

        service.startSession(b)
        assertEquals(BriefingReason.UPDATED, service.reasons()["ref"])
    }

    @Test fun `done and archive keep content time while reopen shows the task again`() {
        val service = service()
        service.startSession(a)
        val created = service.createNote(task("t", "타이어 공기압 확인"))
        service.createNote(NoteDraft("r", NoteAnchor.VEHICLE, NoteKind.REFERENCE, "하이패스 카드 있음"))
        service.returnVehicle()
        service.startSession(b)
        service.acknowledge("t")
        assertEquals(created.contentUpdatedAt, service.resolve("t").contentUpdatedAt)
        assertEquals(created.contentUpdatedAt, service.state().notes.first { it.id == "t" }.contentUpdatedAt)
        service.archive("r")
        assertTrue(service.briefing().attention.isEmpty())
        service.returnVehicle()

        service.startSession(c)
        service.acknowledge("t")
        val reopened = service.reopen("t", "다시 경고등 켜짐")
        assertTrue(reopened.contentUpdatedAt > created.contentUpdatedAt)
        assertEquals(BriefingReason.ACKNOWLEDGED, service.reasons()["t"])
        service.returnVehicle()
        service.startSession(b)
        assertEquals(BriefingReason.UPDATED, service.reasons()["t"])
        assertEquals(
            listOf(ResolutionAction.DONE, ResolutionAction.ARCHIVE, ResolutionAction.REOPEN),
            service.state().resolutions.map { it.action },
        )
    }

    @Test fun `repeated commands do not duplicate records or saves`() {
        val service = service()
        service.startSession(a)
        service.createNote(task("t", "엔진오일 경고"))
        service.createNote(task("t", " 엔진오일 경고 "))
        assertEquals(1, service.state().notes.size)
        assertThrows(HandoverException::class.java) { service.createNote(task("t", "다른 내용")) }
        service.returnVehicle()

        service.startSession(b)
        assertEquals(service.activeSession(), service.startSession(b))
        service.acknowledge("t")
        val saves = store.saveCount
        service.acknowledge("t")
        service.markPresented(listOf("t"))
        service.resolve("t")
        service.resolve("t")
        assertEquals(saves + 1, store.saveCount)
        assertEquals(1, service.state().resolutions.size)
        assertEquals(1, service.state().receipts.count { it.driverId == b })
    }

    @Test fun `presenting is not acknowledging`() {
        val service = service()
        service.startSession(a)
        service.createNote(task("t", "블랙박스 메모리 교체"))
        service.returnVehicle()
        service.startSession(b)
        service.markPresented(listOf("t"))
        val receipt = service.state().receipts.single { it.driverId == b }
        assertNotNull(receipt.presentedAt)
        assertNull(receipt.acknowledgedAt)
        assertEquals(BriefingReason.NEW, service.reasons()["t"])
    }

    @Test fun `failed save leaves state unchanged`() {
        val failing = object : HandoverStore {
            var fail = false
            override fun load() = HandoverState()
            override fun save(state: HandoverState) { if (fail) error("disk full") }
        }
        val service = service(failing)
        service.startSession(a)
        failing.fail = true
        val error = assertThrows(HandoverException::class.java) { service.createNote(task("t", "와이퍼")) }
        assertEquals("기록을 저장하지 못했어요. 다시 시도해주세요.", error.message)
        assertTrue(service.state().notes.isEmpty())
    }

    @Test fun `input validation explains the problem`() {
        val service = service()
        assertThrows(HandoverException::class.java) { service.createNote(task("t", "세션 없음")) }
        service.startSession(a)
        assertThrows(HandoverException::class.java) { service.createNote(task("t", "  ")) }
        assertThrows(HandoverException::class.java) { service.createNote(task("t", "가".repeat(301))) }
        assertThrows(HandoverException::class.java) {
            service.createNote(NoteDraft("p", NoteAnchor.PLACE, NoteKind.REFERENCE, "장소 없음"))
        }
        assertThrows(HandoverException::class.java) { service.createNote(task("u", "모르는 장소", place = "nowhere")) }
        service.createNote(task("ok", "가".repeat(300)))
        assertThrows(HandoverException::class.java) { service.archive("ok") }
        assertThrows(HandoverException::class.java) { service.resolve("ok", "가".repeat(201)) }
        assertEquals(1, service.state().notes.size)
    }

    @Test fun `another driver must take over an unreturned session explicitly`() {
        val service = service()
        val first = service.startSession(a)
        val error = assertThrows(ActiveSessionException::class.java) { service.startSession(b) }
        assertEquals("김하늘님의 사용이 아직 반납되지 않았어요.", error.message)
        service.startSession(b, takeOver = true)
        val closed = service.state().sessions.first { it.id == first.id }
        assertTrue(closed.endedByTakeover)
        assertNotNull(closed.returnedAt)
        assertEquals(b, service.activeSession()?.driverId)
    }

    @Test fun `summary counts new important and carried over work`() {
        val service = service()
        service.startSession(a)
        assertEquals("새로 확인할 인계가 없어요.", service.briefing().summary)
        service.createNote(task("h", "브레이크 소음", Importance.HIGH))
        service.createNote(task("n", "세차 필요"))
        service.returnVehicle()
        service.startSession(b)
        assertEquals("지난 사용 이후 새로 생기거나 바뀐 인계가 2건 있어요. 그중 1건은 중요해요.", service.briefing().summary)
        service.acknowledge("h")
        service.acknowledge("n")
        assertEquals("아직 끝나지 않은 중요한 일이 1건 남아 있어요.", service.briefing().summary)
    }

    @Test fun `json round trip keeps snapshot sources and rejects broken records`() {
        val vehicle = SimulatedVehicleContextSource(clock = { 42 })
        val service = service()
        DemoScenario.seed(service, vehicle.currentSnapshot()) { "demo-${++ids}" }
        val restored = HandoverJson.decode(HandoverJson.encode(service.state()))
        assertEquals(service.state(), restored)
        val snapshot = restored.notes.first().snapshot!!
        assertTrue(snapshot.isSimulated)
        assertNull(snapshot.reading(VehicleSignal.FUEL_PERCENT)!!.value)

        assertThrows(IllegalArgumentException::class.java) {
            HandoverJson.decode("""{"schemaVersion":99}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            HandoverJson.decode(
                """{"notes":[{"id":"x","vehicleId":"v","authorId":"a","createdAt":1,"contentUpdatedAt":1,""" +
                    """"anchor":"VEHICLE","kind":"TASK","text":"t"}]}""",
            )
        }
    }

    @Test fun `unavailable readings carry no value`() {
        assertThrows(IllegalArgumentException::class.java) {
            VehicleReading(VehicleSignal.GEAR, "P", null, DataSource.VEHICLE, 1, Availability.UNAVAILABLE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VehicleReading(VehicleSignal.GEAR, null, null, DataSource.VEHICLE, 1, Availability.AVAILABLE)
        }
    }

    @Test fun `demo scenario leaves three notes for the next driver`() {
        val service = service()
        DemoScenario.seed(service, null) { "demo-${++ids}" }
        assertNull(service.activeSession())
        service.startSession(b)
        assertEquals(3, service.briefing().attention.size)
        assertThrows(HandoverException::class.java) { DemoScenario.seed(service, null) { "again" } }
    }
}
