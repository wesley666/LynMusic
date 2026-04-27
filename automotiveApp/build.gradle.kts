import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val sharedVersionConfig = rootProject.readSharedVersionConfig()

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "top.iwesley.lyn.music.automotive"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "top.iwesley.lyn.music.automotive"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = sharedVersionConfig.getValue("APP_VERSION_CODE").toInt()
        versionName = sharedVersionConfig.getValue("APP_VERSION_NAME")
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
    }
    configureLynReleaseSigning(rootProject)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":android:runtime"))
    implementation(project(":player:app"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)

    debugImplementation(libs.compose.uiTooling)
}
