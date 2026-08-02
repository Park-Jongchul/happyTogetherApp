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

    buildFeatures { viewBinding = true }
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
    }
    into(webDst)
}

tasks.named("preBuild") { dependsOn(copyWebAssets) }

tasks.named("clean", Delete::class) {
    delete(webDst)
}
