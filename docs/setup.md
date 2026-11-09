# 개발 환경과 실행

## 고정한 구성

| 항목 | 버전·선택 |
| --- | --- |
| 언어·UI | Kotlin 2.1.20 / Jetpack Compose |
| Gradle / Android Gradle Plugin | 8.13 / 8.9.2 |
| Compose BOM | 2025.04.01 |
| Activity / Lifecycle | 1.10.1 / 2.8.7 |
| kotlinx.serialization / coroutines | 1.8.1 / 1.10.2 |
| min / target / compile SDK | 28 / 35 / 35 |
| Build Tools | 35.0.0 (AGP 기본) |
| 팀·CI 권장 JDK | 17 |
| JVM 바이트코드 | 17 |
| 로컬 검증 환경 | macOS arm64, Homebrew OpenJDK 23.0.2 |
| Pleos Emulator·SDK | 앱 담당 환경 미설치. 연동 담당 환경에서 확인 중 |

AGP 8.9는 API 35, JDK 17과 Gradle 8.11.1 이상을 요구합니다. Gradle 8.13으로 로컬 JDK 23도 사용할 수 있게 고정했습니다. 신규 버전을 무조건 따라가지 않고 첫 공동 실행에서 Pleos와의 호환성을 확인한 뒤 변경합니다. [AGP 공식 호환성](https://developer.android.com/build/releases/agp-8-9-0-release-notes)

## 처음 받았을 때

1. Android Studio와 JDK 17을 준비합니다. Gradle JDK를 17로 지정합니다.
2. SDK Manager에서 Android SDK Platform 35, Build Tools 35.0.0, Platform Tools를 설치합니다.
3. 저장소 루트를 Android Studio에서 열고 Gradle Sync를 실행합니다.
4. `local.properties`에 자신의 Android SDK 경로를 설정합니다. 이 파일은 커밋하지 않습니다.

macOS 기본 설치 경로 예시(사용자명은 바꿔야 합니다):

```properties
sdk.dir=/Users/YOUR_USER/Library/Android/sdk
```

CLI에서는 SDK 경로를 `ANDROID_HOME`으로 지정해도 됩니다. JDK는 `JAVA_HOME`으로 지정합니다. Gradle 8.13은 JDK 25에서 실행되지 않으므로, Android Studio에 내장된 JDK가 25라면 Gradle JDK를 17 또는 21로 바꿉니다. 저장소에 특정 개발자의 절대 경로를 고정하지 않습니다.

```sh
./gradlew :core:test :app:lintDebug :app:assembleDebug :app:assembleRelease
./gradlew :app:installDebug
adb shell am start -n io.github.beomhui.drivecompanion.debug/io.github.beomhui.drivecompanion.MainActivity
```

설치·시작 명령에는 실행 중인 에뮬레이터나 연결된 기기가 필요합니다. Windows에서는 `gradlew.bat`를 사용합니다. 별도 Gradle 전역 설치는 필요 없습니다.

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- 단위 테스트: `core/build/reports/tests/test/index.html`
- lint: `app/build/reports/lint-results-debug.html`
- release 서명: `tools/make-signing-key.sh`를 한 번 실행하면 키가 저장소 밖 `~/.drive-companion/`에 만들어지고, 이후 `assembleRelease`가 서명된 `app-release.apk`를 만듭니다. 키가 없으면(CI 포함) 지금처럼 `app-release-unsigned.apk`를 만듭니다. 이 폴더는 따로 백업합니다. 키를 잃으면 설치된 앱을 업데이트할 수 없습니다.

## 화면에서 확인할 것

debug 빌드의 프로필 화면 아래 개발 도구에서 **시연 데이터 넣기**를 누르면 김하늘님이 인계 3건을 남기고 반납한 상태가 됩니다.

1. 이도윤으로 시작 → "새로 받은 인계 3" → 하나 확인, 샘플 박스는 처리 완료(메모 선택)
2. 반납 → 앱 강제 종료 후 다시 실행 → 박서연으로 시작 → 와이퍼만 새 인계, 샘플은 최근 처리한 일
3. 참고 정보를 고친 뒤 다른 운전자로 시작 → "내용 바뀜"으로 다시 표시
4. 시뮬레이션의 **주행 중으로 바꾸기** → 상단 "주행 중", 입력 버튼 비활성
5. 반납하지 않고 다른 운전자 선택 → 대신 종료 확인 대화상자
6. 기록을 지우고 시연 데이터를 다시 넣은 뒤 이도윤으로 시작 → 인수 요약 아래 시뮬레이션의 **내비 목적지: 한빛상사** → 맨 위 요약에 "이번 목적지 한빛상사에서 할 일이 1건", 샘플 박스가 "이번 목적지" 섹션으로 이동
7. **주행 중으로 바꾸기 → 주차로 바꾸기**만으로는 도착 안내가 뜨지 않음 → **주행 중으로 바꾸기 → 내비 도착 신호 → 주차로 바꾸기**에서 "한빛상사에 도착했어요" → "처리 완료로 기록". 다시 주차해도 같은 안내는 뜨지 않음
8. 내비 없이: 목적지 칩에서 **본사 주차장** 선택 → **도착했어요** → 충전 케이블 참고 정보 안내. 이 상태에서 인계 남기기 → 물건을 고르면 대상 장소가 본사 주차장으로 미리 선택됨

## Pleos 공통 환경 준비

1. 각자 Pleos 계정을 준비하고 공동 프로젝트·팀 접근·SDK 권한을 확인합니다.
2. 공식 설치 가이드에 따라 OS에 맞는 Pleos Connect Emulator와 Virtual Console을 준비합니다. 문서 안내는 Emulator 2.1.0 / API 34이며 실제 제공 버전을 기록합니다.
3. 앱 설치·런처 표시·화면·주행 제한 동작을 확인합니다. compileSdk 35와 기기 API 34는 별개이고 minSdk 28 이상에서 설치 대상이 됩니다.
4. NaviHelper 목적지·도착 이벤트를 먼저 재현하고 실제 접근 가능한 SDK 버전을 고정합니다.
5. Vehicle 상태 구독과 Gleo·음성·오디오 포커스를 차례로 연결합니다.

### Apple Silicon Mac 설치 절차 (2026-11-04 연동 담당 실측)

1. Android Studio를 설치하고 한 번 실행해 초기 설정을 마칩니다.
2. Settings > Languages & Frameworks > Android SDK > **SDK Update Sites**에서 `+`로 추가합니다.
   - Name: `Pleos Connect System Image`
   - URL: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   - 추가 후 **Apply**를 눌러야 저장됩니다. 누르지 않으면 이미지 목록에 나오지 않습니다.
3. **SDK Platforms** 탭에서 Show Package Details를 켜고, Android 14 아래 `Pleos Connect v2.1.0 APIs arm64 System Image`를 설치합니다. 42dot SDK 라이선스에 각자 동의합니다. 약 4.7GB를 받고 18GB로 풀립니다.
4. Device Manager > Create Device > Automotive > `Pleos Connect v2.1.0`을 고릅니다.
   - API는 **API 34**, Services는 **Show All**로 바꿔야 Pleos 이미지가 보입니다. 기본값(API 35, Google APIs)으로 두면 다른 이미지를 새로 받습니다.
   - Additional settings: Boot mode **Cold**(Quick이면 VHAL을 못 씀), RAM **4GB**.
5. CLI 빌드는 JDK 17 또는 21로 합니다(`brew install openjdk@21`). Android Studio 내장 JDK 25로는 Gradle 8.13이 실행되지 않습니다.
6. Virtual Console: `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/` 또는 `tools/vc.sh`.
7. STT를 마이크로 시험할 때만 `adb emu avd hostmicon`으로 호스트 마이크를 켭니다.
8. 에뮬레이터와 Gradle을 함께 돌리면 메모리가 부족해 Gradle 데몬이 죽을 수 있습니다(24GB Mac에서 1회 발생). 다시 실행하면 됩니다.

[공식 에뮬레이터 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator) · [프로젝트·권한](https://document.pleos.ai/docs/pleos-only/create-my-project)

## 로컬 검증 기록 · 2026-10-25

Android Studio가 설치되어 있지 않아 공식 Android Command-line Tools를 작업용 임시 디렉터리에 설치했습니다. 프로젝트의 전역 SDK 경로나 IDE 설정은 변경하지 않았습니다. CLI와 Gradle 배포 파일의 공식 SHA-256을 확인했습니다. 일반적인 개발은 위의 정식 SDK 설치 경로를 사용합니다.

빌드·검사 결과:

- 검증 명령: `:core:test :app:lintDebug :app:assembleDebug :app:assembleRelease`
- 로컬 실행 결과: 단위 테스트 5개 통과, debug/release APK 빌드 성공, lint 오류 0개. 의존성 최신 버전 안내 경고는 남겨두며 현재 조합을 유지합니다.
- 미검증: Android/Pleos 에뮬레이터 UI 실행, 실차, Gleo/SDK 권한, 실제 음성, 성능 측정, 연동 담당 환경

GitHub Actions도 같은 명령을 JDK 17에서 실행하도록 구성했습니다. CI 성공 여부와 SDK 런타임 검증은 별개입니다.

## 로컬 검증 기록 · 2026-11-03 (앱 담당)

- 환경: macOS 26.6 arm64, Homebrew OpenJDK 23.0.2, Android SDK `~/Library/Android/sdk`(Platform 35, Build Tools 35.0.0, Emulator 37.2.13)
- 명령: `:core:test :app:lintDebug :app:assembleDebug :app:assembleRelease` — 성공. core 단위 테스트 15개 통과(HandoverServiceTest 12, SpeechPolicyTest 3). lint 오류 0개, 버전 업데이트 안내 경고만 남음
- 화면 실행: **Pleos 에뮬레이터가 아니라** Google 표준 Android Automotive API 34 이미지(`android-34-ext9;android-automotive;arm64-v8a`, 1024×768)에서 debug APK로 확인
  - 시연 데이터 → 이도윤 인수 요약 3건 → 와이퍼 확인·샘플 처리 완료(메모) → 반납 → 앱 강제 종료·재실행 → 박서연: 와이퍼와 B가 확인하지 않은 참고 정보만 새 인계, 샘플은 최근 처리한 일
  - 반납하지 않은 세션에서 다른 운전자 선택 → 대신 종료 대화상자 → 시작
  - 장소 참고 정보 작성·저장, 시뮬레이션 주행 전환 시 입력 잠금
- 미검증: Pleos 에뮬레이터·실차, 실제 차량 값과 주행 제한, 한글 입력 시 레이아웃(adb로는 영문만 입력), release 설치(미서명)

## 첫 통합 확인 · 2026-11-05 (연동 담당, Pleos 에뮬레이터)

- 환경: Pleos Connect Emulator v2.1.0(API 34, arm64, cold boot), OpenJDK 21, 브랜치 `feat/pleos-vehicle-adapter`(PR #4) debug APK, 앱 데이터 초기화 후 시작
- 차량 값: 실제 어댑터 사용(상단 "에뮬레이터 · 차량 데이터"). 콘솔에서 주행거리 30,000km·SOC 80%로 두고 시작
- 흐름과 결과(모두 통과)
  - A(김하늘): 시연 데이터로 인계 3건(와이퍼 중요 작업, 샘플 박스 작업, 충전 케이블 참고 정보)을 남기고 반납. 노트마다 "주행거리 30,000km · 배터리 80%"가 실제 SDK 값으로 기록됨
  - B(이도윤): 3건이 새 인계로 표시. 내비에 설정된 실제 목적지(한빛상사, `requestRoute`)가 "내비 목적지"로 잡혀 샘플 박스가 "이번 목적지" 섹션에 먼저 나옴. 샘플 박스 처리 완료, 와이퍼·케이블 확인 → D 주행·주행거리 30,042km·P → 반납
  - 앱 강제 종료·재실행 → C(박서연): 와이퍼·케이블이 새 인계로 표시(B의 확인이 C에게 승계되지 않음). 샘플 박스는 "최근 처리한 일"에만 있음
  - C가 케이블 참고 정보 본문을 수정·확인 후 반납 → B 재시작: 케이블은 "내용 바뀜"(수정 시각 표시)으로 다시 표시, 와이퍼는 "계속 남은 중요한 일"로 표시
- 참고: 반납 확인 대화상자의 "반납하기"를 누르지 않고 앱을 끄면 세션이 남아 다음 운전자 선택 때 "대신 종료" 대화상자가 뜸(의도한 동작)
- 미검증: release 설치(서명 전), 도착 이벤트(에뮬레이터 GNSS 문제, `docs/sdk-checks.md`)

## release 설치 확인 · 2026-11-06 (연동 담당, Pleos 에뮬레이터)

- 실제 키로 서명한 release APK(`apksigner` 통과, v3 서명)를 새로 설치: 제품 화면과 "에뮬레이터 · 차량 데이터"가 나오고 개발 도구는 보이지 않음
- 인계를 남긴 뒤 `adb install -r`로 업데이트, 앱 강제 종료·재실행: 사용 세션, 인계 본문, "주행거리 30,000km · 배터리 80%"가 그대로 남음
- 참고: 명령줄(`emulator -avd ... -no-snapshot-load`)로 켰을 때는 부하가 100 이상으로 올라 업데이트 설치가 끝나지 않았고, Device Manager로 켜니 부하 3대로 정상이었음. 원인은 확인하지 못했으므로 시연·테스트는 Device Manager로 켠다
- 미검증: 실차 설치, 한글 입력

## 데이터와 자격 증명

API 키·서명키·Pleos 자격 증명·실제 사용자 기록은 커밋하지 않습니다. 현재 앱에는 네트워크나 마이크·위치 권한을 선언하지 않았습니다. 공용 모델 키를 APK에 넣지 않으며 자체 LLM 연결 방식은 운영 모델과 함께 결정합니다.
