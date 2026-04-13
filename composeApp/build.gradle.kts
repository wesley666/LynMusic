import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun readSharedVersionConfig(): Map<String, String> =
    rootProject.file("app-version.xcconfig")
        .readLines()
        .map(String::trim)
        .filter { line ->
            line.isNotEmpty() &&
                !line.startsWith("//") &&
                !line.startsWith("#")
        }
        .mapNotNull { line ->
            val separatorIndex = line.indexOf('=')
            if (separatorIndex < 0) {
                null
            } else {
                line.substring(0, separatorIndex).trim() to
                    line.substring(separatorIndex + 1).trim()
            }
        }
        .toMap()

val sharedVersionConfig = readSharedVersionConfig()
val appVersionCode = sharedVersionConfig.getValue("APP_VERSION_CODE").toInt()
val appVersionName = sharedVersionConfig.getValue("APP_VERSION_NAME")
val desktopPackageVersion = sharedVersionConfig
    .getValue("APP_DESKTOP_PACKAGE_VERSION")
val androidArtifactBaseName = "LynMusic-$appVersionName"

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvmToolchain(21)

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    listOf(
        macosArm64(),
    ).forEach { macTarget ->
        macTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = false
        }
    }
    
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        val applePlaybackMain by creating {
            dependsOn(commonMain.get())
        }
        val iosMain by creating {
            dependsOn(applePlaybackMain)
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        val macosMain by creating {
            dependsOn(applePlaybackMain)
        }
        val iosArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }
        val macosArm64Main by getting {
            dependsOn(macosMain)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.documentfile)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.session)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sardineAndroid)
            implementation(libs.smbj)
        }
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:data"))
            implementation(project(":shared:features"))
            implementation(project(":player:core"))
            implementation(project(":player:app"))
            implementation(libs.androidx.room.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sardine)
            implementation(libs.smbj)
            implementation(libs.vlcj)
        }
    }
}

android {
    namespace = "top.iwesley.lyn.music"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "top.iwesley.lyn.music"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

android.applicationVariants.configureEach {
    val hasMultipleOutputs = outputs.size > 1
    outputs.configureEach {
        val abiFilter = filters.find { it.filterType == "ABI" }?.identifier
        val outputLabel = abiFilter ?: if (hasMultipleOutputs) "universal" else null
        val outputSuffix = listOfNotNull(buildType.name, outputLabel).joinToString("-")
        (this as BaseVariantOutputImpl).outputFileName = "$androidArtifactBaseName-$outputSuffix.apk"
    }
}

compose.desktop {
    application {
        mainClass = "top.iwesley.lyn.music.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LynMusic"
            packageVersion = desktopPackageVersion
            macOS {
                bundleID = "top.iwesley.lyn.music"
                iconFile.set(project.file("src/jvmMain/resources/desktop-icon.icns"))
            }
            windows {
                iconFile.set(project.file("src/jvmMain/resources/desktop-icon.ico"))
                shortcut = true
                menu = true
                menuGroup = "LynMusic"
                upgradeUuid = "f70eff91-c266-4763-920a-64ec7eb7958d"
            }
            linux {
                iconFile.set(project.file("src/jvmMain/resources/desktop-icon.png"))
            }
        }
    }
}
