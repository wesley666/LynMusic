package top.iwesley.lyn.music

class MacOSPlatform : Platform {
    override val name: String = "macOS"
}

actual fun getPlatform(): Platform = MacOSPlatform()
