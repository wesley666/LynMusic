import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "top.iwesley.lyn.music.player.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources.enable = true
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        withHostTest {
            isIncludeAndroidResources = true
            targetSdk { version = release(libs.versions.android.minSdk.get().toInt()) }
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
        commonMain.dependencies {
            implementation(project(":shared:resources"))
            api(project(":cast:api"))
            implementation(project(":shared:core"))
            implementation(project(":shared:data"))
            implementation(libs.androidx.room.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlin.testJunit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
