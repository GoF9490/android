# Re-dubb Android 앱 ROADMAP

**📅 최종 업데이트**: 2026-11-17
**📊 진행 상황**: Phase 3 대기 (9/28 Tasks 완료)
**참고 문서**: `.claude/docs/PRD.md`, `.claude/docs/API.md`

## 개요

온프레미스 음원 스트리밍 MVP("re-dubb")의 Android 네이티브 앱을 만든다. Google 로그인, 음원 업로드, 목록·검색, HLS 재생(백그라운드·잠금 화면 포함), 원본 다운로드를 구현한다. 백엔드·Nginx·웹은 이미 구현되어 있으며 이 저장소는 앱만 다룬다.

Phase N은 PRD §18의 A(N-1) 단계에 대응한다. 환경 세팅과 프로젝트 골격(Gradle·버전 카탈로그·다크 테마·Manifest의 권한과 딥링크·debug 평문 HTTP 설정·빈 `MainActivity`)은 이미 끝났으므로 이 로드맵에 없다.

## 공통 규칙 (모든 Task에 적용)

- 작업 전에 `CLAUDE.md`와 Task의 "관련 PRD" 절을 읽는다. 계약이 헷갈리면 `API.md`가 `PRD.md`보다 우선한다. 문서끼리 충돌하면 임의로 정하지 말고 결재를 요청한다.
- 패키지 루트는 `app/src/main/java/com/example/musicapp/`이고 구조는 PRD §10을 따른다. 단위 테스트는 `app/src/test/`, UI 테스트는 `app/src/androidTest/`의 같은 패키지 경로에 둔다.
- 완료 전 검증: `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug` (Git Bash는 `./gradlew`). 셋 다 통과해야 완료다. 단일 테스트는 `--tests "com.example.musicapp.*클래스명"`.
- 기기 확인이 필요한 Task는 에뮬레이터(`Medium_Phone_API_37.0`)에 `installDebug` 후 확인한다. 서버가 필요하면 PC에서 docker 스택(Nginx 80)이 떠 있어야 하고 `adb reverse tcp:80 tcp:80`을 먼저 실행한다. **에뮬레이터나 스택을 쓸 수 없으면 확인을 건너뛴 채 완료 처리하지 말고, 무엇을 확인하지 못했는지 보고한다.**
- AGP 9는 Kotlin을 내장한다. `org.jetbrains.kotlin.android` 플러그인을 추가하지 않는다.
- 의존성 추가·버전 변경, PRD에 없는 정책 결정은 결재 사항이다. 버전의 원본은 `gradle/libs.versions.toml`이다.
- `../`의 다른 저장소(backend, frontend, nginx)는 읽기만 한다. 필요한 변경은 PRD §15에 요청으로 기록한다.
- 토큰(JWT)과 딥링크 URI를 로그·예외 메시지·테스트 출력에 남기지 않는다. 테스트에 실제 토큰·시크릿을 넣지 않는다.
- 사용자 표시 문구는 PRD §3.4·§4.4의 클라이언트 문구를 쓴다(서버 `message` 노출 금지).
- 들여쓰기 4칸, 함수명은 동사로 시작, 주석·커밋 메시지는 한국어, 식별자는 영어. 커밋에 Claude 서명을 넣지 않는다.
- PRD에 없던 세부 결정을 내렸으면 PRD §16 결정 이력에 한 줄 추가한다.

## 미결정 사항

