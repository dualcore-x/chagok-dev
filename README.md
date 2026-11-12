# 차곡 · 공용차 인계노트

공용차를 바꿔 타도, 차량에 남은 문제와 업무가 다음 운전자에게 끊기지 않고 이어지는 Pleos Connect 차량 앱입니다. "차곡"은 차(車)에 기록이 차곡차곡 쌓여 다음 사람에게 이어진다는 뜻입니다.

- 반납하는 운전자가 해야 할 일·참고 정보를 차량·장소·물건 단위로 남기고, 그때의 주행거리·배터리가 차량에서 자동으로 붙습니다.
- 다음 운전자는 프로필을 고르면 처음 보는 것과 바뀐 것만 요약해서 받고, 내비 목적지에서 할 일이 맨 앞에 옵니다.
- 확인한 사람, 끝낸 사람과 시각이 운전자별로 남고, 끝나지 않은 일만 그다음 운전자에게 넘어갑니다.
- 주차(P) 중에만 입력을 받고, 말로 남길 수 있습니다.

## 테스트 환경

| 항목 | 내용 |
| --- | --- |
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (Android 14 · API 34, arm64), 화면 2560×1440, 160dpi. Android Studio Device Manager에서 **Cold boot**, RAM 4GB |
| 화면 규격 | 2560×1440 가로, 160dpi(에뮬레이터 기본값). 앱은 왼쪽 계기 영역과 위·아래 시스템 바를 뺀 약 1680×1210dp 영역에 뜨고, 이 크기에서 글자·버튼을 화면에 맞게 키웁니다 |
| 개발 PC | macOS Apple Silicon. Windows·Linux는 [공식 설치 안내](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)의 x86 이미지 주소를 씁니다 |
| 빌드 | JDK 17 또는 21, Android SDK Platform 35 · Build Tools 35.0.0. JDK 25에서는 Gradle 8.13이 실행되지 않습니다 |
| Pleos SDK | Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech·SpeechToText 3.3.1 (ON_DEVICE, 앱 등록·네트워크 불필요) |
| 권한 | `CAR_DRIVE`, `CAR_ENERGY`, `NAVI_ROUTE`, `TTS_SERVICE`, `STT_SERVICE` |
| 실차 | 확인하지 않았습니다 |

Pleos 차량 서비스가 없는 기기(표준 Android Automotive 에뮬레이터 등)에서는 차량 값과 음성이 시뮬레이션으로 동작하고, 화면 상단에 "시뮬레이션"으로 표시됩니다. SDK별 확인 결과는 [SDK 확인](docs/sdk-checks.md), 화면 흐름 검증 기록은 [개발 환경](docs/setup.md)에 있습니다.

CLI로 빌드하기 전에 SDK 경로(`local.properties`의 `sdk.dir` 또는 `ANDROID_HOME`)를 지정합니다. 자세한 내용은 [개발 환경](docs/setup.md)에 있습니다.

## 필요한 설정

1. Pleos 에뮬레이터 설치: [개발 환경](docs/setup.md)의 "Apple Silicon Mac 설치 절차"를 따릅니다. 시스템 이미지는 SDK Update Site로 받습니다.
2. 차량 값 바꾸기: Virtual Console을 씁니다.
   ```sh
   adb forward tcp:8088 tcp:8088
   tools/vc.sh gear Park      # Park·Reverse·Neutral·Drive
   tools/vc.sh odo 30000      # 주행거리 km. 지금 값보다 작게는 바꿀 수 없습니다
   tools/vc.sh soc 80         # 배터리 %
   ```
   브라우저에서는 `http://127.0.0.1:8088/`입니다.
