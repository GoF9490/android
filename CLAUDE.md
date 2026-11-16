# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 현재 상태

PRD §18의 **A0(환경·골격) 단계가 거의 끝났다.** Android Studio·SDK·에뮬레이터와 Gradle 프로젝트 골격(Compose·Hilt·Retrofit 등 의존성, 다크 테마, 빈 `MainActivity`)이 있고 `assembleDebug`·`installDebug`·`lintDebug`가 통과한다. 인증·목록·업로드·플레이어 등 기능 코드는 아직 없으며(A1~), 단위 테스트 소스도 아직 없다(`testDebugUnitTest`는 `NO-SOURCE`). 남은 A0 항목: `core` 공통 코드(`ApiException`·에러 정규화), 변경분 커밋.

이 저장소는 온프레미스 음원 스트리밍·아카이빙 MVP("re-dubb")의 **Android 앱**만 다룬다. 백엔드(Spring)·Nginx·docker-compose는 `../backend`, `../docker-compose`, `../nginx` 소관이며 여기서 구현하지 않는다. 필요한 변경은 구현하지 않고 PRD §15에 요청 사항으로 기록한다. 디자인·동작 참고가 필요하면 `../frontend/`(React 웹, 읽기 전용)를 본다.

## 문서 체계 (`.claude/docs/`)

- 이 저장소의 문서는 `PRD.md`(Android 기준 문서)와 `API.md`(백엔드 API 명세) 두 개뿐이다. 작업 전에 `PRD.md`의 해당 절을 읽는다. 초안 `PRD-03`을 대체하며 달라진 점은 PRD §16에 있다.
- 우선순위: `API.md` > `PRD.md`. 충돌이 보이면 임의로 판단하지 말고 사용자에게 확인한 뒤 PRD §16에 기록한다. 더 확인이 필요하면 `../backend/music`(백엔드 구현)과 `../nginx/.claude/docs/`(인프라 문서)를 읽기만 한다.
- `PRD-01`~`PRD-07`·`PRD-frontend.md`는 이 저장소에서 삭제했다(2026-11-16). 사본은 `../nginx/.claude/docs/`·`../frontend/.claude/docs/`에, 원문은 git 기록(커밋 `b556e91`)에 있다. 그 문서 머리말의 "`PRD.md` §7"은 구 백엔드 인덱스를 뜻하며 이 저장소와 무관하다.
- 앱이 웹과 같은 동작·문구를 따를 때는 웹 PRD(`../frontend/.claude/docs/PRD.md`)를 읽기만 한다(§3.4 에러 문구, §4.4 로그인 오류, §16 결정 이력).
- `ROADMAP.md`는 아직 없다. PRD §18 완료 후 `write-roadmap` 스킬로 작성하고, 이후 `scripts/run-roadmap.sh`(phase별로 `claude -p` 세션 순차 실행)와 `/docs:update-roadmap`을 쓴다.

## 명령

PowerShell에서는 `.\gradlew.bat`, Git Bash에서는 `./gradlew`.

```bash
./gradlew assembleDebug                        # 디버그 APK (app/build/outputs/apk/debug/)
./gradlew installDebug                         # 연결된 기기·에뮬레이터에 설치
./gradlew testDebugUnitTest                    # 단위 테스트
./gradlew testDebugUnitTest --tests "com.example.musicapp.*SomeTest"   # 단일 테스트
./gradlew lintDebug                            # Android Lint (오류가 있으면 빌드 실패)
./gradlew connectedDebugAndroidTest            # UI 테스트 (기기 필요)

adb reverse tcp:80 tcp:80                      # 기기 localhost:80 → PC Nginx (에뮬레이터·기기 재연결 시 재실행)
emulator -avd Medium_Phone_API_37.0            # 에뮬레이터 실행 (또는 Studio Device Manager)
adb shell am start -n com.example.musicapp/.MainActivity
```

- 환경: Windows 11, Android Studio 설치(`C:\Program Files\Android\Android Studio`), SDK `%LOCALAPPDATA%\Android\Sdk`(Platform 37.0, build-tools 36.0.0, cmdline-tools 23.0.0), AVD `Medium_Phone_API_37.0`(Android 17). Gradle은 PATH의 JDK 21로 실행하고 JVM 타깃은 17이다. `ANDROID_HOME`·PATH는 새 터미널부터 적용되며, 빌드는 `local.properties`의 `sdk.dir`로도 동작한다.
- `local.properties`(gitignore)에 `sdk.dir`와 `musicBaseUrl`(기본 `http://localhost`, `BuildConfig.BASE_URL`로 주입)이 있다. `sdk.dir`는 `C\:/Users/...` 형식이어야 lint를 통과한다.
- 앱 동작 확인에는 docker 스택(Nginx 80)이 PC에서 떠 있어야 한다. 에뮬레이터는 평소 검증용, 백그라운드·잠금 화면·블루투스 재생은 실기기로 확인한다.
- **AGP 9.x는 Kotlin 내장**이라 `org.jetbrains.kotlin.android` 플러그인을 적용하면 빌드가 실패한다. 의존성 버전의 원본은 `gradle/libs.versions.toml`이다.
- `sdkmanager`는 deprecated(Android CLI로 위임, 사용 통계 안내 출력). 쓸 때 `--no-metrics`를 붙인다.
- 에뮬레이터에는 `curl`이 없다. 서버 응답 확인은 기기 Chrome에서 `http://localhost/...`를 열어 본다.

