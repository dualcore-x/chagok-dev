package com.dualcore.chagok.core.handover

import com.dualcore.chagok.core.vehicle.VehicleSnapshot

/** 개발·시연용. A(김하늘)가 인계 세 건을 남기고 반납한 상태를 만든다. 다음은 B가 사용을 시작하면 된다. */
object DemoScenario {
    fun seed(service: HandoverService, snapshot: VehicleSnapshot?, newId: () -> String) {
        if (service.state().notes.isNotEmpty()) throw HandoverException("기록이 있을 때는 시연 데이터를 넣을 수 없어요. 먼저 초기화해주세요.")
        if (service.activeSession() != null) service.returnVehicle()
        val fleet = service.fleet
        service.startSession(fleet.drivers[0].id)
        service.createNote(NoteDraft(
            newId(), NoteAnchor.VEHICLE, NoteKind.TASK,
            "운전석 와이퍼에서 끽 소리가 나요. 정비 예약이 필요해요.",
            importance = Importance.HIGH, snapshot = snapshot,
        ))
        service.createNote(NoteDraft(
            newId(), NoteAnchor.ITEM, NoteKind.TASK,
            "트렁크의 샘플 박스 2개는 한빛상사 3층 구매팀 전달용이에요.",
            targetPlaceId = fleet.places[0].id, snapshot = snapshot,
        ))
        service.createNote(NoteDraft(
            newId(), NoteAnchor.PLACE, NoteKind.REFERENCE,
            "충전 케이블은 본사 주차장 B2 관리실 선반에 있어요.",
            targetPlaceId = fleet.places[1].id, snapshot = snapshot,
        ))
        service.returnVehicle()
    }
}
