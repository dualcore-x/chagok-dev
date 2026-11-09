pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
    }
}
rootProject.name = "DriveCompanion"
include(":app", ":core")
// 개발·시연용 확인 앱. 사용법은 tools/probe/README.md.
include(":probe")
project(":probe").projectDir = file("tools/probe")
