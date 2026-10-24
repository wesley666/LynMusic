import org.gradle.api.tasks.Copy
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val sharedVersionConfig = rootProject.readSharedVersionConfig()
val desktopPackageVersion = sharedVersionConfig
    .getValue("APP_DESKTOP_PACKAGE_VERSION")
val desktopConsoleEnabled: Boolean? = providers.gradleProperty("desktopConsole")
    .map(String::toBoolean)
    .orElse(false)
    .get()
val isMacOsHost = System.getProperty("os.name").contains("mac", ignoreCase = true)
val jvmMacOsNowPlayingBridgeOutput = layout.buildDirectory.dir("generated/jvmMacOsNowPlayingBridge")
val jvmMacOsNowPlayingBridgeModuleCache = layout.buildDirectory.dir("tmp/jvmMacOsNowPlayingBridgeModuleCache")

fun String.shellQuote(): String = "'${replace("'", "'\"'\"'")}'"

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "top.iwesley.lyn.music.composeapp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources.enable = true
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        withHostTest {
            isIncludeAndroidResources = true
            targetSdk { version = release(libs.versions.android.targetSdk.get().toInt()) }
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        // These shared fixtures are compiled only into test source sets.
        listOf("commonTest", "jvmTest", "androidHostTest").forEach { sourceSetName ->
            named(sourceSetName) {
                kotlin.srcDir(rootProject.file("tools/test-fixtures/src/$sourceSetName/kotlin"))
            }
        }
        named("androidHostTest") {
            dependencies {
                implementation(libs.robolectric)
            }
        }
        getByName("jvmMain") {
            dependencies {
                implementation(project(":ui:desktop"))
                implementation(compose.desktop.currentOs)
                implementation(libs.compose.material3)
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.sardine)
                implementation(libs.smbj)
                implementation(libs.vlcj)
                implementation(libs.androidx.sqlite.bundled)
            }
        }

        commonMain.dependencies {
            implementation(project(":shared:resources"))
            implementation(project(":shared:core"))
            implementation(project(":shared:data"))
            implementation(project(":shared:features"))
            implementation(project(":player:core"))
            implementation(project(":app:runtime"))
            implementation(project(":ui:common"))
            implementation(libs.androidx.room.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}

val compileJvmMacOsNowPlayingBridge = tasks.register<Exec>("compileJvmMacOsNowPlayingBridge") {
    val swiftSources = fileTree(layout.projectDirectory.dir("src/jvmMacosMain/swift")) {
        include("*.swift")
    }
    val outputFile = jvmMacOsNowPlayingBridgeOutput.map { it.file("libLynMusicNowPlayingBridge.dylib") }
    val outputDirectoryPath = jvmMacOsNowPlayingBridgeOutput.get().asFile.absolutePath
    val moduleCachePath = jvmMacOsNowPlayingBridgeModuleCache.get().asFile.absolutePath
    val outputFilePath = outputFile.get().asFile.absolutePath
    val swiftSourcePaths = swiftSources.files
        .sortedBy { it.name }
        .joinToString(" ") { file -> file.absolutePath.shellQuote() }

    inputs.files(swiftSources)
    outputs.file(outputFile)
    if (isMacOsHost) {
        executable = "/bin/zsh"
        environment("CLANG_MODULE_CACHE_PATH", moduleCachePath)
        args(
            "-lc",
            listOf(
                "mkdir -p ${outputDirectoryPath.shellQuote()} ${moduleCachePath.shellQuote()}",
                "&&",
                "/usr/bin/xcrun",
                "swiftc",
                "-emit-library",
                "-module-name",
                "LynMusicNowPlayingBridge",
                "-module-cache-path",
                moduleCachePath.shellQuote(),
                "-framework",
                "Foundation",
                "-framework",
                "AppKit",
                "-framework",
                "MediaPlayer",
                "-o",
                outputFilePath.shellQuote(),
                swiftSourcePaths,
            ).joinToString(" "),
        )
    } else {
        executable = "true"
    }
}

tasks.named<Copy>("jvmProcessResources") {
    if (isMacOsHost) {
        dependsOn(compileJvmMacOsNowPlayingBridge)
        from(jvmMacOsNowPlayingBridgeOutput) {
            into("native/macos")
        }
    }
}

compose.desktop {
    application {
        mainClass = "top.iwesley.lyn.music.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LynMusic"
            packageVersion = desktopPackageVersion
            //includeAllModules = true
            modules("java.management", "java.security.jgss", "jdk.management")
            macOS {
                bundleID = "top.iwesley.lyn.music"
                iconFile.set(project.file("src/jvmMain/resources/desktop-icon.icns"))
            }
            windows {
                iconFile.set(project.file("src/jvmMain/resources/desktop-icon.ico"))
                dirChooser = false
                shortcut = true
                menu = true
                menuGroup = "LynMusic"
                upgradeUuid = "f70eff91-c266-4763-920a-64ec7eb7958d"
                console = desktopConsoleEnabled == true
            }
            linux {
                iconFile.set(project.file("src/jvmMain/resources/desktop-icon.png"))
            }
        }
    }
}
