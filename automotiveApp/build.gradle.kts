import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val sharedVersionConfig = rootProject.readSharedVersionConfig()
val appVersionName = sharedVersionConfig.getValue("APP_VERSION_NAME")
val androidArtifactBaseName = "LynMusic-Car-$appVersionName"

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "top.iwesley.lyn.music.automotive"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    ndkVersion = libs.versions.android.ndk.get()

    defaultConfig {
        applicationId = "top.iwesley.lyn.music.automotive"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = sharedVersionConfig.getValue("APP_VERSION_CODE").toInt()
        versionName = appVersionName
    }

    sourceSets.getByName("main") {
        res.srcDir(rootProject.file("composeApp/src/androidMain/res"))
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
        jniLibs {
            keepDebugSymbols.clear()
        }
    }
    configureLynReleaseSigning(rootProject)

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
    lint {
        error += setOf("NewApi")
        abortOnError = true
        checkDependencies = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":shared:resources"))
    implementation(project(":android:runtime"))
    implementation(project(":player:app"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)

    debugImplementation(libs.compose.uiTooling)
}

if (rootProject.isAndroidLintOnAssembleEnabled()) {
    androidComponents {
        onVariants { variant ->
            val variantName = variant.name.replaceFirstChar { it.titlecase() }
            tasks.matching { it.name == "assemble$variantName" }.configureEach {
                dependsOn("lint$variantName")
            }
        }
    }
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
