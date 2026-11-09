# SDK 확인과 설명회 질문

문서 확인: 2026-10-25. Pleos 에뮬레이터 실측: 2026-11-04(아래 첫 절). 실측하지 않은 항목은 여전히 문서상 지원일 뿐입니다.

## 에뮬레이터 실측 결과 (2026-11-04, 연동 담당)

- 환경: Pleos Connect Emulator v2.1.0 (API 34, arm64, cold boot), macOS arm64, OpenJDK 21
- SDK: Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1 (Maven `https://nexus-playground.pleos.ai/repository/maven-releases/`)
- 방법: SDK를 직접 호출하는 확인용 앱(`tools/probe`)과 `PleosVehicleContextSource`를 설치하고, Virtual Console로 값을 바꿔 수신을 확인

| 항목 | API | 결과 | 비고 |
| --- | --- | --- | --- |
| 누적 주행거리 | `Odometer.getCurrentDistanceDriven`, `registerDistanceDriven` | 됨 | km(Float). 콘솔 변경이 바로 이벤트로 옴 |
| 기어 | `Gear.getCurrentGearSelectionState`, `registerGearSelection` | 됨 | P·R·N·D 모두 이벤트 수신. 권한 `CAR_DRIVE` |
| EV 배터리 SOC | `EvBattery.getEvBatteryStateOfCharge`, `registerEvBatteryStateOfCharge` | 됨 | %. 권한 `CAR_ENERGY` |
| 연료 잔량 | `Fuel.getFuelPercent` | 안 됨 | EV 에뮬레이터라 속성 미지원(`IllegalArgumentException`). `UNAVAILABLE`로 표시 |
| 주행 모드 | `DrivingMode.getCurrentDrivingModeState` | 안 됨 | 속성 미지원 |
| 경고등 | `Safety.getTelltalesInfo` | 안 됨 | `SecurityException`. SDK 소스에 `PRIVATE_CAR_SAFETY`(signature\|privileged)로 표기. 일반 앱 사용 불가 |
| 주행 상태 | AAOS `CarDrivingStateService` | 됨 | 40km/h에서 MOVING, 정지·P에서 PARKED |
| 주행 중 화면 | Pleos 플랫폼 | 앱 전체 차단 | D 기어면 속도 0이어도 "주행 중에는 사용할 수 없습니다" 화면으로 가려짐. P로 돌아오면 복귀 |
| 목적지 설정·조회 | `NaviHelper.requestRoute`, `getDestinationInfo` | 됨 | `requestRoute`로 경로 안내가 시작되고 `onRouteStarted`(거리·시간·이름) 수신. 권한 `NAVI_ROUTE_SEARCH`. 이때 `onDestinationChanged`는 오지 않으므로 어댑터가 `onRouteStarted`에서 목적지를 다시 조회한다. 경로를 취소하면 빈 `onDestinationInfo`가 온다. 내비 화면에서 직접 시작한 경로는 `onRouteStarted`·`onDestinationChanged`·`onNavigationStatus`가 오지 않고, `cancelRoute`로 지워지지도 않는다. 그래서 어댑터가 10초마다 목적지를 다시 읽는다. 목적지가 없으면 위경도 0·이름 공백인 빈 값 |
| 도착 이벤트 | `NaviHelperEventListener.onDestinationArrived` | 에뮬레이터에서 재현 불가 | 내비는 GNSS NMEA로 위치를 받는데, 에뮬레이터 GNSS HAL이 체크섬이 틀린 NMEA를 내보내 내비가 버린다(`NMEA checksum mismatch`). `adb emu geo fix`·`geo nmea`·콘솔 속도 모두 내비 위치를 움직이지 못함. 실차 또는 공식 문의로 확인 |
| TTS ON_DEVICE | `TextToSpeech(ctx, Mode.ON_DEVICE).speak` | 됨 | 앱 등록 없이 재생. ready·start·done 이벤트 수신 |
| STT ON_DEVICE (파일) | `SpeechToText.sendAudio` | 됨 | 16kHz·16bit·mono PCM. "주차장 비 삼 층에 세워 두었어요…"가 "주차장 v 3층에 세워 두었어요 충전 케이블은 트렁크에 있어"로 인식. 앱 등록 불필요 |
| STT ON_DEVICE (마이크) | `SpeechToText.request` | 됨 | 사람 목소리로 "트렁크에 샘플 박스 2개가 있어요" 인식. `STT_SERVICE`만 선언했고 `RECORD_AUDIO`는 필요 없음. 에뮬레이터에서는 `adb emu avd hostmicon`으로 호스트 마이크를 켜야 함. 침묵만으로는 인식이 끝나지 않아 어댑터가 15초 뒤 닫음 |
| 오디오 포커스 | `AudioFocusRequest` `GAIN_TRANSIENT_MAY_DUCK`, `USAGE_ASSISTANT` | 됨 | TTS 서비스가 우리 포커스를 뺏지 않음. 다른 앱이 `GAIN`이나 `GAIN_TRANSIENT`를 요청하면 `LOSS`·`LOSS_TRANSIENT`를 받아 바로 멈추고 `INTERRUPTED`를 돌려줌. 소리가 그 순간 끊겼는지는 귀로 확인하지 않음 |
| 서비스 재시작 후 복구 | Vehicle, NaviHelper SDK | 됨 | `am crash`로 각 서비스를 죽이면 SDK가 다시 연결하고 리스너를 복구한다. 기어·주행거리 알림과 목적지 조회가 다시 동작(확인용 앱으로 시험). `am force-stop`이면 서비스가 다시 뜨지 않아 앱을 재시작해야 복구됨 |

