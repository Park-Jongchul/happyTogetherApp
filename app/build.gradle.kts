plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kr.happytogether.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.happytogether.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // AGP 8 부터 BuildConfig 는 기본으로 생성되지 않습니다.
    // MainActivity 의 BuildConfig.DEBUG(웹 디버깅 스위치) 때문에 필요합니다.
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

/* ── APK 파일 이름 ────────────────────────────
   Build → Build APK(s) 로 만들어지는 파일 이름을 happyTogether.apk 로 고정합니다.
   debug / release 는 서로 다른 폴더에 생성되므로 이름이 같아도 충돌하지 않습니다.
     app/build/outputs/apk/debug/happyTogether.apk
     app/build/outputs/apk/release/happyTogether.apk

   빌드 타입을 이름에 넣고 싶다면 아래 문자열을
   "happyTogether-" + variant.name + ".apk" 로 바꾸면 됩니다.           */
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName?.set("happyTogether.apk")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}

/* ── 웹 자산 동기화 ──────────────────────────────
   웹 저장소(happyTogetherWeb)의 index.html / assets 를
   app/src/main/assets/web 으로 복사합니다.
   웹을 수정한 뒤 그냥 빌드하면 자동으로 최신본이 APK 에 들어갑니다.

   경로는 gradle.properties 의 happyTogether.webDir 로 바꿀 수 있습니다.
   기본값: 이 프로젝트와 나란히 있는 ../happyTogetherWeb          */
val webDirPath = (project.findProperty("happyTogether.webDir") as String?)
    ?: "../happyTogetherWeb"
val webSrc = rootProject.file(webDirPath)
val webDst = layout.projectDirectory.dir("src/main/assets/web")

val copyWebAssets by tasks.registering(Copy::class) {
    description = "웹앱(index.html, assets/) 을 안드로이드 assets 으로 복사"
    doFirst {
        require(File(webSrc, "index.html").exists()) {
            """
            |웹 소스를 찾을 수 없습니다: ${webSrc.absolutePath}
            |
            |happyTogetherWeb 저장소를 이 프로젝트와 같은 폴더에 두거나,
            |gradle.properties 에 아래 한 줄을 추가하세요.
            |    happyTogether.webDir=/절대/경로/happyTogetherWeb
            |
            |  git clone https://github.com/Park-Jongchul/happyTogether.git happyTogetherWeb
            """.trimMargin()
        }
    }
    from(webSrc) {
        include("index.html")
        include("assets/**")
        exclude("**/.DS_Store")          // macOS 부산물은 APK 에 넣지 않는다
    }
    into(webDst)
}

tasks.named("preBuild") { dependsOn(copyWebAssets) }

/* ── clean ────────────────────────────────────
   macOS 의 Finder / Spotlight 가 삭제 도중 build 폴더에 .DS_Store 를 다시 만들면
   Gradle 의 Delete 태스크가 "New files were found" 로 실패합니다.
   Gradle 이 지우기 전에 우리가 먼저, 몇 번 재시도하며 확실히 비웁니다.        */
val buildDirFile = layout.buildDirectory.get().asFile

tasks.named("clean", Delete::class) {
    delete(webDst)
    doFirst {
        val dir = buildDirFile
        repeat(5) { if (dir.exists()) dir.deleteRecursively() }
        if (dir.exists()) {
            logger.warn("clean: ${dir.absolutePath} 를 비우지 못했습니다. " +
                "Finder 에서 이 폴더를 열어 두었다면 닫고 다시 시도하세요.")
        }
    }
}
