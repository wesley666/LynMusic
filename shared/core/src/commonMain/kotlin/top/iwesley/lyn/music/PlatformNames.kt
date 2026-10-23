package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.PlatformDescriptor

const val ANDROID_PLATFORM_NAME = "Android"
const val ANDROID_AUTOMOTIVE_PLATFORM_NAME = "Android Automotive"
const val ANDROID_TV_PLATFORM_NAME = "Android TV"

fun PlatformDescriptor.isPCPlatform(): Boolean = name == "Desktop"

fun PlatformDescriptor.isMobilePlatform(): Boolean = name == ANDROID_PLATFORM_NAME

fun PlatformDescriptor.isAndroidPlatform(): Boolean = name.contains("Android")

fun PlatformDescriptor.isAndroidTV(): Boolean = name == ANDROID_TV_PLATFORM_NAME

fun PlatformDescriptor.isAndroidAutomotivePlatform(): Boolean = name == ANDROID_AUTOMOTIVE_PLATFORM_NAME
