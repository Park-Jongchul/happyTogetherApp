# 행복하자 우리 (Happy Together) — 안드로이드 앱

웹앱을 WebView 로 감싸 **APK** 로 만드는 안드로이드 프로젝트입니다.

| 구분 | 저장소 | 경로 / 주소 |
|---|---|---|
| **안드로이드 앱 (이 저장소)** | `happyTogetherApp` | `/Users/mac/jongchul/ProjectPjc/happyTogether` |
| 웹앱 | `happyTogether` | `/Users/mac/jongchul/ProjectPjc/happyTogetherWeb` · https://park-jongchul.github.io/happyTogether/ |
| 화면설계서 | `happyTogetherDoc` | https://park-jongchul.github.io/happyTogetherDoc/ |

> **Android Studio 에서 이 폴더를 그대로 Open 하시면 됩니다.**
> `/Users/mac/jongchul/ProjectPjc/happyTogether`

---

## 웹 소스와의 관계

빌드할 때 **웹 저장소의 `index.html` 과 `assets/` 를 자동으로 복사**해서
`app/src/main/assets/web/` 에 넣습니다. 웹을 고치고 여기서 빌드만 하면 최신본이 APK 에 들어갑니다.

기본 경로는 나란히 있는 `../happyTogetherWeb` 입니다.

```
ProjectPjc/
├─ happyTogether/        ← 이 프로젝트 (안드로이드)
└─ happyTogetherWeb/     ← 웹 소스
```

다른 위치에 두었다면 `gradle.properties` 에 한 줄 추가하세요.

```properties
happyTogether.webDir=/절대/경로/happyTogetherWeb
```

웹 저장소가 없다면:

```bash
cd /Users/mac/jongchul/ProjectPjc
git clone https://github.com/Park-Jongchul/happyTogether.git happyTogetherWeb
```

---

## 특징

- **웹 자산을 APK 안에 번들** — `WebViewAssetLoader` 로 `https://appassets.androidplatform.net/` 에서 서빙.
  `file://` 이 아니라 https 출처라서 `localStorage` · 카메라 · 마이크가 웹과 똑같이 동작하고, **오프라인에서도 열립니다.**
- **하드웨어 뒤로가기** → 웹 해시 라우터 히스토리를 되돌립니다. 최상단에서 한 번 더 누르면 종료.
- **safe-area 대응** — edge-to-edge + `shortEdges` 라서 웹의 `env(safe-area-inset-*)` 가 노치·제스처바를 피해 갑니다.
- **권한 연결** — 보이스룸 마이크(`RECORD_AUDIO`), 프로필 사진·증빙 첨부(파일 선택기).
- **외부 링크** — 카카오T·지도·결제 등 외부 도메인은 WebView 대신 해당 앱/브라우저로 넘깁니다.
- **당겨서 새로고침** — 최상단에서만 동작해 리스트·채팅 스크롤과 충돌하지 않습니다.

---

## 빌드 방법

### 1) Android Studio (권장)

1. **Open** → `/Users/mac/jongchul/ProjectPjc/happyTogether` 선택
2. Gradle 동기화가 끝나면 상단 ▶ 실행
3. APK 파일: **Build → Build Bundle(s) / APK(s) → Build APK(s)**
   → `app/build/outputs/apk/debug/app-debug.apk`

### 2) 커맨드라인

Gradle 래퍼 바이너리(`gradlew`, `gradle-wrapper.jar`)는 저장소에 넣지 않았습니다.
Android Studio 로 한 번 열면 자동 생성되며, Gradle 이 설치돼 있다면 아래로도 만들 수 있습니다.

```bash
cd /Users/mac/jongchul/ProjectPjc/happyTogether
gradle wrapper                 # 최초 1회
./gradlew assembleDebug        # 디버그 APK
./gradlew assembleRelease      # 릴리스 APK (서명 설정 필요)
```

---

## 배포용 서명

`app/build.gradle.kts` 의 `buildTypes.release` 에 추가하세요.

```kotlin
signingConfigs {
    create("release") {
        storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release.keystore")
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        isMinifyEnabled = true
    }
}
```

키스토어 생성:

```bash
keytool -genkey -v -keystore release.keystore -alias happytogether \
        -keyalg RSA -keysize 2048 -validity 10000
```

> 키스토어와 비밀번호는 **절대 커밋하지 마세요.** 분실하면 스토어 업데이트가 불가능합니다.
> (`.gitignore` 에 `*.keystore`, `*.jks` 를 넣어뒀습니다.)

---

## 서버 최신본 대신 번들 자산을 띄우려면

현재 앱은 **GitHub Pages 최신본**(`https://park-jongchul.github.io/happyTogether/`)을 띄웁니다.
웹만 배포하면 앱 업데이트 없이 즉시 반영되지만, 오프라인에서는 열리지 않습니다.

오프라인 동작이 필요하면 `MainActivity.kt` 의 `START_URL` 을 `BUNDLED_URL` 로 바꾸세요.

```kotlin
private const val START_URL = BUNDLED_URL   // https://appassets.androidplatform.net/web/index.html
```

> 번들 주소에 `assets` 를 또 붙이면 안 됩니다. `AssetsPathHandler` 가 `"/"` 에 등록돼 있어
> `/web/index.html` 이 곧 `app/src/main/assets/web/index.html` 입니다.

---

## 구성

```
happyTogether/
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle.properties
├─ gradle/wrapper/gradle-wrapper.properties
└─ app/
   ├─ build.gradle.kts            웹 자산 복사 태스크 포함
   ├─ proguard-rules.pro
   └─ src/main/
      ├─ AndroidManifest.xml      권한 · 딥링크
      ├─ java/kr/happytogether/app/
      │  ├─ App.kt
      │  └─ MainActivity.kt       WebView 설정 · 권한 · 뒤로가기
      └─ res/
         ├─ layout/activity_main.xml
         ├─ drawable/             스플래시 · 로고 · 런처 아이콘
         ├─ mipmap-anydpi-v26/    적응형 아이콘
         └─ values/               색상 · 문자열 · 테마
```

- 패키지: `kr.happytogether.app`
- minSdk 24 / targetSdk 35 / Kotlin 2.0 / AGP 8.7
