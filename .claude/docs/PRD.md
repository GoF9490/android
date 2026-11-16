# [PRD] Android 모바일 앱 상세 설계 (v1.0)

> **작성일:** 2026-11-16
> **목적:** 구현이 끝난 백엔드·음원 서버·Nginx의 **실제 계약**(`API.md`)에 맞춘 Android 앱의 단일 기준 문서입니다. 초안인 `PRD-03`(모바일 앱)을 대체하며, 달라진 점은 §16에 기록합니다.
> **문서 우선순위:** `API.md`(백엔드 API 명세) > 이 문서. 충돌이 발견되면 임의로 판단하지 말고 사용자에게 확인한 뒤 §16에 기록합니다. 확인이 더 필요하면 백엔드 구현(`../backend/music`)과 인프라 문서(`../nginx/.claude/docs/`)를 읽기만 합니다.
> **삭제된 참조 문서:** `PRD-01`~`PRD-07`(전체 구성·백엔드·Nginx·음원 서버·DB·웹 초안·Android 초안)과 `PRD-frontend.md`는 2026-11-16에 이 저장소에서 삭제했습니다(앱에 필요한 내용은 이 문서와 `API.md`에 있음). 사본은 `../nginx/.claude/docs/`·`../frontend/.claude/docs/`에, 원문은 이 저장소 git 기록(커밋 `b556e91`)에 있으며, 그 머리말의 "`PRD.md` §7 참조"는 이 문서가 아니라 구 백엔드 인덱스를 뜻합니다.
> **참고 구현:** 웹 클라이언트의 확정 결정(`../frontend/.claude/docs/PRD.md`, 이 문서에서 "웹 PRD"로 부르며 §16의 "웹 #n"은 그 결정 이력 번호)과 소스(`../frontend/`)를 디자인·동작 참고로 읽을 수 있습니다. 웹 쪽 파일은 읽기만 하며 수정하지 않습니다.

## 1. 개요 및 범위

### 1.1. 제품 범위

온프레미스 음원 스트리밍·아카이빙 MVP("re-dubb")의 **Android 네이티브 클라이언트**입니다. Nginx(`80`) 단일 진입점을 통해 백엔드 API, HLS 스트림, 앨범아트에 접근합니다. 디자인 레퍼런스는 YouTube Music 다크 테마입니다.

| 영역 | 기능 |
|---|---|
| 인증 | Google 로그인(Chrome Custom Tabs + 딥링크), JWT 보관, 프로필 아바타, 로그아웃 |
| 업로드 | 로그인 사용자의 음원 업로드(태그 자동 채우기, 앨범자켓 교체, 진행률) |
| 목록·검색 | 최신순 무한 스크롤, 제목·가수 키워드 검색(300ms 디바운스), 변환 상태 뱃지·자동 갱신 |
| 재생 | 하단 미니 플레이어 + 확장 플레이어(시크·볼륨·반복), **백그라운드·잠금 화면 재생** |
| 다운로드 | 원본 파일 다운로드(`DownloadManager`) |

### 1.2. 네이티브 앱을 채택한 이유

1. **백그라운드 재생 지속성:** 웹뷰는 화면 꺼짐·앱 전환 시 OS 절전 정책으로 오디오가 끊깁니다. Foreground Media Service만 무중단 재생을 보장합니다.
2. **시스템 미디어 연동:** 잠금 화면 알림 컨트롤, 블루투스·차량 버튼, 전화 수신 시 자동 일시정지(Audio Focus)는 `MediaSession`으로만 완전하게 제어됩니다.

### 1.3. 이 저장소가 하는 일과 하지 않는 일

* **한다:** Android 앱 소스 구현, 단위·UI 테스트, 디버그 APK 빌드.
* **하지 않는다:** 백엔드 코드, Nginx 설정, `docker-compose.yml`, 웹 프론트엔드 변경. 필요한 변경은 구현하지 않고 §15에 요청 사항으로만 기록합니다.
* **범위 밖(MVP):** 재생목록·다음 곡 자동 재생, 다중 음질 선택, Refresh Token, 오프라인 캐시, 릴리스 서명·스토어 배포.

## 2. 기술 스택

대중성 > 사용 난이도 > 성능 순으로 안드로이드 공식 권장 구성을 선정합니다. **버전은 2026-11-16에 Maven 메타데이터로 확인한 최신 안정판이며, `gradle/libs.versions.toml`이 원본입니다(이 표와 어긋나면 카탈로그가 우선).** `PRD-03`의 2024년 버전은 사용하지 않습니다. 이 조합으로 `assembleDebug`·`installDebug`·`lintDebug` 성공을 확인했습니다.

| 구분 | 선정 | 버전 | 역할 및 선정 사유 |
|---|---|---|---|
| 언어 | **Kotlin** | 2.4.21 | 안드로이드 표준 |
| UI | **Jetpack Compose + Material 3** (Compose BOM) | BOM 2026.09.00 | 선언형 다크 테마 UI, 모달·바텀시트·슬라이더 |
| 미디어 | **Media3** `exoplayer`, `exoplayer-hls`, `session` (+ `ui` 필요 시) | 1.11.1 | HLS 재생, `MediaSessionService`, 알림·잠금 화면 컨트롤 |
| 네트워크 | **Retrofit + OkHttp + kotlinx.serialization** | Retrofit 3.0.0, OkHttp 5.5.1, kotlinx.serialization 1.13.0 | REST·멀티파트. Gson 대신 Kotlin 네이티브 직렬화(null 안전, 리플렉션 없음) |
| 이미지 | **Coil 3** (`coil-compose`, `coil-network-okhttp`) | 3.6.3 | 앨범자켓·프로필 이미지 |
| 로그인 | **Chrome Custom Tabs** (`androidx.browser`) | 1.10.0 | WebView OAuth 차단 정책 준수 |
| DI | **Hilt** (KSP) | Hilt 2.60.1, KSP 2.3.14 | 공식 표준. ViewModel·Service 주입 (사용자 확정) |
| 상태 | **ViewModel + StateFlow** (`lifecycle-viewmodel-compose`, `collectAsStateWithLifecycle`) | lifecycle 2.11.0 | 단방향 데이터 흐름 |
| 저장 | **DataStore (Preferences)** | 1.3.0 | 토큰·볼륨 등 설정 |
| 내비게이션 | 단일 화면 + 모달/바텀시트 (Navigation 라이브러리 미사용) | - | 라우트가 사실상 1개. 필요해지면 도입 |
| 단위 테스트 | **JUnit + MockK + Turbine + kotlinx-coroutines-test + MockWebServer** | JUnit 4.13.2, MockK 1.14.12, Turbine 1.2.1, coroutines 1.11.0, MockWebServer(OkHttp BOM) | API·ViewModel·인터셉터 검증 |
| UI 테스트 | **Compose UI Test** (`ui-test-junit4`) | Compose BOM 관리 | 핵심 화면 동작 |
| 빌드 | **Gradle Kotlin DSL + 버전 카탈로그**, AGP | AGP 9.5.1, Gradle 9.9.1 | `gradle/libs.versions.toml` |

**SDK 수준:** `minSdk 26`(Android 8.0), `compileSdk`·`targetSdk` **37**(설치된 Platform 37.0). **`applicationId`·`namespace`는 `com.example.musicapp`** (사용자 확정).

