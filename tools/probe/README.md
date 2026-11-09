# Pleos Probe (개발·시연용 확인 앱)

SDK 값을 직접 읽고, 내비 경로와 다른 앱의 오디오 포커스를 adb로 만들어 내는 작은 앱입니다. 제출 APK와는 따로 빌드하며 시연 영상에는 나오지 않게 합니다.

```sh
./gradlew :probe:installDebug
adb shell am start -S -n dev.handover.probe/.MainActivity --es cmd values      # 차량 값·경고등·목적지
adb shell am start -S -n dev.handover.probe/.MainActivity \
  --es cmd route --es lat 37.4150 --es lon 127.0985 --es name 한빛상사       # 경로 시작(requestRoute)
adb shell am start -S -n dev.handover.probe/.MainActivity --es cmd cancel      # 위로 시작한 경로 취소
adb shell am start -S -n dev.handover.probe/.MainActivity --es cmd focus --es type transient
adb logcat -s PROBE:I
```

- `cancel`은 이 앱의 `requestRoute`로 시작한 경로만 지웁니다. 내비 화면에서 시작한 경로는 지우지 못합니다.
- `focus`는 `gain`(음악처럼 계속)과 `transient`(Gleo처럼 잠깐)를 흉내 냅니다. 앱을 `am force-stop dev.handover.probe`로 끄면 포커스가 반납됩니다.
- 실행하면 확인용 앱이 앞에 뜹니다. 촬영 중에는 이 장면을 편집에서 잘라냅니다.
