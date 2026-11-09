# 구조와 데이터 계약

기준: 데이터 계약 v1 (2026-11-03). 저장 모델은 앱 담당, 연동 포트는 연동 담당이 주도합니다. 계약을 바꿀 때는 두 사람이 먼저 합의하고 같은 날 반영합니다.

## 모듈

| 위치 | 담당 | 내용 |
| --- | --- | --- |
| `core/.../core/handover` | 앱 담당 | 모델, `HandoverService`, `BriefingBuilder`, `DestinationTracker`, `HandoverJson`, `DemoScenario` |
| `core/.../core/vehicle` | 연동 담당 주도 | `VehicleContextSource` 포트 초안, 출처·실행 환경, `SimulatedVehicleContextSource` |
| `core/.../core/DrivingContext.kt` | 연동 담당 | 응답 정책 `SpeechPolicy` (음성 단계에서 사용) |
| `app/.../data/local` | 앱 담당 | `FileHandoverStore` — 앱 내부 JSON 파일, AtomicFile로 교체 저장 |
| `app/.../ui` | 앱 담당 | Compose 화면과 `HandoverViewModel` |
| `core/.../core/speech` | 연동 담당 | `SpeechPort` 포트와 `SimulatedSpeechPort` |
| `app/.../integration` | 연동 담당 | `PleosVehicleContextSource`, `PleosSpeechPort`. Pleos 차량 서비스가 있으면 `AppContainer`가 자동으로 사용 |

core는 Android에 의존하지 않아 JVM 단위 테스트로 인계 규칙을 검사합니다.

## 저장 모델

```text
Note        id, vehicleId, authorId, createdAt, contentUpdatedAt
            anchor: VEHICLE | PLACE | ITEM, kind: TASK | REFERENCE
            text, targetPlaceId?, importance: NORMAL | HIGH
            taskState: OPEN | DONE        (TASK만)
            referenceState: ACTIVE | ARCHIVED (REFERENCE만)
            snapshot?: VehicleSnapshot
Receipt     noteId, driverId, sessionId, presentedAt?, acknowledgedAt?   (세션마다 하나)
Resolution  id, noteId, actorId, action: DONE | REOPEN | ARCHIVE, performedAt, comment?
DriveSession id, vehicleId, driverId, startedAt, returnedAt?, endedByTakeover
```

v1에서 로드맵 계약에 더한 것: `Resolution.action`의 `ARCHIVE`(참고 정보 보관 종료 이력), `DriveSession.endedByTakeover`(앞 사람이 반납하지 않아 대신 종료).

## 인계 규칙

- **확인 ≠ 해결.** B가 확인해도 일은 OPEN일 수 있고, C에게는 별도 확인 기록이 필요합니다.
- **다시 보여주기.** 운전자의 가장 최근 `acknowledgedAt`보다 `contentUpdatedAt`이 늦으면(`>`) 다시 보여줍니다.
- **`contentUpdatedAt`이 바뀌는 때.** 본문·중요도·대상 장소 수정과 다시 열기뿐입니다. 완료·보관·전달 기록으로는 바뀌지 않습니다. 수정하거나 다시 연 사람은 자동으로 확인한 것으로 기록합니다.
- **전달 ≠ 확인.** 인수 요약을 화면에 띄우면 `presentedAt`만 남고, 사용자가 "확인했어요"를 눌러야 `acknowledgedAt`이 남습니다.
- **작성자.** 자기 인계는 작성 시각에 확인한 것으로 기록해 자신에게 다시 알리지 않습니다.
- **중복 방지.** 노트 id는 화면이 작성 시작 때 한 번 만듭니다. 같은 id·같은 내용의 재저장, 완료한 일의 재완료, 이미 확인한 내용의 재확인은 저장하지 않습니다.
- **세션.** 차량당 진행 중 세션은 하나입니다. 다른 운전자가 시작하려면 `takeOver`로 명시적으로 대신 종료해야 합니다.
- **저장 실패.** 저장소에 먼저 쓰고 성공했을 때만 메모리 상태를 바꿉니다.