**AGP 9 주의:** AGP 9.x는 Kotlin을 내장하므로 `org.jetbrains.kotlin.android` 플러그인을 적용하지 않습니다(적용하면 빌드 오류). Compose(`kotlin.plugin.compose`)·직렬화(`kotlin.plugin.serialization`)·KSP·Hilt 플러그인은 그대로 별도 적용합니다.

**코딩 규칙:** 들여쓰기 4칸(Kotlin 공식 스타일), 변수·함수 camelCase, 함수명은 동사로 시작(`getTracks`, `handleClick`), 클래스·Composable은 PascalCase, 코드 주석·문서·커밋 메시지는 한국어, 식별자는 영어.

## 3. 백엔드 계약 요약

### 3.1. 경로와 Nginx 라우팅

| 경로 | 처리 | 앱 사용 |
|---|---|---|
| `/api/*`, `/oauth2/*`, `/login/oauth2/*` | 백엔드 | REST 호출, 로그인 진입 |
| `/stream/*` | Nginx → RustFS | ExoPlayer 재생 (`streamUrl`) |
| `/artwork/*` | Nginx → RustFS | Coil 이미지 (`artworkUrl`) |
| `/internal-storage/*` | Nginx 내부 전용 | **직접 호출 금지** |

`streamUrl`·`artworkUrl`은 **상대경로 또는 절대 URL**이며(`MEDIA_PUBLIC_BASE_URL` 설정에 따라 다름), 상대경로면 `BASE_URL`을 붙이고 절대 URL이면 그대로 사용합니다.

### 3.2. 엔드포인트

| 메서드 | 경로 | 인증 | 성공 | 앱 용도 |
|---|---|:---:|:---:|---|
| GET | `/oauth2/authorization/google?redirect_uri=` | - | 302 | 로그인 진입 (Custom Tabs) |
| GET | `/api/auth/me` | **필수** | 200 | 로그인 사용자·아바타 |
| POST | `/api/tracks/upload` | 선택 | 202 | 업로드 (UI는 로그인 사용자만 노출) |
| GET | `/api/tracks` | 선택 | 200 | 목록·검색 |
| GET | `/api/tracks/{trackId}` | 선택 | 200 | 변환 상태 폴링 |
| GET | `/api/tracks/{trackId}/play` | 선택 | 200 | 스트리밍 진입점 (`STREAM` 이력 기록) |
| GET | `/api/tracks/{trackId}/download` | 선택 | 200 | 원본 다운로드 (`DOWNLOAD` 이력 기록) |

* `/api/auth/me`를 제외한 모든 API는 `permitAll`이며 무효·만료 토큰도 익명으로 처리됩니다. **401은 `/api/auth/me`에서만** 발생합니다.
* 목록 쿼리: `keyword`(trim 후 200자 초과 `400`, 빈 값은 전체), `page`(0 이상), `size`(1 이상, 기본 20, 100 초과는 보정), 정렬 `createdAt DESC` 고정. 앱은 `status` 필터를 쓰지 않습니다.
* `/play`는 `READY`만 성공(그 외 `409 TRACK_NOT_READY`), `/download`는 상태와 무관합니다.

### 3.3. 모델 (`data/api/model`)

```kotlin
@Serializable enum class TrackStatus { PENDING, PROCESSING, READY, FAILED, UNKNOWN }  // UNKNOWN은 클라이언트 전용

@Serializable
data class TrackResponse(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Int,
    val status: TrackStatus = TrackStatus.UNKNOWN,  // 기본값이 있어야 coerceInputValues가 동작
    val artworkUrl: String? = null,
    val originalFilename: String,
    val fileExtension: String,        // mp3 | wav | flac | m4a
    val fileSizeBytes: Long,
    val createdAt: String,            // UTC ISO-8601
)

@Serializable
data class TrackPageResponse(
    val content: List<TrackResponse>,
    val page: Int, val size: Int,
    val totalElements: Long, val totalPages: Int,
    val hasNext: Boolean,
)

@Serializable
data class StreamPlayResponse(
    val trackId: String, val streamUrl: String,
    val title: String, val artist: String,
    val durationSeconds: Int, val artworkUrl: String? = null,
)

@Serializable
data class UserResponse(
    val userId: Long, val email: String,
    val profileImageUrl: String? = null, val role: String,
)

@Serializable
data class ErrorResponse(val code: String, val message: String, val timestamp: String)
```