## 아키텍처에서 놓치기 쉬운 것

- **ExoPlayer는 `PlaybackService`(MediaSessionService)가 소유**하고, UI는 `MediaController`로만 제어한다. Activity에서 ExoPlayer를 직접 만들면 화면이 꺼질 때 재생이 끊긴다.
- **`/api/tracks/{id}/play`는 재생 시작 클릭 1회당 1번만** 호출한다(호출마다 STREAM 이력이 쌓임). 재구성·회전·반복·시크·일시정지·같은 곡 재클릭(토글)에서는 호출하지 않는다. `/play`는 READY가 아니면 409, `/download`는 상태와 무관하다.
- **로그인:** Custom Tabs로 `/oauth2/authorization/google?redirect_uri=musicapp%3A%2F%2Foauth2%2Fcallback` → 백엔드가 `musicapp://oauth2/callback?token=` 또는 `?error=`로 302. `MainActivity`는 `singleTop`이며 `onCreate`와 `onNewIntent` 양쪽에서 처리하고, 처리 후 intent data를 비운다. `redirect_uri`는 백엔드 화이트리스트와 **문자열 완전 일치**여야 한다. 토큰은 로그에 남기지 않는다.
- **`/api/auth/me`만 401을 반환**한다. 나머지 API는 무효 토큰도 익명 처리한다. 로그아웃은 서버 호출 없이 토큰 삭제.
- **BASE_URL과 Google 로그인:** 백엔드가 요청 `Host`로 OAuth 콜백 URI를 계산하므로 `10.0.2.2`·사설 IP로 접속하면 Google 로그인이 실패할 수 있다. 기본은 `adb reverse` + `http://localhost`(PRD §12.3). 평문 HTTP는 debug 전용 network security config로만 허용한다.
- **업로드:** 태그가 없으면 placeholder만 보여 주고 **빈 값은 파트를 보내지 않는다**(서버 fallback 보존). 내장 앨범자켓은 미리보기 전용이며 전송하지 않는다. 파일은 메모리에 통째로 올리지 않고 `ContentResolver` 스트림으로 전송한다.
- **변환 상태 폴링:** 변환 중(PENDING/PROCESSING) 행만 5초마다 상세 조회하고 목록 상태를 갱신한다. 30분 상한, 백그라운드에서는 중단한다. 목록은 Paging3 대신 ViewModel이 직접 관리한다.
- **다운로드:** `DownloadManager`에 `Authorization` 헤더를 붙인다(웹과 달리 이력에 사용자 귀속).
- 에러는 서버 `message` 대신 클라이언트 메시지(PRD §3.4)를 쓴다. `streamUrl`·`artworkUrl`은 상대경로면 `BASE_URL`을 붙이고 절대 URL이면 그대로 쓴다.

## 코딩 규칙

들여쓰기 4칸(Kotlin 공식 스타일), 변수·함수 camelCase, 함수명은 동사로 시작(`getTracks`, `handleClick`), 클래스·Composable PascalCase. 코드 주석·문서·커밋 메시지는 한국어, 식별자는 영어. 스택: Kotlin, Jetpack Compose(Material 3), Media3, Retrofit + OkHttp + kotlinx.serialization, Coil, Hilt(KSP), DataStore, applicationId `com.example.musicapp`. `.claude/rules/spring-java.md`는 `**/*.java` 전용이라 이 저장소(Kotlin)에는 적용되지 않는다.

## Git

커밋은 `/git:commit`(이모지 + 컨벤셔널, 한국어, 원자적 단위)으로 만든다. 현재 브랜치는 `master`인데 기본 브랜치 설정은 `main`이라 원격에 올리기 전에 정리가 필요하다. `.gitignore`는 `.claude/skills/*`, `.claude/rules/*`, `scripts/*`를 무시하므로 이 경로는 커밋되지 않는다(`.claude/docs/`와 `CLAUDE.md`는 커밋 대상). Android용 항목(`build/`, `.gradle/`, `local.properties`, `.idea/` 등)과 `.gitattributes`(`* text=auto eol=lf`, `*.bat`은 CRLF)는 A0에서 추가했다.