## 인수 요약

`BriefingBuilder`는 진행 중인 노트를 운전자 기준으로 나눕니다.

| 구분 | 조건 |
| --- | --- |
| 이번 목적지 | 대상 장소가 이번 목적지인 진행 중 노트(B05). 다른 구분과 겹치지 않고 맨 앞. 할 일 → 중요 → 최근 순 |
| 새로 받은 인계 | 확인한 적 없음(NEW) 또는 확인 뒤 내용이 바뀜(UPDATED). 중요한 것부터, 최근 것부터 |
| 계속 남은 중요한 일 | 확인했지만 아직 OPEN인 중요 TASK |
| 진행 중인 인계 | 그 밖에 확인한 진행 중 항목 |

요약 문장은 템플릿으로 만듭니다. 목적지가 있으면 "이번 목적지 한빛상사에서 할 일이 1건 있어요."가 맨 앞에 옵니다. 새로 받은 건수와 남은 중요한 일 건수는 목적지 항목까지 합쳐 셉니다. 11/9부터 화면에는 이 문장 대신 숫자 칸(새로 받음·중요·목적지 일)을 보여 주고, 문장은 읽어주기 등에 쓰도록 core에 남깁니다.

## 목적지와 도착 (B05·B06)

`DestinationTracker`가 이번 운행의 목적지와 도착 여부를 세션 동안만 기억합니다(저장하지 않음).

- **목적지 출처.** 내비 목적지(`currentDestination()`, `DestinationChanged`)가 오면 그것을 쓰고 화면에 "내비 목적지"로 표시합니다. 내비 값이 없으면 운전자가 등록 장소 중에서 직접 고르고 "직접 선택"으로 표시합니다. 내비 경로 종료(`null`)는 내비 목적지만 지웁니다. 단 도착 근거가 있으면 지우지 않습니다. 내비는 도착하면 경로를 끝내므로, 지우면 주차한 뒤 도착 안내를 띄울 수 없습니다.
- **장소 연결.** `Fleet.placeFor(label)`이 내비 목적지 이름을 등록 장소로 바꿉니다. 공백·대소문자를 무시하고, 이름은 들어 있기만 하면, 별칭은 맨 앞에 올 때만 같은 장소로 봅니다("본사" 별칭이 "서울은행 본사"에 붙지 않도록). 그중 가장 길게 일치한 장소를 고릅니다. 서로 다른 장소가 똑같이 일치하거나 등록되지 않은 장소면 인계를 연결하지 않습니다.
- **주차 ≠ 도착.** 도착 근거(현재 목적지와 맞는 `Arrived` 또는 운전자의 "도착했어요")와 주차 확인이 함께 있을 때만 도착 안내를 띄웁니다. 다른 장소의 도착 신호는 무시하고, 목적지가 바뀌면 이전 도착 근거를 버립니다.
- **도착 근거 표시.** `DestinationTracker.arrivedBy`가 내비 신호(`NAVIGATION`)와 운전자 확인(`DRIVER`)을 나눠 기억하고, 화면에 "도착 · 내비 신호" / "도착 · 직접 확인"으로 보여줍니다. 둘 다 있으면 내비 신호로 표시합니다.
- **한 번만.** 같은 세션·장소의 도착 안내는 한 번입니다. 세션이 바뀌면 앞 운전자의 목적지·도착 기록을 이어받지 않습니다.
- **처리는 사용자가.** 도착 안내는 `presentedAt`만 남깁니다. 안내 내용은 도착한 장소 기준으로 고정해, 안내가 떠 있는 동안 목적지가 바뀌어도 제목과 목록이 어긋나지 않습니다. 완료는 안내 안의 "처리 완료로 기록"을 눌러 메모와 함께 남깁니다.
- **작성.** 목적지가 있을 때 인계를 새로 쓰면 장소·물건의 대상 장소를 목적지로 미리 골라둡니다.

