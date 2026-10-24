rootProject.name = "LynMusic"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")
include(":androidApp")
include(":android:runtime")
include(":app:runtime")
include(":ui:common")
include(":ui:mobile")
include(":ui:desktop")
include(":ui:automotive")
include(":tvApp")
include(":automotiveApp")
include(":shared:core")
include(":shared:resources")
include(":shared:data")
include(":shared:features")
include(":player:core")
include(":cast:api")
include(":cast:upnp:android")
