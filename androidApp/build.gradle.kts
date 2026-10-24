import com.android.build.api.variant.FilterConfiguration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val sharedVersionConfig = rootProject.readSharedVersionConfig()
val appVersionCode = sharedVersionConfig.getValue("APP_VERSION_CODE").toInt()
val appVersionName = sharedVersionConfig.getValue("APP_VERSION_NAME")
val androidArtifactBaseName = "LynMusic-$appVersionName"

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    testOptions { unitTests.isIncludeAndroidResources = true }
    namespace = "top.iwesley.lyn.music"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    ndkVersion = libs.versions.android.ndk.get()

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
        jniLibs {
            keepDebugSymbols.clear()
        }
    }
    configureLynReleaseSigning(rootProject)
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
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
    lint {
        //checkOnly += setOf("NewApi")
        error += setOf("NewApi")
        abortOnError = true
        checkDependencies = true
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(project(":android:runtime"))
    implementation(project(":ui:mobile"))
    implementation(project(":app:runtime"))
    implementation(project(":ui:common"))
    implementation(project(":shared:resources"))
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

androidComponents.onVariants { variant ->
    val hasMultipleOutputs = variant.outputs.size > 1
    variant.outputs.forEach { output ->
        val abiFilter = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
        val outputLabel = abiFilter ?: if (hasMultipleOutputs) "universal" else null
        val outputSuffix = listOfNotNull(variant.buildType, outputLabel).joinToString("-")
        output.outputFileName.set("$androidArtifactBaseName-$outputSuffix.apk")
    }
}
