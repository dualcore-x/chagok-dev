import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 서명키는 저장소 밖에 둔다(tools/make-signing-key.sh). 파일이 없으면 CI처럼 미서명 release를 만든다.
val signingFile = file(System.getenv("DC_SIGNING_PROPERTIES") ?: "${System.getProperty("user.home")}/.drive-companion/signing.properties")
val signing = if (signingFile.isFile) Properties().apply { signingFile.inputStream().use { load(it) } } else null

android {
    namespace = "com.dualcore.chagok"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.dualcore.chagok"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    signing?.let {
        signingConfigs.create("release") {
            storeFile = file(it.getProperty("storeFile"))
            storePassword = it.getProperty("storePassword")
            keyAlias = it.getProperty("keyAlias")
            keyPassword = it.getProperty("keyPassword")
            enableV3Signing = true // Pleos는 서명키 교체를 전제로 minSdk 28 이상을 요구한다.
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-dev" }
        release { signing?.let { signingConfig = signingConfigs.getByName("release") } }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navi)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
}
