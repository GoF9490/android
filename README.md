# re-dubb Android

온프레미스 음원 스트리밍·아카이빙 MVP "re-dubb"의 **Android 앱**이다. 곡 목록·검색, 스트리밍 재생(백그라운드·알림 컨트롤), 원본 다운로드, Google 로그인 후 업로드를 지원한다. 백엔드(Spring)·Nginx·docker-compose·웹 프런트는 별도 저장소(`../backend`, `../nginx`, `../docker-compose`, `../frontend`)이며 이 저장소에서 다루지 않는다.

- 스택: Kotlin, Jetpack Compose(Material 3), Media3, Retrofit + OkHttp + kotlinx.serialization, Coil, Hilt(KSP), DataStore
- applicationId `com.example.musicapp`, minSdk 26

> **앱 아이콘은 임시다.** 다크 배경(`#121212`) 위에 음표(`#FF0000`)를 그린 벡터 adaptive icon이며 최종 디자인은 정해지지 않았다(PRD §16 #54).

## 요구 환경

- Windows 11, Git Bash 또는 PowerShell
- JDK 21(PATH). Gradle이 실행에 쓰며 JVM 타깃은 17
- Android Studio, Android SDK(Platform 37.0, build-tools 36.0.0, cmdline-tools)
- 에뮬레이터 AVD `Medium_Phone_API_37.0`(Android 17) 또는 실기기

## 설정

프로젝트 루트의 `local.properties`(git에 올리지 않는다)에 다음을 둔다.

```properties
sdk.dir=C\:/Users/<사용자>/AppData/Local/Android/Sdk
# 선택. 기본값은 http://localhost (BuildConfig.BASE_URL로 주입)
musicBaseUrl=http://localhost
```

`sdk.dir`는 `C\:/Users/...` 형식이어야 lint가 통과한다. 새 환경에서는 Android Studio로 한 번 열면 자동 생성된다.

## 빌드·실행·테스트

PowerShell에서는 `.\gradlew.bat`, Git Bash에서는 `./gradlew`를 쓴다.

```bash
./gradlew assembleDebug            # 디버그 APK (app/build/outputs/apk/debug/)
./gradlew installDebug             # 연결된 기기·에뮬레이터에 설치
./gradlew testDebugUnitTest        # 단위 테스트
./gradlew lintDebug                # Android Lint
./gradlew connectedDebugAndroidTest   # Compose UI 테스트 (기기 필요)

emulator -avd Medium_Phone_API_37.0   # 에뮬레이터 실행 (또는 Android Studio Device Manager)
adb shell am start -n com.example.musicapp/.MainActivity
```

## 서버 접속

앱 동작 확인에는 PC에서 docker 스택(Nginx, 80 포트)이 떠 있어야 한다. 기기의 `localhost:80`을 PC로 연결한다. 에뮬레이터·기기를 다시 연결할 때마다 재실행한다.

```bash
adb reverse tcp:80 tcp:80
```

- 기본 주소는 `http://localhost`다. 백엔드가 요청 `Host`로 Google OAuth 콜백 주소를 계산하므로 `10.0.2.2`나 사설 IP로 접속하면 Google 로그인이 실패할 수 있다.
- 평문 HTTP는 debug 빌드 전용 network security config로만 허용된다.
- 에뮬레이터에는 `curl`이 없다. 서버 응답은 기기 Chrome에서 `http://localhost/...`로 확인한다.

## 문서

| 문서 | 내용 |
|---|---|
| `.claude/docs/PRD.md` | Android 앱 기준 문서(결정 이력 §16, 통합 확인 항목 §17) |
| `.claude/docs/API.md` | 백엔드 API 명세(PRD보다 우선) |
| `.claude/docs/ROADMAP.md` | 작업 순서와 완료 조건 |
| `CLAUDE.md` | 개발 작업용 안내와 놓치기 쉬운 점 |

## 알려진 제약

- 실제 Google 로그인 전체 흐름, 실기기(백그라운드·잠금 화면·블루투스·전화)와 TalkBack 동작은 아직 확인되지 않았다(Task 7-2에서 사용자 검증).
- `lintDebug` 경고는 0건이다. `PlaybackService`의 `ExportedService`는 시스템 미디어 UI·블루투스가 바인딩해야 하므로 의도적으로 무시한다(PRD §16 #55).