- ~~알 수 없는 `status` 값 처리~~ → 결정됨(2026-11-17): `TrackStatus.UNKNOWN` 추가, UI는 일반 오류 취급(PRD §16 #26). Task 011·012·019의 `when (status)`에는 `UNKNOWN` 분기나 `else`가 필요하다.
- 앱 아이콘 디자인. 현재 아이콘이 없어 lint 경고(`MissingApplicationIcon`)가 난다. → Task 026 (결재 필요)
- `DownloadManager`가 응답의 `Content-Disposition` 파일명을 적용하는지. 실측 후 저장 파일명 지정 방식을 정한다. → Task 023
- 실제 Google 계정 로그인과 실기기 검증은 사용자만 할 수 있다. → Task 008의 최종 확인, Task 028 (결재 필요)
- 현재 브랜치는 `master`, 기본 브랜치 설정은 `main`이다. 원격에 올리기 전에 정리가 필요하다.

### Phase 1: 기반 코드 ✅

- **Task 001: 공통 유틸과 단위 테스트 기반** ✅
  - 📋 `util/TimeFormat.kt`: `formatDuration(seconds)`(목록용 `m:ss`, 1시간 이상 `h:mm:ss`), `formatClock(ms)`(플레이어용 `mm:ss`, 1시간 이상 `h:mm:ss`, 음수·비정상 값은 `00:00`)
  - 📋 `util/MediaUrl.kt`: `resolveMediaUrl(baseUrl, url)` — `http://`·`https://`로 시작하면 그대로, 상대경로면 `baseUrl`과 결합(슬래시 중복·누락 처리)
  - 📋 두 유틸의 단위 테스트 작성(경계값: 0초, 59초, 60초, 3599초, 3600초, 끝 슬래시 있는 baseUrl)
  - 완료 기준: `testDebugUnitTest`가 `NO-SOURCE`가 아니라 테스트를 실행해 통과한다.
  - 의존: 없음
  - 관련 PRD: PRD.md §3.1, §6.1, §7.4

- **Task 002: API 모델과 Retrofit 인터페이스** ✅
  - 📋 `data/api/model/`: `TrackStatus`, `TrackResponse`, `TrackPageResponse`, `StreamPlayResponse`, `UserResponse`, `ErrorResponse` (PRD §3.3 코드 그대로, `@Serializable`)
  - 📋 `data/api/MusicApi.kt`: `getMe`, `getTracks(keyword?, page, size)`, `getTrack(trackId)`, `getPlayInfo(trackId)`, `uploadTrack(@Multipart parts)` — 모두 `suspend`. `keyword`가 null이면 쿼리를 보내지 않는다
  - 📋 `data/api/ApiJson.kt`: `Json { ignoreUnknownKeys = true; coerceInputValues = true }` 단일 인스턴스
  - 📋 `API.md` §3의 응답 예시 JSON을 역직렬화하는 단위 테스트(`album`·`artworkUrl`이 null인 경우, 모르는 필드가 추가된 경우 포함)
  - 완료 기준: 예시 JSON 역직렬화 테스트가 통과하고 빌드가 성공한다.
  - 의존: 없음
  - 관련 PRD: PRD.md §3.2, §3.3 / API.md §3, §4
  - 주의: [결정됨] 알 수 없는 `status`는 `TrackStatus.UNKNOWN`으로 매핑한다(PRD §16 #26). [구현] `/download`는 Retrofit 메서드로 만들지 않는다(Task 023에서 `DownloadManager`가 URL로 직접 요청).

- **Task 003: 에러 정규화와 사용자 메시지 매핑** ✅
  - 📋 `data/api/ApiException.kt`: `ApiException(status: Int, code: ErrorCode)`와 `ErrorCode` enum(API.md §5의 8개 + `NETWORK_ERROR`)
  - 📋 `data/api/ErrorNormalizer.kt`: `Throwable` → `ApiException` 변환. `HttpException` 본문이 `ErrorResponse` JSON이면 `code` 사용, 아니면(Nginx HTML 등) 상태로 추정(`413 → PAYLOAD_TOO_LARGE`, 그 외 4xx → `INVALID_REQUEST`, 5xx → `INTERNAL_ERROR`), 알 수 없는 `code`는 `INTERNAL_ERROR`, `IOException`은 `NETWORK_ERROR`. `CancellationException`은 변환하지 않고 그대로 던진다
  - 📋 `ui/ErrorMessages.kt`: `ErrorCode` → 문구(PRD §3.4 표), 재생 오류 문구, 로그인 오류 코드 → 문구(PRD §4.4 표, 모르는 코드는 `login_failed` 문구)
  - 📋 단위 테스트: JSON 본문, HTML 본문(413·502), 모르는 code, IOException, 취소 전파, 모든 코드의 문구 존재
  - 완료 기준: 위 경우를 모두 다루는 단위 테스트가 통과한다.
  - 의존: 002
  - 관련 PRD: PRD.md §3.4, §4.4 / API.md §5
  - 주의: [구현] 문구는 PRD 표와 글자 단위로 같아야 한다(웹과 통일). 서버 `message`는 `ApiException`에 담지 않는다.

- **Task 004: 네트워크 모듈 (Hilt)** ✅
  - 📋 `di/NetworkModule.kt`: `OkHttpClient`, `Retrofit`(`BuildConfig.BASE_URL`, kotlinx.serialization 컨버터), `MusicApi` 제공. 기본 타임아웃 30초
  - 📋 `data/api/RetryInterceptor.kt`: GET 요청만 5xx·`IOException`에 최대 2회 재시도, 4xx와 POST는 재시도하지 않음
  - 📋 Coil `ImageLoader`를 같은 `OkHttpClient` 계열로 구성(`MusicApp`에 `SingletonImageLoader.Factory` 또는 Hilt 제공)
  - 📋 MockWebServer 테스트: 5xx 후 재시도 횟수(총 3회 요청), 4xx 미재시도, POST 미재시도
  - 완료 기준: 재시도 정책 테스트가 통과하고 앱이 크래시 없이 실행된다(Hilt 그래프 구성 확인).
  - 의존: 002, 003
  - 관련 PRD: PRD.md §3.4, §10
  - 주의: [구현] 인증 헤더는 여기서 붙이지 않는다(Task 006의 `AuthInterceptor`가 추가됨). 인터셉터 목록을 나중에 확장할 수 있게 구성한다. 로깅 인터셉터를 넣는다면 debug 전용이며 `Authorization` 헤더를 가린다(`redactHeader`).

- **Task 005: 앱 셸과 공통 UI** ✅
  - 📋 `ui/MessageHost.kt`: 앱 전역 스낵바 채널(`SnackbarHostState` + 메시지 발행용 `MessageBus`를 Hilt 싱글턴으로). 같은 id의 메시지는 중복 표시하지 않는다
  - 📋 `ui/MainScreen.kt`: `Scaffold` 기반 레이아웃 — 상단바 슬롯(검색창 + 우측 액션), 본문 슬롯, 하단 플레이어 슬롯, 스낵바 호스트. `MainActivity`가 이를 표시하도록 변경
  - 📋 `ui/components/Artwork.kt`: Coil로 앨범자켓을 표시하고 URL이 null이거나 로딩 실패면 음표 플레이스홀더를 보여 주는 컴포넌트(크기·모서리 반경 인자)
  - 📋 PRD §9.2 팔레트 중 M3 스킴에 없는 색(warning, success)을 `ui/theme`에서 꺼내 쓸 수 있게 정리(이미 `Theme.kt`에 상수로 있음)
  - 완료 기준: 에뮬레이터에서 앱이 빈 상단바·본문·스낵바 영역을 가진 다크 화면으로 뜨고, 임시 호출로 스낵바가 표시된다(확인 후 임시 호출 제거).
  - 의존: 004
  - 관련 PRD: PRD.md §9.1, §9.2, §9.3
  - 주의: [구현] `accent`(#FF0000)는 텍스트 색으로 쓰지 않는다. 시스템 바 inset(edge-to-edge)을 `Scaffold` 패딩으로 처리한다.

### Phase 2: 인증 ✅

- **Task 006: 토큰 저장과 인증 인터셉터** ✅
  - 📋 `data/auth/TokenStore.kt`: DataStore(Preferences)에 JWT 저장·조회(`Flow<String?>`)·삭제. `di/StorageModule.kt`로 `DataStore` 제공
  - 📋 `data/auth/JwtParser.kt`: payload의 `exp`(초)를 서명 검증 없이 디코드, `isExpired(token, now)` 제공. 형식이 깨진 토큰은 만료로 취급
  - 📋 `data/auth/AuthInterceptor.kt`: 토큰이 있고 요청 호스트가 `BASE_URL` 호스트와 같을 때만 `Authorization: Bearer` 부착. `NetworkModule`의 클라이언트에 연결
  - 📋 단위 테스트: `JwtParser`(정상·만료·깨진 토큰), `AuthInterceptor`(토큰 있음/없음, 외부 호스트 미부착 — MockWebServer)
  - 완료 기준: 위 단위 테스트가 통과한다.
  - 의존: 004
  - 관련 PRD: PRD.md §4.3
  - 주의: [구현] 인터셉터는 동기 컨텍스트이므로 토큰을 메모리에 캐시해 읽는다(매 요청 `runBlocking`으로 DataStore를 읽지 않는다). [검토] 토큰이 로그·`toString()`에 노출되지 않는지 확인.

- **Task 007: 인증 상태 관리** ✅
  - 📋 `data/auth/AuthRepository.kt`: `login(token)`(저장 → `/me` 조회), `logout()`(토큰 삭제), `refreshUser()`. 상태는 `StateFlow<AuthState>`(`Loading` / `LoggedOut` / `LoggedIn(user)`)
  - 📋 규칙 구현: 앱 기동 시 토큰이 있으면 `/me` 호출 / 이미 만료된 토큰이면 `/me` 없이 조용히 로그아웃 / `/me`가 401이면 토큰 삭제 + `UNAUTHORIZED` 스낵바 1회 / 5xx·네트워크 실패면 토큰 유지하고 `LoggedOut` 표시
  - 📋 `feature/auth/AuthViewModel.kt`: 상태 노출, 로그아웃 액션, 포그라운드 복귀 시 재조회 트리거
  - 📋 단위 테스트(MockK + Turbine): 위 네 규칙과 로그아웃
  - 완료 기준: 인증 상태 전이 단위 테스트가 통과한다.
  - 의존: 005, 006
  - 관련 PRD: PRD.md §4.3
  - 주의: [검토] 로그아웃이 목록·재생 상태를 건드리지 않는지 확인(PRD: 목록·재생은 유지).

- **Task 008: 로그인 진입과 딥링크 수신** ✅
  - 📋 `feature/auth/LoginDialog.kt`: `"Sign with Google"` 버튼 하나를 가진 다이얼로그
  - 📋 `feature/auth/LoginLauncher.kt`: `CustomTabsIntent`로 `{BASE_URL}/oauth2/authorization/google?redirect_uri=musicapp%3A%2F%2Foauth2%2Fcallback` 열기
  - 📋 `feature/auth/DeepLinkParser.kt`: `musicapp://oauth2/callback` URI에서 `token` / `error` / 없음 구분(순수 함수 + 단위 테스트)
  - 📋 `MainActivity`: `onCreate`의 intent와 `onNewIntent` 양쪽에서 딥링크 처리 → 처리 직후 intent의 data를 비워 회전·재생성 시 재처리 방지 → `token`이면 `AuthRepository.login`, `error`면 PRD §4.4 문구 스낵바
  - 📋 에뮬레이터 확인: 로그인 버튼 → Custom Tab에 Google 로그인 화면 표시, `adb shell am start -a android.intent.action.VIEW -d "musicapp://oauth2/callback?error=access_denied"`로 오류 스낵바 확인
  - 완료 기준: 딥링크 파서 단위 테스트 통과, 에뮬레이터에서 Custom Tab 진입과 `error` 딥링크 스낵바가 확인된다.
  - 의존: 007
  - 관련 PRD: PRD.md §4.1, §4.2, §4.4, §12.3 / API.md §3.1
  - 주의: [탐색] Manifest의 딥링크 intent-filter와 `singleTop`은 이미 있다. [구현] `redirect_uri`는 백엔드 화이트리스트와 문자열 완전 일치여야 한다. 토큰·URI를 로그에 남기지 않는다. [검토] 실제 Google 계정으로 로그인해 토큰이 돌아오는 전체 흐름은 사용자만 확인할 수 있다. 결재 필요(확인 전까지 "미확인"으로 보고).

- **Task 009: 상단바 로그인 버튼·아바타·프로필 메뉴** ✅
  - 📋 `feature/auth/AuthAction.kt`: 비로그인이면 `[로그인]` 버튼(누르면 `LoginDialog`), 로그인이면 `profileImageUrl` 원형 아바타(null이면 기본 아이콘)
  - 📋 `feature/auth/ProfileMenu.kt`: 아바타를 누르면 `DropdownMenu`에 `업로드`, `로그아웃`. `업로드`는 콜백만 노출(다이얼로그 연결은 Task 016)
  - 📋 `MainScreen` 상단바 우측 슬롯에 연결
  - 완료 기준: 에뮬레이터에서 비로그인 시 `[로그인]` 버튼과 다이얼로그가 보이고, 로그인 상태(가짜 상태 주입 또는 실제 로그인)에서 아바타와 두 메뉴 항목이 보이며 `로그아웃`이 버튼 상태로 되돌린다.
  - 의존: 008
  - 관련 PRD: PRD.md §3.1 상단, §5 서두, §9.1, §9.3
  - 주의: [구현] 아이콘 버튼에 `contentDescription`을 넣는다. 구글 프로필 이미지 요청에는 `Authorization`이 붙지 않아야 한다(Task 006 규칙).

### Phase 3: 목록·검색

- **Task 010: 트랙 목록 상태 관리**
  - 📋 `data/tracks/TrackRepository.kt`: `getTracks(keyword, page)`(size 20), `getTrack(trackId)`. 오류는 `ApiException`으로 정규화
  - 📋 `feature/tracks/TrackListViewModel.kt`: 상태(`tracks`, `isLoading`, `isLoadingMore`, `hasNext`, `keyword`, `error`). `loadNextPage()`(중복 호출 방지, `hasNext=false`면 무시), `refresh()`, `updateTrack(track)`(행 단위 갱신용)
  - 📋 검색: 입력은 즉시 상태에 반영, 조회는 300ms 디바운스, `trim()` 후 빈 값이면 `keyword` 미전송, 200자 제한. 검색어가 바뀌면 page 0부터 다시 조회하되 새 결과 도착 전까지 이전 목록 유지, 이전 요청 취소·늦은 응답 폐기
  - 📋 단위 테스트(Turbine + coroutines-test): 디바운스, 요청 취소, 페이지 이어 붙이기, `hasNext=false` 중단, 새로고침, 오류 상태
  - 완료 기준: 위 단위 테스트가 통과한다.
  - 의존: 004
  - 관련 PRD: PRD.md §6.1, §6.2 / API.md §3.4
  - 주의: [계획] Paging3를 쓰지 않는다(PRD §16 #16). [구현] 오류 시 스낵바는 `MessageBus`로, 4xx는 재시도하지 않는다.

- **Task 011: 트랙 목록 UI**
  - 📋 `feature/tracks/SearchBar.kt`: 알약형 검색창(`#212121`, placeholder `노래 제목 또는 가수를 검색하세요...`), 상단바 슬롯에 배치
  - 📋 `feature/tracks/TrackRow.kt`: 썸네일(`Artwork`), 제목(흰색 굵게, 한 줄 말줄임), `노래 • {가수} • {재생시간}`, 상태 뱃지, 우측 다운로드 아이콘 버튼(콜백만 노출, 동작은 Task 023)
  - 📋 `feature/tracks/StatusBadge.kt`: `PENDING`/`PROCESSING` → `변환 중...`, `FAILED` → `변환 실패`(warning 색), `READY` → 없음
  - 📋 `feature/tracks/TrackList.kt`: `LazyColumn`(key = `trackId`), 끝에서 5개 전 도달 시 `loadNextPage`, pull-to-refresh, 로딩 스켈레톤, 빈 상태 문구(검색 결과 없음 / 업로드된 곡 없음), 하단 로딩 표시
  - 📋 `MainScreen` 본문에 연결. 행 클릭은 콜백만 노출(재생은 Task 019)
  - 완료 기준: 스택이 떠 있는 에뮬레이터에서 실제 목록이 최신순으로 보이고, 검색어 입력 시 결과가 바뀌며, 스크롤 끝에서 다음 페이지가 붙는다.
  - 의존: 005, 010
  - 관련 PRD: PRD.md §6.1, §6.2, §6.3, §9
  - 주의: [구현] 다운로드 버튼 탭이 행 클릭으로 전달되지 않게 한다. 색만으로 상태를 전달하지 않는다(뱃지는 텍스트 포함). [검토] 한글 입력(IME 조합) 중 검색이 정상 동작하는지 확인.

- **Task 012: 변환 상태 폴링**
  - 📋 `feature/tracks/TrackStatusPoller.kt`: 화면에 보이는 `PENDING`/`PROCESSING` 트랙을 5초마다 `getTrack`으로 조회해 `TrackListViewModel.updateTrack`으로 반영(목록 전체 재조회 없음)
  - 📋 중단 조건: `READY`/`FAILED` 도달, 행이 화면에서 사라짐, `404`(목록 새로고침 + 스낵바), 폴링 시작 후 30분 경과(뱃지 옆 `상태 새로고침` 버튼 표시, 누르면 1회 조회 후 폴링 재개)
  - 📋 5xx·네트워크 오류는 알림 없이 다음 틱에서 재시도. 앱이 백그라운드면 중지(`repeatOnLifecycle(STARTED)`), 복귀 시 재개
  - 📋 단위 테스트(가상 시간): `PROCESSING → READY` 전환 후 중단, `FAILED` 중단, 30분 상한, 404 처리, 오류 후 지속
  - 완료 기준: 위 단위 테스트가 통과한다.
  - 의존: 011
  - 관련 PRD: PRD.md §6.3 / API.md §1.4, §3.5
  - 주의: [계획] "화면에 보이는 행" 판단은 `LazyListState`의 visible items로 하거나 행 Composable의 수명(`LaunchedEffect`)에 묶는다. 어느 쪽이든 로직은 테스트 가능한 클래스로 분리한다. [검토] 폴링 조회에는 재시도 인터셉터(2회)와 5초 틱이 겹쳐 요청이 몰리지 않는지 확인.

### Phase 4: 업로드

- **Task 013: 태그 추출과 사전 검증**
  - 📋 `feature/upload/AudioTagReader.kt`: `MediaMetadataRetriever.setDataSource(context, uri)`로 제목·가수·앨범·`embeddedPicture`를 IO 디스패처에서 추출. 값은 `trim()` 후 빈 문자열이면 null. 실패하면 빈 결과 반환(예외를 밖으로 던지지 않음), `release()` 보장
  - 📋 `feature/upload/SelectedFile.kt`: `ContentResolver`로 표시 이름·크기(`OpenableColumns`)·MIME 조회
  - 📋 `feature/upload/UploadValidator.kt`: 음원 100MB 초과, 자켓 10MB 초과·JPEG/PNG 아님, 제목 200자·가수 150자·앨범 200자 초과(trim 후, **초과일 때만** 오류). 순수 함수
  - 📋 `UploadValidator` 단위 테스트(경계값: 정확히 100MB·10MB·200자는 통과)
  - 완료 기준: 검증 단위 테스트가 통과한다.
  - 의존: 003
  - 관련 PRD: PRD.md §5.1, §5.2
  - 주의: [구현] 크기는 1024 단위. 음원 형식·크기·자켓 오류 문구는 PRD §3.4 문구를 재사용하고, 글자 수 초과 문구는 "…자 이하로 입력해 주세요." 형식이다. `AudioTagReader`는 Android 프레임워크 의존이라 단위 테스트 대상이 아니다.

- **Task 014: 스트리밍 멀티파트 업로드**
  - 📋 `data/tracks/ContentUriRequestBody.kt`: `ContentResolver.openInputStream` 기반 `RequestBody`. `contentLength`는 조회한 크기, 쓰기 중 누적 바이트를 콜백으로 보고(진행률)
  - 📋 `TrackRepository.uploadTrack(file, title?, artist?, album?, artwork?, onProgress)`: `file` 파트는 필수, 나머지는 `trim()` 후 값이 있을 때만 파트 추가. 파일 파트의 filename은 원본 표시 이름
  - 📋 업로드 전용 `OkHttpClient`(`@Named`): `writeTimeout`·`readTimeout`·`callTimeout` 해제, 재시도 없음. 코루틴 취소 시 `Call`이 취소되어야 한다
  - 📋 MockWebServer 테스트: 빈 값 파트가 요청 본문에 없음, 필드명(`file`, `title`, `artist`, `album`, `artworkFile`), 202 응답 파싱, 413 HTML 본문 → `PAYLOAD_TOO_LARGE`
  - 완료 기준: 위 테스트가 통과한다.
  - 의존: 004, 013
  - 관련 PRD: PRD.md §5.1, §5.3 / API.md §3.3
  - 주의: [구현] 파일을 `ByteArray`로 통째로 읽지 않는다(최대 100MB). 한글 파일명이 멀티파트 헤더에서 깨지지 않는지 확인. [검토] 진행률 콜백이 과도하게 자주 호출되어 UI를 막지 않도록 스로틀을 둔다.

- **Task 015: 업로드 상태 관리**
  - 📋 `feature/upload/UploadViewModel.kt`: 상태(선택 파일, 제목·가수·앨범 입력값과 placeholder, 내장 커버 미리보기, 사용자가 고른 자켓, 진행률, 단계 `Editing`/`Uploading`/`Processing`, 인라인 오류)
  - 📋 태그 정책: 태그 값이 있으면 입력칸에 채움 / 없으면 입력칸은 비우고 placeholder만(제목 = 확장자 뺀 파일명, 가수 = `Unknown Artist`) / 추출은 5초 타임아웃, 실패·시간 초과는 "값 없음"과 동일
  - 📋 경합 규칙: 파일을 다시 고르면 태그 칸·내장 커버 초기화, 사용자가 직접 고른 자켓은 유지, 사용자가 직접 수정한 칸은 늦게 도착한 태그로 덮어쓰지 않음, 이전 파일의 늦은 결과는 무시
  - 📋 전송: 사전 검증 → `uploadTrack` → 진행률 반영(100% 후 응답 전에는 `Processing`) → 202면 완료 이벤트(목록 새로고침 트리거), 오류면 PRD §3.4 문구를 인라인 오류로 두고 입력값 유지. `cancelUpload()`는 전송만 중단하고 `Editing`으로 복귀
  - 📋 단위 테스트: 태그 있음/없음/타임아웃, 재선택 경합, 수정한 칸 보존, 성공·오류·취소 전이
  - 완료 기준: 위 단위 테스트가 통과한다.
  - 의존: 014
  - 관련 PRD: PRD.md §5.2, §5.3
  - 주의: [구현] 내장 커버는 미리보기 전용이다. 서버로 보내지 않는다. placeholder 값(`Unknown Artist` 등)을 서버로 보내지 않는다. [계획] `AudioTagReader`는 인터페이스로 주입해 테스트에서 가짜로 대체한다.

- **Task 016: 업로드 다이얼로그 UI**
  - 📋 `feature/upload/UploadDialog.kt`: 음원 선택(`OpenDocument`, MIME `audio/*`), 자켓 영역(탭하면 `PickVisualMedia.ImageOnly`, 없으면 음표 플레이스홀더), 제목·가수·앨범 입력칸, `[저장]`·`취소`
  - 📋 전송 중 UI: 진행률(%) 표시, `[저장]` 비활성, `전송 취소` 버튼, 100% 후 `서버에서 처리 중...`. 전송 중 닫기 시도(뒤로 가기·바깥 탭·`취소`)는 확인 대화상자(`계속 업로드` / `전송 취소하고 닫기`)
  - 📋 인라인 오류 표시, 사전 검증 오류 즉시 안내
  - 📋 `ProfileMenu`의 `업로드`에 연결, 업로드 성공 시 다이얼로그를 닫고 `TrackListViewModel.refresh()` 호출
  - 📋 에뮬레이터 확인: 샘플 MP3를 기기에 넣고(`adb push`) 업로드 → 목록에 `변환 중...`으로 나타났다가 뱃지가 사라지는지 확인
  - 완료 기준: 에뮬레이터에서 업로드가 성공해 목록에 곡이 추가되고 폴링으로 `READY`가 되며, 잘못된 파일(예: 텍스트 파일을 `.mp3`로 변경)은 인라인 오류가 표시된다.
  - 의존: 009, 012, 015
  - 관련 PRD: PRD.md §5, §3.4, §9.3
  - 주의: [구현] 다이얼로그는 닫히면 상태가 초기화되어야 한다. 업로드는 ViewModel 범위에서 진행되어 화면 회전에 끊기지 않아야 한다. [검토] 로그인하지 않으면 업로드 진입점이 보이지 않는지 확인.

### Phase 5: 플레이어

- **Task 017: PlaybackService (백그라운드 재생 서비스)**
  - 📋 `player/PlaybackService.kt`: `MediaSessionService` 구현. ExoPlayer 생성(`setAudioAttributes(…, handleAudioFocus = true)`, `setHandleAudioBecomingNoisy(true)`), `MediaSession` 발행, `onGetSession` 반환, `onTaskRemoved`에서 재생 중이 아니면 종료, `onDestroy`에서 player·session `release()`
  - 📋 `AndroidManifest.xml`: `<service android:name=".player.PlaybackService" android:foregroundServiceType="mediaPlayback" android:exported="true">` + `androidx.media3.session.MediaSessionService` intent-filter(주석 자리 교체)
  - 📋 세션 액티비티: 알림을 탭하면 `MainActivity`가 열리도록 `setSessionActivity` 설정
  - 완료 기준: 빌드·lint가 통과하고 서비스가 Manifest에 등록되어 있다(동작 확인은 Task 019·022).
  - 의존: 004
  - 관련 PRD: PRD.md §7.1, §7.3
  - 주의: [계획] ExoPlayer는 이 서비스만 만든다. Activity·ViewModel에서 직접 만들지 않는다. [구현] Media3 API는 버전별 변경이 잦으니 Context7로 Media3 1.11 문서를 확인하고 작성한다. HLS는 `media3-exoplayer-hls` 의존성이 있으면 기본 `MediaSource` 팩토리가 처리한다.

- **Task 018: PlayerRepository (MediaController 연결)**
  - 📋 `player/PlayerRepository.kt`: 인터페이스 — `state: StateFlow<PlayerState>`(`currentTrack: StreamPlayResponse?`, `isPlaying`, `durationMs`, `repeatOne`, `status: Idle/Loading/Playing/Paused/Ended/Error`), `positionMs: StateFlow<Long>`, `play(track)`, `togglePlayPause()`, `seekTo(ms)`, `setVolume(0f..1f)`, `setRepeatOne(Boolean)`, `stop()`
  - 📋 `player/MediaControllerPlayerRepository.kt`: `SessionToken`으로 `MediaController`를 비동기 연결, `Player.Listener`를 상태로 변환, 재생 중에만 500ms 간격으로 위치 갱신. `play(track)`은 PRD §7.2대로 `MediaItem`(mediaId, `resolveMediaUrl`한 URI, `MimeTypes.APPLICATION_M3U8`, 제목·가수·아트워크 메타데이터)을 만들어 `setMediaItem` → `prepare` → `play`
  - 📋 `di/PlayerModule.kt`: 인터페이스 바인딩(싱글턴)
  - 📋 `MediaItem` 생성부를 순수 함수로 분리하고 단위 테스트가 가능하면 URL 결합 규칙만 검증
  - 완료 기준: 빌드가 통과하고, 임시 호출로 스택의 `READY` 트랙이 에뮬레이터에서 소리 나게 재생된다(확인 후 임시 호출 제거).
  - 의존: 001, 017
  - 관련 PRD: PRD.md §7.1, §7.2, §13
  - 주의: [계획] ViewModel 테스트에서 가짜 구현을 쓸 수 있게 인터페이스에 Media3 타입을 노출하지 않는다. [구현] 컨트롤러 연결 전의 호출은 연결 후 실행되게 대기 처리한다. 미디어 요청에는 인증 헤더가 필요 없다.

- **Task 019: 재생 시작 로직**
  - 📋 `feature/player/PlayerViewModel.kt`: `onTrackClick(track)` 분기 — `PENDING`/`PROCESSING`이면 `TRACK_NOT_READY` 문구 스낵바, `FAILED`면 `변환에 실패한 곡입니다` 스낵바, `READY`면 재생 시작
  - 📋 재생 시작: `GET /api/tracks/{id}/play` 1회 → `PlayerRepository.play`. 요청마다 증가하는 `requestId`로 최신이 아닌 응답 폐기
  - 📋 같은 곡 재클릭: `Playing`/`Paused`면 `/play` 없이 토글, `Ended`/`Error`면 `/play` 재호출, `Loading` 중이면 무시
  - 📋 오류: `409 TRACK_NOT_READY`면 스낵바 + 해당 트랙 상태 재조회(`updateTrack`), `404`면 스낵바 + 목록 새로고침, 그 외는 PRD §3.4 문구
  - 📋 `TrackList` 행 클릭에 연결
  - 📋 단위 테스트(가짜 `PlayerRepository`, MockK): `/play` 호출 횟수(토글·반복 시 미호출), 연속 클릭 경합, 409, 상태별 분기
  - 완료 기준: 위 단위 테스트가 통과하고 에뮬레이터에서 행을 누르면 재생되며 같은 행을 다시 누르면 일시정지된다.
  - 의존: 011, 018
  - 관련 PRD: PRD.md §7.1, §6.3, §3.4 / API.md §3.6
  - 주의: [검토] 화면 회전·재구성·시크·일시정지·반복재생에서 `/play`가 다시 호출되지 않는지 확인(호출마다 STREAM 이력이 쌓인다).

- **Task 020: 미니 플레이어 바**
  - 📋 `feature/player/MiniPlayerBar.kt`: 앨범자켓·제목·가수·재생/일시정지 버튼·얇은 진행바. 곡 선택 전에는 숨김. 탭하면 확장 플레이어 열기 콜백
  - 📋 `MainScreen` 하단 슬롯에 연결하고, 표시 중에는 목록 하단 패딩을 확보해 마지막 행이 가려지지 않게 한다
  - 📋 `TrackRow`에 현재 재생 곡 강조(좌측 accent 바 + 제목은 흰색 유지)
  - 완료 기준: 에뮬레이터에서 재생 시작 시 바가 나타나고 재생/일시정지 아이콘이 실제 상태와 일치하며 목록 마지막 행이 바 위로 스크롤된다.
  - 의존: 019
  - 관련 PRD: PRD.md §7.4, §9.1, §9.2, §9.3
  - 주의: [구현] 진행바 갱신(500ms)이 화면 전체를 다시 그리지 않도록 위치 상태 구독을 진행바 Composable 안으로 한정한다. 재생 상태의 단일 출처는 `PlayerRepository`다.

- **Task 021: 확장 플레이어 컨트롤**
  - 📋 `feature/player/ExpandedPlayer.kt`: 풀스크린 시트 — 큰 앨범자켓, 제목·가수, 재생/일시정지, 시크 슬라이더, `01:15 / 03:40` 시간 표기(`formatClock`), 볼륨 슬라이더, 반복 토글, 다운로드 버튼(콜백만, 동작은 Task 023), 닫기
  - 📋 시크: 드래그 중에는 로컬 값만 갱신, 손을 뗄 때 1회 `seekTo`. 길이는 `durationSeconds`로 초기화하고 플레이어 duration으로 보정
  - 📋 볼륨: `setVolume` 즉시 반영, 값은 DataStore에 저장하고 앱 시작·컨트롤러 연결 시 복원(손상 값은 1.0)
  - 📋 반복: `REPEAT_MODE_OFF` ↔ `REPEAT_MODE_ONE` 토글, 활성 시 강조색 + 아이콘 변화
  - 완료 기준: 에뮬레이터에서 시크·볼륨·반복이 동작하고, 앱을 재시작해도 볼륨 값이 유지되며, 곡이 끝났을 때 반복이 켜져 있으면 다시 재생되고 꺼져 있으면 정지한다.
  - 의존: 020
  - 관련 PRD: PRD.md §7.4, §9.3
  - 주의: [구현] 볼륨은 앱 내 `player.volume`이다. 시스템 미디어 볼륨을 바꾸지 않는다. 슬라이더에 접근성 상태 설명을 넣는다. [검토] 반복재생 시 `/play`가 호출되지 않는지 확인.

- **Task 022: 알림 권한과 재생 오류 처리**
  - 📋 Android 13+에서 첫 재생 시점에 `POST_NOTIFICATIONS` 런타임 권한 요청(한 번 거부하면 다시 묻지 않음, 거부해도 재생은 진행)
  - 📋 `onPlayerError` → 상태 `Error` + 재생 오류 스낵바(PRD §3.4). 오류 상태에서 같은 곡 재클릭은 재시작(Task 019 규칙과 연결 확인)
  - 📋 에뮬레이터 확인: 재생 중 홈 버튼·화면 끄기(`adb shell input keyevent 26`) 후에도 재생 유지, 알림창에 미디어 컨트롤 표시·동작, 앱을 최근 앱에서 지워도 동작이 PRD와 일치
  - 📋 확인 결과를 PRD §17에 기록(권한 거부 시 동작 포함)
  - 완료 기준: 에뮬레이터에서 화면이 꺼져도 재생이 이어지고 알림 컨트롤로 일시정지·재생이 되며, 스택을 내린 상태에서 재생을 시도하면 오류 스낵바가 뜬다.
  - 의존: 021
  - 관련 PRD: PRD.md §7.2, §7.3, §17
  - 주의: [검토] 에뮬레이터에서 소리 확인이 어려우면 `PlayerState`의 위치 증가와 `adb shell dumpsys media_session`으로 재생 상태를 확인한다. 잠금 화면·블루투스·전화 수신은 실기기 항목이다(Task 028).

### Phase 6: 다운로드·마감

- **Task 023: 원본 다운로드**
  - 📋 `download/TrackDownloader.kt`: `DownloadManager.Request`로 `{BASE_URL}/api/tracks/{id}/download` 요청, 공용 `Downloads` 폴더 저장, 완료 알림 표시, 로그인 상태면 `addRequestHeader("Authorization", "Bearer …")`
  - 📋 저장 파일명: `Content-Disposition`이 적용되는지 에뮬레이터에서 실측한다. 적용되지 않으면 `originalFilename`을 목적지 파일명으로 지정하고 결과를 PRD §8·§16에 기록
  - 📋 API 28 이하용 `WRITE_EXTERNAL_STORAGE`(`maxSdkVersion="28"`) 선언과 런타임 요청
  - 📋 `TrackRow`의 다운로드 버튼과 `ExpandedPlayer`의 다운로드 버튼에 연결, 요청 시 `다운로드를 시작합니다` 스낵바
  - 완료 기준: 에뮬레이터에서 한글 파일명 곡을 다운로드하면 `Downloads`에 올바른 이름·크기로 저장되고(`adb shell ls -l /sdcard/Download`), `PENDING`·`FAILED` 곡도 다운로드된다.
  - 의존: 011, 021
  - 관련 PRD: PRD.md §8 / API.md §3.7
  - 주의: [탐색] `DownloadManager`는 평문 HTTP에서 debug용 network security config의 영향을 받는다. 실패하면 원인을 로그로 확인한다. [구현] 토큰을 다운로드 제목·설명에 넣지 않는다. 파일을 앱 메모리로 읽지 않는다. [검토] 행의 다운로드 버튼이 재생을 시작시키지 않는지 확인.

- **Task 024: 접근성·화면 상태 마감**
  - 📋 모든 아이콘 버튼의 `contentDescription` 점검(`재생`, `일시정지`, `{title} 원본 다운로드`, `현재 곡 원본 다운로드`, `반복재생` 등), 터치 영역 48dp 이상, 토글의 선택 상태(`semantics`)
  - 📋 화면 회전·프로세스 재생성: 검색어·목록·스크롤 위치·확장 플레이어 열림 상태 유지(`rememberSaveable`/`SavedStateHandle`), 재생은 서비스가 유지
  - 📋 작은 화면(폭 360dp)과 큰 글꼴 설정에서 레이아웃이 깨지지 않는지 확인하고 수정
  - 📋 PRD §14 점검: `Log.*`에 토큰·URI 없음, 사용자 입력은 텍스트로만 렌더링, 외부 호스트에 `Authorization` 미전송
  - 완료 기준: 에뮬레이터에서 회전 후에도 검색어·목록·재생이 유지되고, TalkBack 또는 `uiautomator dump`로 주요 버튼에 설명이 있음이 확인된다.
  - 의존: 016, 022, 023
  - 관련 PRD: PRD.md §9.3, §14
  - 주의: [검토] 색만으로 상태를 전달하는 곳이 없는지(뱃지·현재 재생 행·토글) 다시 본다.

- **Task 025: Compose UI 테스트**
  - 📋 `app/src/androidTest/`: 가짜 Repository를 주입해 서버 없이 동작하는 UI 테스트 구성(Hilt 테스트 설정 또는 ViewModel 직접 주입 방식 중 프로젝트에 맞는 쪽)
  - 📋 테스트: 로그인 다이얼로그 표시, 프로필 메뉴 두 항목, 상태 뱃지 3종, 업로드 다이얼로그(태그 채움·placeholder·인라인 오류), 미니 플레이어 재생/일시정지 토글
  - 완료 기준: 에뮬레이터에서 `connectedDebugAndroidTest`가 통과한다.
  - 의존: 024
  - 관련 PRD: PRD.md §13
  - 주의: [계획] `hilt-android-testing` 등 새 의존성이 필요하면 결재 필요. 가능하면 Composable에 상태·콜백만 넘기는 구조를 활용해 의존성 추가 없이 테스트한다. [검토] 에뮬레이터가 없으면 완료 처리하지 말고 보고한다.

- **Task 026: 앱 아이콘과 README·lint 정리**
  - 📋 앱 아이콘(adaptive icon) 추가와 Manifest `android:icon`·`roundIcon` 지정
  - 📋 `README.md` 작성: 프로젝트 소개, 요구 환경, 빌드·실행·테스트 명령, 서버 접속(`adb reverse`), 문서 위치
  - 📋 `lintDebug` 경고 0건 확인(남기는 경고는 사유를 README 또는 PRD §16에 기록)
  - 📋 `CLAUDE.md`의 "현재 상태" 절을 실제 구현 상태로 갱신
  - 완료 기준: 런처에 아이콘이 보이고 `lintDebug`가 경고 없이 통과하며 README만 보고 빌드·실행할 수 있다.
  - 의존: 024
  - 관련 PRD: PRD.md §9, §12
  - 주의: [계획] 아이콘 디자인(모양·색)은 미결정이다. 결재 필요. 결정 전에는 임시 아이콘임을 명시하고 진행 여부를 묻는다.

### Phase 7: 통합 검증

- **Task 027: 에뮬레이터 통합 점검**
  - 📋 전체 docker 스택 + `adb reverse` 상태에서 시나리오 수행: 목록 조회 → 검색 → `READY` 곡 재생(위치 증가 확인) → 시크 → 반복재생 → 일시정지 → 원본 다운로드(파일명·바이트 확인) → 화면 끄고 재생 유지
  - 📋 로그인이 가능한 경우(사용자가 로그인해 둔 상태): 업로드 → `PENDING` → `READY` 뱃지 갱신 → 재생, 로그아웃
  - 📋 백엔드 로그 또는 DB로 `/play`가 재생 시작 1회당 1건만 기록되는지, 로그인 상태 다운로드 이력에 사용자가 귀속되는지 확인(읽기 전용 확인, 불가하면 미확인으로 보고)
  - 📋 PRD §17 "통합 테스트에서 확인할 항목"을 하나씩 확인해 결과(확인됨/문제/미확인)를 PRD §17에 기록. 발견한 결함은 수정하거나 별도 항목으로 보고
  - 완료 기준: PRD §17의 각 항목에 결과가 기록되고, 수행한 시나리오에서 크래시(`adb logcat`의 `AndroidRuntime`)가 없다.
  - 의존: 025, 026
  - 관련 PRD: PRD.md §13, §17
  - 주의: [검토] 확인하지 못한 항목을 "확인됨"으로 적지 않는다. 백엔드·인프라 문제로 보이면 고치지 말고 PRD §15에 요청으로 기록한다.

- **Task 028: 실기기 수동 검증**
  - 📋 사용자용 체크리스트 문서 작성(`.claude/docs/DEVICE-CHECKLIST.md`): USB 디버깅 연결, `adb reverse`, 설치 방법, 항목별 기대 결과
  - 📋 체크 항목: Google 로그인 전체 흐름 5회 이상 반복(딥링크 복귀, Chrome 외 기본 브라우저 포함), 화면 끄기·앱 전환 시 재생 유지, 잠금 화면·알림 컨트롤, 블루투스 버튼, 전화 수신 시 일시정지, 이어폰 분리 시 일시정지, 알림 권한 거부 시 동작, 100MB급 업로드, 한글 파일명 다운로드
  - 📋 사용자가 수행한 결과를 받아 PRD §17에 기록하고, 문제 항목은 수정 Task로 정리해 보고
  - 완료 기준: 사용자가 체크리스트를 수행해 결과가 PRD §17에 기록된다.
  - 의존: 027
  - 관련 PRD: PRD.md §12.1, §13, §17
  - 주의: [계획] 이 Task의 검증 수행은 사용자만 할 수 있다. 결재 필요. 에이전트는 체크리스트 작성까지만 하고 사용자 결과를 기다린다(임의로 통과 처리 금지).