중간 휴게소·반복 P·목적지 변경에서 도착 신호 자체를 안정적으로 만드는 일(J06)은 연동 담당 어댑터 몫입니다. 앱은 위 조건으로 한 번 더 거릅니다.

## 말로 남기기 (B06)

인계 작성 화면의 "말로 남기기"가 `SpeechPort.listen`을 부릅니다. STT 결과는 입력칸에 넣기만 하고, 저장은 운전자가 확인한 뒤 "저장"을 눌러야 합니다.

- **붙이기.** 알아들은 말은 이미 쓴 글 뒤에 붙입니다(`appendHeard`). 쓴 글은 지우지 않고, 300자를 넘는 부분만 자르고 알립니다.
- **멈춤·시간 초과.** 운전자가 "멈추기"를 누르거나 말이 없어 듣기가 끝나도(`Cancelled`), 그때까지 알아들은 말(`Partial`)은 넣습니다. 오류면 아무것도 넣지 않고 다시 말하거나 직접 입력하라고 알립니다.
- **확인 안내.** 음성으로 받은 글이 있으면 숫자·층수가 잘못 들릴 수 있다고 안내합니다(`docs/sdk-checks.md`의 "v 3층" 사례). 듣는 중에는 저장 버튼을 막습니다.
- **버리는 경우.** 작성 화면을 떠나거나 주차가 풀리면 듣기를 멈추고 결과를 버립니다. 늦게 온 결과는 요청 번호로 걸러 다른 인계에 들어가지 않게 합니다.

## 차량 데이터 계약

- `source`: SDK를 거친 값은 에뮬레이터여도 `VEHICLE`, 사용자가 입력한 값은 `USER`, 앱이 만든 가짜 값만 `SIMULATED`.
- `runtime: EMULATOR | VEHICLE | UNKNOWN`은 값마다가 아니라 앱 전체에 하나입니다. 화면 상단에 자동으로 표시되어 영상에서도 실행 환경이 드러납니다.
- `availability`가 `UNAVAILABLE`이면 `value`는 비어 있어야 합니다. 미수신을 0으로 채우지 않습니다. 화면 문구는 core `describeSnapshot`이 만들고, 값마다 미수신·이전 값·직접 입력을, 줄 끝에 시뮬레이션을 표시합니다(`docs/product.md` Q05 표).
- `isParked()`가 `true`일 때만 화면 입력을 엽니다. `null`(미확인)도 막습니다.
- 이벤트는 `subscribe` 콜백으로 받습니다. core가 코루틴에 의존하지 않도록 Flow 대신 콜백을 썼습니다.
- `currentDestination()`은 v1 이후 추가한 포트 메서드입니다(기본값 `null`). 앱을 켰을 때 이미 설정된 내비 목적지를 읽습니다.

## 저장 방식

`filesDir/handover.json` 하나에 전체 상태를 JSON으로 씁니다(kotlinx.serialization). 1차 범위(차량 1대, 운전자 3명)에는 충분하고, 2차 서버 동기화 때 다시 판단합니다. 읽을 수 없는 파일은 지우지 않고 `handover.unreadable-<시각>.json`으로 옮긴 뒤 새로 시작하며 화면에 알립니다. `schemaVersion`이 더 새로우면 같은 방식으로 처리합니다.

## 알려진 제약

- 기기 시계가 되돌아가면(헤드유닛 시간 동기화 전, 에뮬레이터 스냅샷 복원) 수정된 노트가 다시 안 뜰 수 있습니다. 시연·테스트는 Cold boot로 합니다. 버전 이력(revision)은 기기 간 동기화가 생기는 2차에 필요하면 추가합니다.
- 운전자 식별은 프로필 선택뿐입니다(PIN 없음).
- 플랫폼 주행 제한(CarUxRestrictions)과 실제 주차 상태는 연동 담당 어댑터 연결 전까지 시뮬레이션입니다. `distractionOptimized`를 선언하지 않았습니다.
