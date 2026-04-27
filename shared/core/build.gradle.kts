import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

val sharedVersionConfig = rootProject.readSharedVersionConfig()
val sharedAppVersionCode = sharedVersionConfig.getValue("APP_VERSION_CODE").toInt()
val sharedAppVersionName = sharedVersionConfig.getValue("APP_VERSION_NAME")

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

abstract class GenerateBuildMetadataTask : DefaultTask() {
    @get:Input
    abstract val appVersionName: Property<String>

    @get:Input
    abstract val appVersionCode: Property<Int>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val buildTimeUtc = BUILD_TIMESTAMP_FORMATTER.format(Instant.now())
        val versionName = appVersionName.get()
        val versionCode = appVersionCode.get()
        val outputFile = outputDirectory.file("top/iwesley/lyn/music/core/model/BuildMetadata.kt")
            .get()
            .asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            package top.iwesley.lyn.music.core.model

            object BuildMetadata {
                const val appVersionName = ${versionName.asKotlinStringLiteral()}
                const val appVersionCode = $versionCode
                const val versionDisplay = ${"$versionName ($versionCode)".asKotlinStringLiteral()}
                const val buildTimeUtc = ${buildTimeUtc.asKotlinStringLiteral()}
            }
            """.trimIndent()
        )
    }

    private fun String.asKotlinStringLiteral(): String =
        buildString {
            append('"')
            this@asKotlinStringLiteral.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    else -> append(character)
                }
            }
            append('"')
        }

    private companion object {
        val BUILD_TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
                .withZone(ZoneOffset.UTC)
    }
}

val generateBuildMetadata by tasks.registering(GenerateBuildMetadataTask::class) {
    appVersionName.set(sharedAppVersionName)
    appVersionCode.set(sharedAppVersionCode)
    outputDirectory.set(layout.buildDirectory.dir("generated/source/buildMetadata/commonMain/kotlin"))
    outputs.upToDateWhen { false }
}

kotlin {
    jvmToolchain(21)

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "top.iwesley.lyn.music.shared.core"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generateBuildMetadata)
}
