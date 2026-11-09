plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
// 개발·시연용 확인 앱. 제출 APK에는 들어가지 않는다. 사용법은 tools/probe/README.md.
android {
    namespace = "dev.handover.probe"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.handover.probe"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
// 개발 도구라 release로 만들지 않는다.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enable = false }
}
dependencies {
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navi)
}