3. 목적지: 인수 화면의 목적지 칩에서 **한빛상사** 또는 **본사 주차장**을 고르면 그곳 인계가 맨 앞에 옵니다. 두 곳은 시연용으로 앱에 등록된 장소라 에뮬레이터 내비에서는 검색되지 않습니다.
   - Pleos 내비에 경로가 잡혀 있으면 그 목적지 이름이 10초 안에 "내비 목적지"로 반영되고, 이름에 등록 장소 이름이 들어 있을 때만 인계가 연결됩니다.
   - 내비 경로는 다음 운전자가 사용을 시작할 때도 남아 있으니, 시험이 끝나면 경로를 지우거나 에뮬레이터를 다시 켭니다.
   - 에뮬레이터는 내비 도착 신호를 만들지 못하므로, 도착은 앱의 **도착했어요**로 확인합니다.
4. 말로 남기기: `adb emu avd hostmicon`으로 PC 마이크를 켭니다.

## 실행 방법

제출한 APK를 설치할 때는 `adb install apk/DualCore_Chagok_release.apk`를 실행합니다(아래 "APK" 참고).

소스로 빌드할 때는 아래 명령을 씁니다.

```sh
./gradlew :app:installDebug          # 개발용(시연 데이터 넣기 등 개발 도구 포함)
./gradlew :app:assembleRelease       # 서명된 release: app/build/outputs/apk/release/app-release.apk
```

1. **누가 운전하나요?**에서 운전자를 골라 사용을 시작합니다.
2. **인계 남기기**로 남기고 **반납하기**로 넘깁니다.
3. 다른 운전자로 시작하면 받을 인계가 요약됩니다. **확인했어요**, **처리 완료**, **고치기**로 처리합니다.

release 서명키는 저장소 밖에 둡니다. 처음 한 번 `tools/make-signing-key.sh`를 실행하면 `~/.drive-companion/`에 만들어집니다. 키가 없으면 미서명 `app-release-unsigned.apk`가 만들어지며 설치할 수 없으니, 소스로 확인할 때는 `installDebug`를 씁니다.

## APK

제출용 release APK는 `apk/DualCore_Chagok_release.apk`입니다.

| 항목 | 값 |
| --- | --- |
| 빌드한 소스 | 이 저장소의 소스 (패키지 경로를 `com.dualcore.chagok`으로 바꾼 뒤 `./gradlew :app:assembleRelease`) |
| 앱 ID · 버전 | `com.dualcore.chagok` · versionName 1.0.0 · versionCode 1 · minSdk 28 · targetSdk 35 |
| 서명 | APK Signature Scheme v3, 인증서 SHA-256 `5440cde4fd78a24d2d64ea98c6fd498ef30a07f002e51ae1dbe6a0f9387e1e07` |
| 파일 SHA-256 | `37a25f5e54e02ba555085bad38041fcf0f1d278daf5b2130608689120f7f3b4b` |
| 크기 | 42.6MB |

받은 파일이 같은지 확인할 때는 `shasum -a 256 apk/DualCore_Chagok_release.apk`의 결과를 위 값과 비교합니다.

## 문서

| 문서 | 내용 |
| --- | --- |
| [제품 범위](docs/product.md) | 1차 제출 범위, 대표 시연 흐름, 제외 기능 |
| [구조와 데이터 계약](docs/architecture.md) | 모듈, 데이터 계약, 인계 규칙, 저장 방식 |
| [개발 환경](docs/setup.md) | 설치 절차, 버전, 검증 기록 |
| [SDK 확인](docs/sdk-checks.md) | Pleos 에뮬레이터에서 SDK별로 되는 것과 안 되는 것 |
| [시연 대본](docs/demo-script.md) | 시연 영상 장면과 준비 |
| [회귀 확인](docs/regression.md) | 제출 APK를 바꿀 때마다 다시 돌리는 시나리오와 결과 |
| [확인용 앱](tools/probe/README.md) | 목적지 설정·취소, 오디오 포커스 시험용 개발 도구 |
| [로드맵](docs/ROADMAP.md) | 일정과 진행 상태 |

## 사용한 글꼴

화면 글꼴은 [Pretendard](https://github.com/orioncactus/pretendard) Std(자주 쓰는 한글 2,350자 경량판)입니다. SIL Open Font License 1.1이며, 라이선스 전문은 `app/src/main/assets/licenses/Pretendard-OFL-1.1.txt`에 있고 APK에도 함께 들어갑니다.