개발 시 주의:

- TTS는 `initialize()` 성공 뒤에 `addEventListener()`를 불러야 한다. 먼저 부르면 SDK 내부 `UninitializedPropertyAccessException`으로 앱이 죽는다. 공식 문서의 순서 안내와 다르다.
- `Vehicle.initialize()`에는 연결 완료 콜백이 없다. 어댑터는 첫 조회가 성공할 때까지 1초 간격으로 재시도한 뒤 리스너를 등록한다.
- 주행 중에는 플랫폼이 앱 화면을 가리므로, 주행 중 화면 안내는 설계하지 않는다. 주행 중 음성 안내가 필요하면 DO 선언과 오디오 포커스를 따로 확인해야 한다.
- 경고등 자동 기록은 일반 앱 권한으로 불가능하므로 범위에서 뺀다.
- 도착은 에뮬레이터에서 재현할 수 없으므로 시연은 목적지 설정은 실제 SDK로, 도착은 화면의 "도착했어요" 버튼으로 보여 준다. Pleos 에뮬레이터에서 안내창이 뜨고 재주차 때 반복되지 않는 것을 확인했다. 대안으로 FusedLocation SDK의 근접 알림(ProximityAlert)이 있으나 미검증이다.
- STT는 "B3" 같은 층수 표기를 "v 3층"처럼 잘못 듣는다. 장소 메모는 저장 전에 화면에서 확인·수정하게 한다.

### Virtual Console 자동 조작

`tools/vc.sh`로 브라우저 없이 기어·주행거리·SOC·속도를 바꾼다. 시연 장면 재현과 회귀 확인에 쓴다. 콘솔은 주행거리를 줄이는 값을 거부한다(`traveled_distance_must_not_decrease`). 에뮬레이터를 다시 켠 뒤에는 작은 값도 들어갔다. `vc.sh`는 거부되면 오류를 출력하고 1로 끝난다. 콘솔은 브라우저 세션 heartbeat(`POST /browser/session`)가 없으면 쓰기를 `503 emulator_inactive`로 거부하므로, 스크립트가 매번 heartbeat를 먼저 보낸다.

```
adb forward tcp:8088 tcp:8088
tools/vc.sh gear Drive
tools/vc.sh odo 12345
tools/vc.sh soc 42
tools/vc.sh speed 40
```

## 근거와 확인할 사항