* `Json { ignoreUnknownKeys = true; coerceInputValues = true }`로 서버 필드 추가에 견딥니다. 알 수 없는 `status` 값(또는 누락)은 `TrackStatus.UNKNOWN`으로 매핑하며(§16 #26), UI에서는 일반 오류로 취급하고 `when` 분기에 `UNKNOWN`(또는 `else`)을 반드시 둡니다.
* 서버 `message`는 그대로 노출하지 않고 아래 **클라이언트 메시지**를 씁니다. 알 수 없는 `code`는 `INTERNAL_ERROR`와 같이 처리합니다.

### 3.4. 에러 → 사용자 메시지 매핑

웹 PRD(`../frontend/.claude/docs/PRD.md` §3.4)와 문구를 통일합니다.

| 상태 | `code` | 표시 메시지 | 표시 위치 |
|:---:|---|---|---|
| 400 | `INVALID_AUDIO_FORMAT` | 지원하지 않거나 손상된 음원 파일입니다. MP3, WAV, FLAC, M4A 파일을 선택해 주세요. | 업로드 모달 인라인 |
| 400 | `INVALID_ARTWORK` | 앨범자켓은 10MB 이하의 JPEG 또는 PNG 이미지만 사용할 수 있습니다. | 업로드 모달 인라인 |
| 400 | `INVALID_REQUEST` | 요청 내용을 확인해 주세요. (입력 길이 초과 등) | 업로드 모달 인라인 / 스낵바 |
| 401 | `UNAUTHORIZED` | 로그인이 만료되었습니다. 다시 로그인해 주세요. | 스낵바 + 비로그인 전환 |
| 404 | `TRACK_NOT_FOUND` | 존재하지 않는 곡입니다. 목록을 새로고침합니다. | 스낵바 + 목록 새로고침 |
| 409 | `TRACK_NOT_READY` | 아직 변환 중인 곡입니다. 잠시 후 다시 시도해 주세요. | 스낵바 |
| 413 | `PAYLOAD_TOO_LARGE` | 파일이 너무 큽니다. 음원은 100MB, 앨범자켓은 10MB 이하여야 합니다. | 업로드 모달 인라인 |
| 500 | `INTERNAL_ERROR` | 서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. | 스낵바 |
| - | 네트워크 오류·타임아웃 | 서버에 연결할 수 없습니다. 네트워크를 확인해 주세요. | 스낵바 |
| - | 재생 오류(`PlaybackException`) | 재생 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. | 스낵바 |

모든 오류는 `ApiException(status, code, message)`로 정규화합니다. 본문이 `ErrorResponse` 형식이 아니면(Nginx `413`·`502` HTML 등) 상태 코드로 추정합니다(`413 → PAYLOAD_TOO_LARGE`, 그 외 4xx → `INVALID_REQUEST`, 5xx → `INTERNAL_ERROR`).

**재시도 정책:** 4xx는 재시도하지 않고, 5xx·네트워크 오류만 최대 2회 재시도합니다. 업로드(POST)는 비멱등이므로 재시도하지 않습니다.

## 4. 인증 (Google 로그인 + JWT)

### 4.1. 로그인 진입

1. 상단 `[로그인]` 버튼 → 로그인 다이얼로그(`"Sign with Google"` 버튼 1개).
2. 버튼 → `CustomTabsIntent`로 `{BASE_URL}/oauth2/authorization/google?redirect_uri=musicapp%3A%2F%2Foauth2%2Fcallback`을 엽니다.
3. **`redirect_uri`는 백엔드 `OAUTH2_ALLOWED_REDIRECT_URIS`와 완전 일치**해야 합니다(기본 화이트리스트에 `musicapp://oauth2/callback` 포함). 불일치하면 토큰 없이 `?error=invalid_redirect_uri`로 돌아옵니다.

### 4.2. 딥링크 수신

* `MainActivity`는 `launchMode="singleTop"`이며 `musicapp://oauth2/callback` intent-filter를 가집니다. 앱이 이미 떠 있으면 `onNewIntent`, 아니면 `onCreate`의 intent로 수신합니다(두 경로 모두 처리).
* 처리 순서: ① `data`에서 `token`/`error` 추출 ② intent의 `data`를 비워 재처리 방지(회전·재생성 시 중복 로그인 방지) ③ `token`이면 저장 후 `/api/auth/me` 조회, `error`면 §4.4 메시지 스낵바.
* 토큰·딥링크 URI를 로그(`Log.*`)·크래시 리포트에 남기지 않습니다.
* 사용자가 Custom Tab을 닫기만 한 경우(콜백 없음)는 별도 처리 없이 비로그인 상태를 유지합니다.

### 4.3. 토큰 사용과 세션 관리

| 항목 | 규칙 |
|---|---|
| 보관 | DataStore(Preferences). MVP에서는 앱 샌드박스 보호에 의존하며, 암호화 저장은 §17 과제(§16 #4) |
| 전송 | OkHttp `Interceptor`가 토큰이 있으면 `Authorization: Bearer {jwt}`를 모든 API 요청에 부착. 외부 호스트(이미지 CDN 등)에는 부착하지 않음 |
| 사용자 조회 | 앱 기동 시 토큰이 있으면 `GET /api/auth/me`. 성공하면 아바타 표시 |
| 401 처리 | `/api/auth/me`가 `401`이면 토큰 삭제 → 비로그인 전환 → `UNAUTHORIZED` 스낵바 |
| 만료 | JWT payload의 `exp`(초)를 디코드(서명 검증 없이 표시용)해 이미 만료면 `/me` 호출 없이 로그아웃(스낵바 없음). 최종 기준은 서버 |
| 네트워크 실패 | `/me`가 5xx·네트워크 오류로 실패하면 토큰은 유지하고 비로그인으로 표시(포그라운드 복귀 시 재시도) |
| 로그아웃 | 서버 호출 없음. 토큰 삭제 + 사용자 상태 초기화(목록·재생은 유지) |

### 4.4. 로그인 오류 메시지 (`?error={code}`)

웹 PRD(§4.4)와 동일합니다.

| `error` | 표시 메시지 |
|---|---|
| `access_denied` | 로그인이 취소되었습니다. |
| `invalid_request` | 로그인 요청이 올바르지 않습니다. 다시 시도해 주세요. |
| `authorization_request_not_found` | 로그인 시간이 초과되었습니다(3분). 다시 시도해 주세요. |
| `invalid_state_parameter` | 로그인 검증에 실패했습니다. 다시 시도해 주세요. |
| `invalid_user_info` | 구글 계정 정보를 확인할 수 없습니다. 이메일이 인증된 계정으로 로그인해 주세요. |
| `server_error`, `temporarily_unavailable` | 로그인 서버에 문제가 있습니다. 잠시 후 다시 시도해 주세요. |
| `invalid_redirect_uri` | 로그인 설정 오류입니다. (개발 환경이면 §12의 허용 콜백 등록을 확인하세요) |
| `login_failed` 및 그 외 | 로그인에 실패했습니다. 다시 시도해 주세요. |

## 5. 업로드

로그인 상태에서만 프로필 아바타의 드롭다운(`DropdownMenu`)에 `"업로드"`, `"로그아웃"` 두 항목이 노출됩니다(백엔드는 익명 업로드도 허용하나 UI 정책은 웹과 동일).

### 5.1. 입력과 사전 검증

| 필드 | 제약 (백엔드와 동일) | 클라이언트 검증 |
|---|---|---|
| `file` (필수) | MP3/WAV/FLAC/M4A, 최대 100MB. 서버가 실제 내용(Tika)으로 최종 검증 | SAF 선택(`OpenDocument`, MIME `audio/*`), 크기(`OpenableColumns.SIZE`) 100MB 초과 시 즉시 안내 |
| `title` | 최대 200자, 선택 | 글자 수 제한 |
| `artist` | 최대 150자, 선택 | 글자 수 제한 |
| `album` | 최대 200자, 선택 | 글자 수 제한 |
| `artworkFile` | JPEG/PNG, 최대 10MB, 선택 | Photo Picker(`PickVisualMedia.ImageOnly`), MIME·크기 검사 |

* 요청 전체 한도는 110MB입니다. 한도 초과는 서버 `413`으로도 처리합니다.
* 공백만 있는 값은 서버가 "비어 있음"으로 취급합니다. 클라이언트도 `trim()` 후 빈 값은 **파트를 보내지 않습니다.**

### 5.2. 태그 자동 채우기 정책 (PRD-03 대비 변경)

파일 선택 직후 `MediaMetadataRetriever`(`setDataSource(context, uri)`, 백그라운드 디스패처)로 읽습니다.

| 상황 | 동작 |
|---|---|
| 태그 값 있음 | 입력칸에 **값으로 채움** (수정 가능) |
| 태그 값 없음(예: 무태그 WAV) | 입력칸은 **비워 두고 placeholder만 표시** — 제목: 원본 파일명(확장자 제외), 가수: `Unknown Artist` |
| 파싱 실패·타임아웃(5초) | 오류 없이 "값 없음"과 동일하게 처리(업로드를 막지 않음) |

* **빈 칸은 서버로 보내지 않아** 서버 fallback(요청값 → ID3 → 파일명/`Unknown Artist`)이 동작합니다. `PRD-03`처럼 클라이언트가 `'Unknown Artist'`를 값으로 채워 보내면 클라이언트 파싱 실패 시 서버가 읽을 수 있는 ID3를 덮어쓰므로 채택하지 않습니다.
* **앨범자켓:** `embeddedPicture`는 **미리보기 전용**이며 전송하지 않습니다(서버가 직접 추출). 사용자가 직접 고른 JPEG/PNG만 `artworkFile`로 전송합니다(서버 우선순위: 사용자 첨부 > 내장 > 없음). 커버가 없으면 기본 음표 플레이스홀더를 표시합니다.
* 파일을 바꾸면 태그 칸·내장 커버를 새 파일 기준으로 초기화하되, 사용자가 직접 고른 커버와 직접 수정한 칸은 유지합니다.

### 5.3. 전송과 결과 처리

* `ContentResolver.openInputStream` 기반 커스텀 `RequestBody`로 **파일을 메모리에 통째로 올리지 않고** 스트리밍하며, 바이트 카운팅으로 진행률(%)을 `StateFlow`에 노출합니다. `contentLength`는 `OpenableColumns.SIZE`로 지정합니다.
* 업로드용 OkHttp 클라이언트는 `writeTimeout`·`callTimeout`을 해제하고(대용량·저속망), 취소는 코루틴 취소(`Call.cancel`)로 처리합니다. 전송 중에는 `[저장]` 비활성, 닫기 시 확인 대화상자를 거칩니다. 진행률 100% 이후에도 응답 전까지는 `서버에서 처리 중...`을 표시합니다.
* 업로드는 화면 이탈에 영향받지 않도록 ViewModel 범위에서 수행합니다(MVP에서 프로세스 종료 후 이어받기는 범위 밖).
* `202` 수신 → 모달 닫기 → 목록 새로고침. 응답은 `PENDING`이며 목록에 `변환 중...`으로 나타나고 §6.3 폴링이 시작됩니다.
* 오류는 §3.4 표대로 모달 안에 인라인 표시하고 입력값은 유지합니다.

## 6. 목록·검색·변환 상태 갱신

### 6.1. 목록

* `LazyColumn`, `size=20`. 마지막 항목 근처(예: 끝에서 5개 전)에 도달하면 `page + 1`을 로드하며 `hasNext=false`면 중단합니다. 행 단위 상태 갱신(§6.3)이 쉽도록 Paging3 대신 ViewModel이 `List<TrackResponse>` 상태를 직접 관리합니다.
* 검색어 변경 시 페이지를 0으로 초기화하되, 새 결과가 도착하기 전까지 이전 목록을 유지해 깜빡임을 줄입니다. 이전 요청은 취소하고 늦게 도착한 응답은 폐기합니다.
* 당겨서 새로고침(pull-to-refresh) 지원.
* 행 구성: 좌측 라운드 앨범자켓 썸네일(`artworkUrl`, `null`·로딩 실패 시 기본 썸네일), 제목(흰색 굵게), `노래 • {가수} • {재생시간}`, 상태 뱃지, 우측 다운로드 아이콘. `createdAt DESC`는 서버 정렬을 그대로 사용합니다.
* 재생 시간 표기: `mm:ss`, 1시간 이상은 `h:mm:ss`. 빈 상태(검색 결과 없음 / 업로드된 곡 없음)와 로딩(스켈레톤) 표시.

### 6.2. 검색

* 상단 알약형(Pill) 검색창(`#212121`), 입력 후 **300ms 디바운스**.
* 전송 전 `trim()`, 200자 초과 입력은 입력 단계에서 제한. 빈 값은 `keyword`를 보내지 않습니다. 한글 IME 조합 중에도 동작에 문제가 없는지 확인합니다.

### 6.3. 변환 상태 뱃지와 폴링

| `status` | 뱃지 | 행 동작 |
|---|---|---|
| `PENDING`, `PROCESSING` | `변환 중...` | 재생 클릭 시 안내 스낵바(409 방지), 다운로드는 가능 |
| `READY` | 없음 | 재생 가능 |
| `FAILED` | `변환 실패` (경고색) | 재생 불가("변환에 실패한 곡입니다" 스낵바), 다운로드는 가능 |

* 화면에 보이는 변환 중 행에 대해 **5초마다** `GET /api/tracks/{trackId}`로 상태를 조회하고, 응답으로 목록 상태의 해당 트랙을 갱신합니다(전체 재조회 없음).
* **중단 조건:** `READY`/`FAILED` 도달, 행이 화면에서 사라짐, `404`(목록 새로고침), 폴링 시작 후 **30분 경과**(뱃지에 "상태 새로고침" 버튼). 5xx·네트워크 오류는 알림 없이 다음 틱에서 재시도합니다.
* 앱이 백그라운드이면 폴링을 일시 중지하고(`repeatOnLifecycle(STARTED)`) 복귀 시 재개합니다. 앱을 다시 열면 목록 응답의 `status`로 폴링이 자동 재개됩니다.

## 7. 플레이어 (Media3 + 백그라운드 재생)

### 7.1. 구조

* **ExoPlayer는 `PlaybackService`(`MediaSessionService`)가 소유**하고 `MediaSession`을 발행합니다. UI는 `MediaController`(`SessionToken`으로 연결)로만 제어하며 ExoPlayer를 직접 만들지 않습니다. 이 구조여야 화면이 꺼지거나 Activity가 종료되어도 재생이 유지됩니다.
* UI 상태(`currentTrack`, `isPlaying`, `position`, `duration`, `repeatMode`, `status`)는 `PlayerRepository`가 `MediaController` 리스너를 `StateFlow`로 변환해 제공합니다. 재생 위치는 재생 중에만 주기적(예: 500ms)으로 폴링합니다.
* 행 클릭(`READY`만) → `GET /api/tracks/{id}/play` → `MediaItem` 생성 후 재생. **재생 시작 클릭 1회당 `/play`를 1번만 호출**합니다(호출마다 `STREAM` 이력이 쌓이므로 재구성·반복·시크·일시정지·화면 회전에서는 호출하지 않음).
* `409 TRACK_NOT_READY`면 스낵바 + 해당 트랙 상태 재조회.
* **연속 클릭 경합:** 요청마다 증가하는 `requestId`를 두고 최신이 아닌 응답은 폐기합니다.
* **같은 곡 재클릭:** `playing`/`paused`의 같은 곡은 `/play` 없이 재생/일시정지를 토글합니다. 곡이 끝난 뒤나 오류 상태에서의 재클릭은 재시작이므로 `/play`를 다시 호출합니다. 응답 대기 중인 같은 곡의 중복 클릭은 무시합니다.
* 곡이 끝나고 반복재생이 꺼져 있으면 정지합니다(다음 곡 자동 재생은 범위 밖).

### 7.2. MediaItem

```kotlin
// 알림창·잠금 화면에 표시할 메타데이터를 함께 바인딩한다
val mediaItem = MediaItem.Builder()
    .setMediaId(play.trackId)
    .setUri(resolveMediaUrl(play.streamUrl)) // 상대경로면 BASE_URL 결합
    .setMimeType(MimeTypes.APPLICATION_M3U8)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(play.title)
            .setArtist(play.artist)
            .setArtworkUri(play.artworkUrl?.let { Uri.parse(resolveMediaUrl(it)) })
            .build()
    )
    .build()
```

* `streamUrl`은 HLS 매니페스트(AAC 192kbps, 6초 세그먼트)이며 청크는 Nginx가 RustFS에서 직접 전송합니다. 미디어 요청에는 인증 헤더가 필요 없습니다.
* 재생 오류(`onPlayerError`)는 `status=error`로 두고 §3.4의 재생 오류 스낵바를 띄웁니다. 네트워크 일시 오류는 Media3 기본 재시도에 맡기고, 최종 실패 후 재클릭하면 §7.1대로 재시작합니다.

### 7.3. 서비스·권한 (`AndroidManifest.xml`)

* 권한: `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`(Android 13+는 첫 재생 시점에 런타임 요청; 거부해도 재생은 동작하며 알림 컨트롤만 제한될 수 있음을 확인).
* `<service android:name=".player.PlaybackService" android:foregroundServiceType="mediaPlayback" android:exported="true">` + `androidx.media3.session.MediaSessionService` intent-filter.
* `<activity android:name=".MainActivity" android:exported="true" android:launchMode="singleTop">`에 LAUNCHER와 `musicapp://oauth2/callback` VIEW(BROWSABLE) intent-filter를 둡니다.
* Audio Focus 처리(`setAudioAttributes(..., handleAudioFocus = true)`)와 `setHandleAudioBecomingNoisy(true)`(이어폰 분리 시 일시정지)를 켭니다.

### 7.4. 컨트롤

| 컨트롤 | 동작 |
|---|---|
| 미니 플레이어 바 | 곡 선택 후 하단에 고정 표시(선택 전 숨김). 앨범자켓·제목·가수·재생/일시정지·얇은 진행바. 탭하면 확장 플레이어 |
| 재생/일시정지 | `isPlaying`과 동기화되는 토글 (플레이어 이벤트가 상태의 단일 출처) |
| 진행 슬라이더 | 드래그 중에는 로컬 값만 갱신하고 **놓을 때 1회** `seekTo`. `01:15 / 03:40` 표기. 길이는 `durationSeconds`로 초기화하고 플레이어 duration으로 보정 |
| 볼륨 | `player.volume`(0.0~1.0) 슬라이더. 하드웨어 볼륨 키는 시스템 미디어 볼륨을 제어함(앱 볼륨과 별개). 값은 DataStore에 저장·복원 |
| 반복재생 | `REPEAT_MODE_OFF` ↔ `REPEAT_MODE_ONE` 토글(활성 시 강조색). 반복은 `/play`를 다시 호출하지 않음 |
| 다운로드 | 현재 곡 원본 다운로드(§8) |
| 시스템 컨트롤 | 알림·잠금 화면·블루투스 버튼의 재생/일시정지는 Media3 기본 동작 |

## 8. 원본 다운로드

* 시스템 `DownloadManager`로 `GET {BASE_URL}/api/tracks/{id}/download`를 요청해 공용 `Downloads` 폴더에 저장합니다. Android 10(API 29)+는 공용 폴더 쓰기 권한이 필요 없고 `minSdk 26`~28은 `setDestinationInExternalPublicDir`를 사용합니다(`WRITE_EXTERNAL_STORAGE`는 API 28 이하에만 `maxSdkVersion=28`로 선언).
* 로그인 상태이면 `addRequestHeader("Authorization", "Bearer …")`를 붙여 **`DOWNLOAD` 이력에 사용자가 귀속**됩니다(웹은 브라우저 이동 방식이라 익명으로 남는 점과 다름).
* 백엔드는 본문 없이 `X-Accel-Redirect`로 Nginx가 파일을 내려주며 `Content-Disposition: attachment; filename*=UTF-8''…`로 한글 파일명을 지정합니다. `DownloadManager`가 헤더 파일명을 적용하는지 확인하고, 적용되지 않으면 `setTitle`/`setDestinationInExternalPublicDir`의 파일명을 `originalFilename`으로 지정합니다.
* 모든 상태(`PENDING`·`FAILED` 포함)에서 가능하며, 행 다운로드 버튼은 행 클릭(재생)과 이벤트가 겹치지 않아야 합니다. 완료·실패는 `DownloadManager` 알림에 맡기고, 요청 시점에 "다운로드를 시작합니다" 스낵바를 표시합니다.
* 한계(MVP 수용): 다운로드 진행·완료 상태를 앱 UI에 반영하지 않습니다.

## 9. UI·디자인·접근성

### 9.1. 화면 구성

단일 메인 화면 + 다이얼로그/바텀시트: ① 상단(검색창 + `[로그인]`/아바타) ② 트랙 리스트 ③ 하단 미니 플레이어 바 ④ 확장 플레이어(풀스크린 시트) ⑤ 로그인·업로드 다이얼로그.

### 9.2. 디자인 토큰 (웹 `tokens.css`와 동일 팔레트)

| 토큰 | 값 | Material 3 매핑 |
|---|---|---|
| `bg` | `#030303` | `background` |
| `bgElevated` | `#121212` | `surfaceContainer` (상단바·플레이어 바) |
| `surface` | `#212121` | `surfaceVariant` (검색창·다이얼로그·행 pressed) |
| `accent` | `#FF0000` | `primary` (활성 토글·진행바) |
| `text` / `textMuted` | `#FFFFFF` / `#AAAAAA` | `onBackground` / `onSurfaceVariant` |
| `warning` | `#FFB300` | 커스텀 (`변환 실패` 뱃지) |
| `success` / `error` | `#4CAF50` / `#FF5252` | 스낵바 표식 / `error` |

* 웹 PRD(§9.2)에서 계산한 WCAG 대비를 그대로 따릅니다: 텍스트·상태 색은 4.5:1 이상이며, **`accent`는 `#212121` 위에서 4.03:1이므로 텍스트에 쓰지 않습니다**(진행바·활성 토글 등 비텍스트 UI에만 사용).
* 다크 테마 고정(`darkColorScheme`), 동적 색상(Material You)은 사용하지 않습니다. 시스템 바는 `enableEdgeToEdge`로 처리합니다.
* 디자인 세부는 필요하면 `../frontend/src`(특히 `styles/`, `features/*`)를 읽어 참고합니다.

### 9.3. 접근성·반응형

* 모든 아이콘 버튼에 `contentDescription`(예: `재생`, `일시정지`, `{title} 원본 다운로드`), 슬라이더에 상태 설명(`semantics`), 터치 영역 48dp 이상.
* 색상만으로 상태를 전달하지 않습니다(뱃지는 텍스트 포함, 반복·음소거는 아이콘 변화 + 선택 상태 표시).
* 세로 모드 우선. 화면 회전·프로세스 재생성 시에도 재생(서비스)과 검색어·목록 스크롤이 유지되어야 하며, 미니 바가 마지막 행을 가리지 않도록 하단 패딩을 확보합니다.

## 10. 패키지 구조

```text
android/
├── settings.gradle.kts / build.gradle.kts
├── gradle/libs.versions.toml            # 버전 카탈로그
├── gradlew, gradlew.bat, gradle/wrapper/
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/xml/network_security_config.xml   # debug 전용 평문 허용은 src/debug로 분리
        │   └── java/com/example/musicapp/
        │       ├── MusicApp.kt                        # @HiltAndroidApp
        │       ├── MainActivity.kt                    # 딥링크 수신, Compose 진입
        │       ├── di/                                # NetworkModule, StorageModule, PlayerModule
        │       ├── data/
        │       │   ├── api/                           # MusicApi(Retrofit), model/, ApiException, 에러 정규화
        │       │   ├── auth/                          # TokenStore(DataStore), AuthInterceptor, JwtParser, AuthRepository
        │       │   └── tracks/                        # TrackRepository, 업로드 RequestBody
        │       ├── feature/
        │       │   ├── auth/                          # AuthViewModel, LoginDialog, ProfileMenu
        │       │   ├── tracks/                        # TrackListViewModel, TrackList, TrackRow, StatusBadge, SearchBar
        │       │   ├── upload/                        # UploadViewModel, UploadDialog, AudioTagReader
        │       │   └── player/                        # PlayerViewModel, MiniPlayerBar, ExpandedPlayer
        │       ├── player/                            # PlaybackService, PlayerRepository
        │       ├── download/                          # TrackDownloader
        │       └── ui/                                # theme/(Color, Theme, Type), components/, MessageHost(스낵바)
        ├── debug/res/xml/network_security_config.xml  # 평문 HTTP 허용 (debug만)
        ├── test/                                      # 단위 테스트
        └── androidTest/                               # UI 테스트
```

## 11. 환경 설정과 빌드

| 이름 | 설명 | 기본값 |
|---|---|---|
| `musicBaseUrl` (`local.properties`) | 서버 기준 URL. `BuildConfig.BASE_URL`로 주입 | `http://localhost` (§12.3의 `adb reverse` 전제) |

* `local.properties`는 `.gitignore` 대상이며 시크릿(Google 클라이언트 시크릿 등)은 앱에 두지 않습니다. `BuildConfig`·리소스는 APK에서 추출 가능하다고 가정합니다.
* **평문 HTTP(`http://`)**: 로컬 Nginx는 TLS가 없으므로 `usesCleartextTraffic`을 전역 `true`로 두지 않고 **debug 빌드 전용 `network_security_config`**(`src/debug`)로 허용합니다. 릴리스 빌드는 HTTPS를 전제로 하며, 운영 도메인 확정 후 별도 결정합니다(§17).
* 빌드 변형: `debug`(개발·테스트)만 사용하며 서명 키 관리는 범위 밖입니다. R8/ProGuard는 릴리스 도입 시 검토합니다.

## 12. 개발 환경 (Windows 11)

### 12.1. 도구 (사용자 확정)

* **Android Studio** 설치(`winget install Google.AndroidStudio`) → 첫 실행 마법사로 SDK 설치. Claude는 터미널에서 `gradlew`로 빌드·테스트합니다.
* JDK 21(설치됨)을 Gradle JDK로 사용하거나 Studio 내장 JBR을 사용합니다(A0에서 호환 확인).
* 환경변수: `ANDROID_HOME`(`%LOCALAPPDATA%\Android\Sdk`), PATH에 `platform-tools`·`emulator`·`cmdline-tools\latest\bin`.
* **테스트 기기:** 평소에는 에뮬레이터(AVD), 백그라운드·잠금 화면·블루투스 재생의 최종 확인은 실기기(USB 디버깅)에서 수행합니다.

### 12.2. 실행

```bash
./gradlew assembleDebug                         # 디버그 APK 빌드
./gradlew installDebug                          # 연결된 기기에 설치
./gradlew testDebugUnitTest                     # 단위 테스트
./gradlew testDebugUnitTest --tests "com.example.musicapp.*TrackRepositoryTest"   # 단일 테스트
./gradlew connectedDebugAndroidTest             # UI 테스트 (기기 필요)
```

전제: 전체 docker 스택(Nginx `80`, 백엔드, RustFS, PostgreSQL)이 PC의 `http://localhost`에서 실행 중이어야 합니다.

### 12.3. 서버 접속 주소

앱의 `localhost`는 기기 자신을 가리키므로 아래 중 하나를 사용합니다.

| 환경 | `BASE_URL` | 비고 |
|---|---|---|
| **에뮬레이터·실기기(권장)** | `http://localhost` | `adb reverse tcp:80 tcp:80`로 기기의 80포트를 PC 80포트로 터널링. 기기를 다시 연결하면 재실행 필요 |
| 에뮬레이터(대안) | `http://10.0.2.2` | 에뮬레이터 전용 호스트 별칭. **Google 로그인 불가 가능성(아래)** |
| 실기기 Wi-Fi(대안) | `http://192.168.x.x` | PC 내부 IP. **Google 로그인 불가 가능성(아래)** |

**Google 로그인과 호스트 문제 (중요):** 백엔드는 요청 `Host`로 Google OAuth `redirect_uri`(`…/login/oauth2/code/google`)를 계산합니다. `10.0.2.2`나 사설 IP로 접속하면 Google Console에 등록할 수 없는 주소(사설 IP는 http 불가)가 되어 로그인이 실패합니다. 그래서 **`adb reverse` + `http://localhost`가 기본 해법**이며 기존 웹과 동일한 등록(`http://localhost/login/oauth2/code/google`)을 그대로 사용합니다. **A0 확인 결과(2026-11-16, 에뮬레이터):** `adb reverse tcp:80 tcp:80` 후 기기 Chrome에서 `http://localhost/api/tracks`가 서버 JSON을 반환했고, `http://localhost/oauth2/authorization/google?redirect_uri=musicapp%3A%2F%2Foauth2%2Fcallback`이 `redirect_uri_mismatch` 없이 Google 로그인 화면까지 도달했습니다. 로그인 완료 후 `musicapp://` 딥링크 복귀는 A1에서 확인합니다. 실기기는 미확인입니다. 로그인 없이도 목록·재생·다운로드는 `10.0.2.2`로 개발할 수 있습니다.

## 13. 테스트 전략

| 계층 | 도구 | 검증 대상 |
|---|---|---|
| 단위 | JUnit + MockK + coroutines-test | `JwtParser`(exp 디코드·만료), 시간 포맷(`mm:ss`·`h:mm:ss`), 에러 정규화·메시지 매핑, 딥링크 파싱(`token`/`error`/없음), 업로드 사전 검증, 빈 값 파트 생략 |
| API | MockWebServer | Bearer 부착 규칙, 에러 본문(JSON/HTML) 정규화, 멀티파트 구성, 재시도 정책(4xx 미재시도·5xx 2회) |
| ViewModel | Turbine + coroutines-test | 검색 디바운스·요청 취소, 페이징, 폴링 전환(`PROCESSING → READY`, `FAILED`, 30분 상한), `/play` 1회 호출·연속 클릭 경합·409·같은 곡 토글 |
| UI | Compose UI Test | 로그인 다이얼로그·프로필 메뉴, 업로드 다이얼로그(태그 채움·placeholder·오류 인라인), 목록 상태 뱃지, 미니 플레이어 컨트롤 |
| 수동(실기기) | - | Google 로그인 전체 흐름(5회 이상 반복), 화면 끄기·앱 전환 시 재생 유지, 알림·잠금 화면 컨트롤, 블루투스 버튼, 전화 수신 시 일시정지, 이어폰 분리, 화면 회전, 100MB급 업로드, 한글 파일명 다운로드 |

* `PlaybackService`·`MediaController`는 Robolectric 없이 단위 테스트가 어려우므로 `PlayerRepository` 인터페이스로 경계를 두고 ViewModel 테스트에서는 가짜 구현을 사용합니다. 서비스 자체는 수동·계측 테스트로 확인합니다.
* 테스트에 실제 JWT·시크릿을 넣지 않습니다.

## 14. 비기능 요구사항

* **보안:** 토큰은 로그·크래시 리포트·URL에 남기지 않음, 사용자 입력(제목·가수)은 항상 텍스트로 렌더링, 외부 호스트에 `Authorization` 미전송, 딥링크 수신 후 intent data 제거. 릴리스 빌드에서 평문 HTTP 금지.
* **메모리:** 업로드·다운로드는 파일 전체를 메모리에 올리지 않음(스트리밍), 썸네일은 Coil 캐시 사용.
* **안정성:** 재생은 서비스가 소유하므로 Activity 종료·회전에도 유지. 코루틴은 `viewModelScope`·`repeatOnLifecycle`로 수명 관리(화면 이탈 시 폴링 중단). 서비스 종료 시 `release()`로 ExoPlayer·MediaSession 정리.
* **성능:** 목록은 페이지 단위 로드, 미디어 바이트는 백엔드를 거치지 않고 Nginx가 전송하므로 앱은 URL만 사용.

## 15. 백엔드·인프라 요청 사항 (이 저장소에서 구현하지 않음)

| # | 요청 | 담당 | 비고 |
|---|---|---|---|
| 1 | 모바일 로그인 검증 시 `adb reverse` + `localhost` 접속이 Custom Tab에서 동작하는지 확인. 불가하면 대안(다른 호스트·포트의 콜백 등록 등) 협의 | 인프라·백엔드 | §12.3. 에뮬레이터에서 Google 화면 도달 확인(2026-11-16), 로그인 완료·딥링크 복귀·실기기는 A1·A6에서 확인 |
| 2 | `OAUTH2_ALLOWED_REDIRECT_URIS`에 `musicapp://oauth2/callback` 포함 유지(기본값에 이미 있음, 환경변수로 덮어쓸 때 누락 주의) | 백엔드 환경변수 | §4.1 |
| 3 | Nginx 접근 로그에서 `token` 쿼리 마스킹(모바일은 커스텀 스킴이라 직접 해당 없음이나 `/oauth2` 응답 경로 점검) | 인프라(`../nginx`) | 웹 PRD §15 #2와 동일 계열 |
| 4 | 운영 환경에서 HTTPS 도메인 제공 시 앱의 debug 평문 허용 제거·`BASE_URL` 교체 | 인프라 | §11 |

## 16. 결정 이력 (PRD-03 대비 변경점)

`PRD-03` 원문은 이 저장소 git 기록(커밋 `b556e91`)과 `../prd/PRD-03.md`(구버전)에 있습니다. 아래 표는 변경 전 내용을 함께 적어 원문 없이도 읽을 수 있습니다.

| # | PRD-03 | 변경 | 사유 |
|---|---|---|---|
| 1 | 리눅스 도커 컨테이너에서 CLI(`sdkmanager`)로 빌드 | **Windows 11 + Android Studio**, 터미널은 `gradlew` | 에뮬레이터·Logcat·Compose 미리보기 필요, 컨테이너는 기기 연동이 번거로움 (사용자 확정 2026-11-16) |
| 2 | 의존성 버전 하드코딩(Compose BOM 2024.10.00, Media3 1.4.1 등) | **버전 카탈로그**로 관리, 버전은 A0에서 최신 안정판 확인 | 초안 시점 버전이 오래됨 |
| 3 | Retrofit + Gson | **kotlinx.serialization** | Kotlin 네이티브, null 안전, 리플렉션 없음 |
| 4 | `EncryptedSharedPreferences`(또는 DataStore) | **DataStore(Preferences)**, 암호화는 §17 과제 | Jetpack Security Crypto의 `EncryptedSharedPreferences`가 deprecated. MVP는 샌드박스 보호 수용 |
| 5 | 태그 없으면 `'Unknown Artist'`를 값으로 입력 | **placeholder만 표시, 빈 값은 미전송** | 클라이언트 파싱 실패 시 서버 ID3 fallback 보존 (웹 #10과 동일) |
| 6 | 내장 앨범자켓 비트맵을 업로드 대상으로 암시 | **미리보기 전용, 전송 안 함** | 서버가 직접 추출, 우선순위 사용자 첨부 > 내장 (웹 #11) |
| 7 | 허용 형식 `.mp3`, `.wav` 등 | **`.m4a` 추가**, 서버가 최종 검증 | 백엔드가 M4A 허용 |
| 8 | `[변환 중]` 뱃지만 정의 | **`FAILED` → `변환 실패` 뱃지**, 5초 폴링·중단 조건·30분 상한 | 실패 상태 미정의 (웹 #9) |
| 9 | `/play` 호출 시점 미정 | **재생 시작 클릭당 1회**, 같은 곡 재클릭은 토글 | 호출마다 `STREAM` 이력이 쌓임 (웹 #14, #33) |
| 10 | `usesCleartextTraffic="true"` 전역 | **debug 전용 network security config** | 릴리스 평문 허용 방지 |
| 11 | `http://10.0.2.2` / 사설 IP 접속 안내 | **`adb reverse` + `http://localhost`를 기본으로** | `Host` 기반 OAuth `redirect_uri` 계산이 Google 등록 주소와 어긋남 (§12.3) |
| 12 | 다운로드는 일반 HTTP | **`Authorization` 헤더를 붙여 요청** | 이력 사용자 귀속(웹은 불가) |
| 13 | DI 미정 | **Hilt(KSP)** | 공식 표준, `ViewModel`·`Service` 주입 (사용자 확정) |
| 14 | 패키지명 `com.example...`(예시 없음) | **`com.example.musicapp`** | 딥링크 스킴 `musicapp`과 일치 (사용자 확정) |
| 15 | 에러 응답 처리 미정의 | `code` 매핑(§3.4), 로그인 `?error=` 매핑(§4.4) | 백엔드 계약 정합, 웹과 문구 통일 |
| 16 | 목록 페이징 미정 | **Paging3 미사용**, ViewModel이 목록·행 상태 관리 | 폴링으로 행 단위 갱신이 필요 |
| 17 | 서비스 소유 구조 미정(예시 코드는 `exoPlayer` 직접 호출) | **ExoPlayer는 Service 소유, UI는 `MediaController`** | 백그라운드·잠금 화면 재생 보장 |
| 18 | `Theme.Material3.Dark.NoActionBar`(XML 테마) | Compose `darkColorScheme` + 웹 팔레트 | 웹과 디자인 토큰 통일 |
| 19 | 볼륨 슬라이더가 `exoPlayer.volume` 또는 시스템 볼륨 | **앱 내 `player.volume`**, 하드웨어 키는 시스템 볼륨 | 두 개념을 분리하고 값을 DataStore에 저장 |
| 20 | AGP·Kotlin 버전 미정 | **AGP 9.5.1 + 내장 Kotlin**(kotlin-android 플러그인 미사용), `compileSdk`·`targetSdk` 37 | 설치된 SDK(Platform 37.0)와 최신 안정판에 맞춤 (A0, 2026-11-16) |
| 21 | 백업 설정 없음 | `allowBackup=false` + `dataExtractionRules`로 앱 데이터(토큰) 클라우드 백업·기기 이전 제외 | JWT가 저장되는 DataStore가 백업에 실리지 않게 함 (A0) |
| 22 | `sdkmanager` 사용 안내 | `sdkmanager`는 deprecated(Android CLI로 위임, 사용 통계 수집 안내가 나옴). 필요하면 `--no-metrics` 사용 | cmdline-tools 23.0.0 확인 시 경고 확인 (A0) |
| 23 | `formatDuration` 음수 입력 미정의 | **`0:00`으로 처리** (`formatClock`과 같은 방어) | 서버 값이 음수일 일은 없으나 방어 (Task 001) |
| 24 | 절대 URL 판별 기준 미정 | **`http://`·`https://` 접두사를 대소문자 무시로 판별** | 상대경로면 `BASE_URL` 결합, 절대 URL은 그대로 (Task 001) |
| 25 | 시간 포맷 함수 시그니처 미정 | **`formatDuration(seconds: Int)`, `formatClock(ms: Long)`** | `durationSeconds`가 `Int`(§3.3), 플레이어 위치는 ms (Task 001) |
| 26 | 알 수 없는 `status` 처리 미정 | **`TrackStatus.UNKNOWN` 추가, `status` 기본값 `UNKNOWN` + `coerceInputValues`로 매핑**, UI는 일반 오류 취급(후속 Task) | 서버가 enum을 추가해도 역직렬화가 실패하지 않고 `FAILED`로 오인하지 않음 (Task 002, 사용자 확정) |
| 27 | `ApiException.status` 타입 미정 | **`Int?`** (네트워크 오류는 `null`), 서버 `message`는 담지 않음. 알 수 없는 Throwable → `INTERNAL_ERROR`, `ErrorResponse` 파싱 실패(필드 누락 포함)는 상태 코드 추정 폴백 | 네트워크 오류에는 HTTP 상태가 없음 (Task 003) |
| 28 | 네트워크 클라이언트 세부 미정 | **`@Named("base")` 클라이언트(타임아웃 30초, `callTimeout` 없음, 인터셉터 없음)에서 API용 클라이언트를 `newBuilder()`로 파생. `RetryInterceptor`는 GET만 5xx·`IOException`에 2회, 재시도 간 300ms×시도횟수 선형 대기(생성자 주입). 로깅 인터셉터는 넣지 않음. Coil도 API 클라이언트를 공유(외부 호스트 `Authorization` 차단은 Task 006 `AuthInterceptor` 호스트 검사)** | 풀·디스패처 공유, 업로드 클라이언트(Task 014) 파생 용이, 토큰 로그 노출 방지 (Task 004) |
| 29 | 스낵바 표시 정책 미정 | **`MessageBus`(Hilt 싱글턴)가 순차 표시, 표시 시간 `Short`, 액션·닫기 버튼 없음. 같은 id가 대기·표시 중이면 중복 발행을 무시하고 표시가 끝나면(취소 포함) 다시 허용. 회전·프로세스 재생성 시 표시 중이던 메시지는 유실(MVP 수용). SUCCESS·ERROR는 아이콘 + 텍스트** | 같은 오류의 연속 스낵바 방지, 색만으로 상태를 전달하지 않음 (Task 005) |
| 30 | M3 스킴 밖 색(warning, success) 접근 방식 미정 | **`MaterialTheme.extendedColors`(`ExtendedColors`, CompositionLocal)로 접근**, `onPrimary`는 흰색으로 명시 | 팔레트 상수를 직접 import하지 않고 테마 한 곳으로 통일 (Task 005) |
| 31 | `Authorization` 부착 조건·토큰 캐시·`exp` 처리 세부 미정 | **`AuthInterceptor`는 요청 호스트와 포트가 `BASE_URL`과 같을 때만 Bearer 부착(스킴 무시, 이미 `Authorization`이 있으면 유지). `TokenStore`는 DataStore + 메모리 캐시(로드·쓰기를 Mutex로 직렬화, 로드 전 최초 1회만 `runBlocking` 허용, 앱 시작 시 `MusicApp`이 미리 로드). `JwtParser`는 해석 불가·`exp` 없음을 만료로 취급(시계 보정 없음, `exp <= now`면 만료)** | 포트가 다른 서버로 토큰이 새지 않게 하고, 첫 요청이 캐시 로드보다 빨라도 토큰이 누락되지 않게 함 (Task 006) |

## 17. 통합 확인 항목과 MVP 이후 과제

**통합 테스트에서 확인할 항목 (문서만으로 확정 불가)**

* Google 로그인 **전체 흐름**(계정 선택 → 콜백 → 토큰 수신)이 성공하는지, 실기기에서도 `adb reverse`가 동작하는지. 에뮬레이터 Chrome에서 `localhost` 접속과 Google 화면 도달은 확인됨(§12.3, §15 #1)
* Custom Tab 로그인 후 `musicapp://oauth2/callback?token=` 딥링크가 앱을 복귀시키고 `onNewIntent`/`onCreate` 양쪽에서 처리되는지, Chrome 외 기본 브라우저에서도 동작하는지
* `DownloadManager`가 Nginx 경유 응답의 `Content-Disposition`(한글 파일명)을 적용하는지, 인증 헤더가 리다이렉트 후에도 문제없는지(§8)
* Android 13+ 알림 권한 거부 시 백그라운드 재생·알림 컨트롤 동작, Android 14+ `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 동작
* HLS 시크·반복재생(`REPEAT_MODE_ONE`) 시 끊김, 생성된 `index.m3u8`이 `#EXT-X-ENDLIST`는 있고 `#EXT-X-PLAYLIST-TYPE:VOD`는 없는 점이 총 길이 표시에 미치는 영향
* 100MB 업로드의 저속망·절전 상태 동작

**MVP 이후 과제**

* 토큰 암호화 저장(Keystore 기반), 커스텀 스킴 → **App Links**(검증된 HTTPS 링크) 전환으로 딥링크 하이재킹 방지
* 로그인 토큰을 URL 쿼리 대신 일회용 코드 교환 방식으로 전환(백엔드와 협의)
* 재생목록·다음 곡 자동 재생, 다중 음질(ABR), Refresh Token 대응
* 업로드 백그라운드 전송(WorkManager)·이어받기, 다운로드 진행 상태 UI, 릴리스 서명·HTTPS

## 18. 개발 단계 요약

상세 태스크(ID·선행·완료 조건)는 `.claude/docs/ROADMAP.md`를 따릅니다(Phase 1~7, 28개 태스크). Phase N은 아래 A(N-1)에 대응하며, A0 중 환경 세팅·프로젝트 골격은 이미 끝나 로드맵에는 잔여 작업만 있습니다.

| 단계 | 내용 | 선행 |
|---|---|---|
| A0 | **환경 세팅**(Android Studio·SDK·AVD, 버전 확인·카탈로그), 프로젝트 골격(Compose·Hilt·Retrofit), 테마·토큰, `ApiException`·에러 정규화, `adb reverse`·로그인 접속 가능성 확인 | - |
| A1 | 인증: 로그인 다이얼로그, Custom Tabs, 딥링크 수신, `TokenStore`·Interceptor, 아바타·프로필 메뉴, 만료 처리 | A0 |
| A2 | 목록·검색: 무한 스크롤, 디바운스 검색, 상태 뱃지, 폴링 | A0 |
| A3 | 업로드: 다이얼로그, `AudioTagReader`, 스트리밍 멀티파트·진행률·취소, 오류 인라인 | A1, A2 |
| A4 | 플레이어: `PlaybackService`·`MediaController`, `/play` 1회 규칙, 미니 바·확장 플레이어, 볼륨 저장, 알림 권한 | A2 |
| A5 | 다운로드, 접근성·반응형 마감 | A2, A4 |
| A6 | 통합 검증: 실기기 백그라운드·잠금 화면, Google 로그인 반복, §17 항목 확인 | A1~A5, 전체 스택 |
