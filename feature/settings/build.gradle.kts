plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "org.feeluown.mobile.feature.settings"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val settingsForbiddenDependencies = listOf(
    "AppSettings",
    "AppSettingsRepository",
    "SettingsState",
    "ProviderMusicRepository",
    "DownloadRepository",
    "ResourceCacheRepository",
    "LocalMusicFeatureController",
    "AppNavigator",
    "AppRoute",
    "CacheUsage",
    "DownloadTask",
    "LocalMusicUiState",
)

val settingsRequiredFiles = listOf(
    "feature/settings/build.gradle.kts",
    "feature/settings/src/commonMain/kotlin/org/feeluown/mobile/feature/settings/SettingsFeature.kt",
    "feature/settings/src/commonTest/kotlin/org/feeluown/mobile/feature/settings/SettingsFeatureTest.kt",
    "shared/src/commonMain/kotlin/org/feeluown/mobile/feature/settings/SettingsFeatureController.kt",
)

val retiredSharedSettingsSurfaces = listOf(
    "shared/src/commonMain/kotlin/org/feeluown/mobile/feature/settings/SettingsController.kt",
    "shared/src/commonMain/kotlin/org/feeluown/mobile/feature/settings/SettingsControllerState.kt",
    "shared/src/commonMain/kotlin/org/feeluown/mobile/feature/settings/ResourceCacheController.kt",
    "shared/src/commonMain/kotlin/org/feeluown/mobile/feature/debug/DebugLogController.kt",
)

tasks.register("checkSettingsFeatureBoundaries") {
    group = "verification"
    description = "Reject shared back-dependencies, aggregate settings contracts, or retired Settings surfaces."

    inputs.files(settingsRequiredFiles.map(rootProject::file))
    inputs.files(retiredSharedSettingsSurfaces.map(rootProject::file))
    inputs.file(rootProject.file("feature/settings/build.gradle.kts"))
    inputs.dir(rootProject.file("feature/settings/src/commonMain/kotlin"))

    doLast {
        val missing = settingsRequiredFiles.map(rootProject::file).filterNot { it.isFile }
        if (missing.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Settings physical boundary is incomplete:")
                    missing.forEach { appendLine(" - ${it.relativeTo(rootProject.projectDir).invariantSeparatorsPath}") }
                },
            )
        }

        val reintroduced = retiredSharedSettingsSurfaces.map(rootProject::file).filter { it.isFile }
        if (reintroduced.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Retired shared Settings surfaces were reintroduced:")
                    reintroduced.forEach { appendLine(" - ${it.relativeTo(rootProject.projectDir).invariantSeparatorsPath}") }
                },
            )
        }

        val buildFile = rootProject.file("feature/settings/build.gradle.kts")
        if (Regex("""project\(\s*[\"']?:shared[\"']?\s*\)""").containsMatchIn(buildFile.readText())) {
            throw GradleException(":feature:settings must not depend on :shared; bind application types in shared.")
        }

        val sourceRoot = rootProject.file("feature/settings/src/commonMain/kotlin")
        val violations = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    settingsForbiddenDependencies.firstOrNull { dependency ->
                        Regex("\\b${Regex.escape(dependency)}\\b").containsMatchIn(line)
                    }?.let { dependency ->
                        "${file.relativeTo(rootProject.projectDir).invariantSeparatorsPath}:${index + 1} ($dependency)"
                    }
                }.asSequence()
            }
            .toList()
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(":feature:settings leaked application/shared dependencies:")
                    violations.forEach { appendLine(" - $it") }
                },
            )
        }
    }
}

tasks.matching { it.name == "allTests" }.configureEach {
    dependsOn("checkSettingsFeatureBoundaries")
}