| 항목 | 공식 문서에서 확인한 내용 | 남은 검증 |
| --- | --- | --- |
| Vehicle | 차량 상태 SDK와 capability 확인 절차 제공 | 각 경고등·공조·탑승 신호의 개별 지원·권한·변경 이벤트. ‘Vehicle 지원’이 모든 신호 지원을 뜻하지 않음 |
| NaviHelper | 목적지·안내·경유지 등 내비 연동 인터페이스 제공 | 에뮬레이터 내비 설치·연결·이벤트 수신, 도착·회전 시점, 경유 변경과 사용자 확인 |
| STT/TTS·위치·제어 | 상세 문서는 존재하지만 SDK 신청 화면에는 준비 중 항목이 있음 | 공모전 계정에 제공되는 실제 권한·일정·대체 경로 |
| Gleo | AppSchema actionCalls 최대 3개, 스키마 검토·승인 후 활성화 | 공모전 승인 절차·기간, 재설치·등록, 입력 인자 형식, 호출자 인증과 콜백 범위 |
| 오디오 | Android 오디오 포커스와 Pleos 정책 준수 필요 | 내비 안내/전화/Gleo 동시 동작, 즉시 중단·취소·상황 재평가 후 재개 |
| 주행 제한 | CarUxRestrictions 이벤트에 따라 화면·입력 제한 적용 | 주차 판정·실제 제한값, 정체 정차 구분, 앱 카테고리와 DO 승인 |
| 졸음·휴식 | 일반적인 차량 SDK 제공만 확인 | 졸음 경고 신호 노출 여부, 시간 수집 수명주기, 쉼터 POI·경유지 API. 자체 졸음 감지는 구현 범위 아님 |
| 절전·시동 종료 | 차량 앱 생명주기에 맞춘 동작 필요 | 앱이 살아 있는 시간·복귀 이벤트·종료 직전 알림 가능 여부. 시동 종료 뒤 상시 실행을 가정하지 않음 |
| 성능 | 비내비 앱 주행 CPU 5% 미만, 정차 25% 이하, RAM 400MB 이하 | 측정 방식·기기·동시 앱 조건, 실제 프로파일링. 아직 측정값 없음 |

앱은 Vehicle 2.3.2와 NaviHelper 3.0.5를 쓴다. Pleos Connect Emulator v2.1.0에서 동작을 확인한 버전으로 고정했다.

## 10/28 설명회 질문

1. 참가자에게 STT/TTS·위치 SDK를 언제, 어떤 권한으로 제공하나요? Gleo 호출과 응답만으로 핵심 음성 흐름을 구현할 수 있나요?
2. Pleos 에뮬레이터에서 NaviHelper 목적지·회전·도착 이벤트를 재현할 수 있나요? 사용할 내비 앱과 테스트 경로는 무엇인가요?
3. 백그라운드 구독·시동 종료·절전·복귀 허용 범위와 제출 시 앱 카테고리는 무엇인가요?
4. Gleo 스키마 승인 소요 기간과 공모전용 테스트 등록 경로가 있나요? 문서 예제의 ACTION_PARAM JSON과 본문의 extras 중 실제 전달 형식은 무엇인가요?
5. 내비 발화가 앱에 주는 오디오 포커스 변화, TTS 용도 설정, 중단·재개 시 권장 규칙은 무엇인가요?
6. 졸음 경고·탑승 관련 신호는 어떤 차종·권한·에뮬레이터에서 열리나요? 쉼터 검색/경유 추가는 지원되나요?
7. 주행 CPU·RAM의 측정 도구·구간은 무엇인가요? Gleo 호출자 인증과 응답 수신자 제한은 어떻게 구성하나요?

## 실행 증거 기록 양식

환경·커밋 / SDK·서비스 버전 / capability·권한 / 재현 절차 / 관찰된 결과 / 실패·미지원 / 로그·영상 위치(개인정보 제거) / 담당·날짜.

첫 실험은 NaviHelper 연결과 목적지·도착 이벤트입니다. 실패하면 실패 지점과 공식 지원 문의에 필요한 최소 재현을 남깁니다. 샘플 화면의 버튼 동작을 SDK 성공으로 기록하지 않습니다.

## 공식 자료

- [SDK 개요](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/), [SDK 신청·제공 상태](https://document.pleos.ai/docs/pleos-only/create-my-project)
- [Vehicle](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro), [NaviHelper](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)
- [API 호환성](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability)
- [Gleo 스키마](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability), [제출·승인](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/deliver-schema), [호출 예제](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/examples)
- [오디오 포커스](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development)
- [주행 제한](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- [성능·리소스](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/performance-resource-mgmt)
- [앱 완성도·권한 심사](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-functionality-performance-stability)
