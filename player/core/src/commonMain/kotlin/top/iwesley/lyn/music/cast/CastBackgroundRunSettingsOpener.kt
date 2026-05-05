package top.iwesley.lyn.music.cast

interface CastBackgroundRunSettingsOpener {
    fun openSettings()
}

object UnsupportedCastBackgroundRunSettingsOpener : CastBackgroundRunSettingsOpener {
    override fun openSettings() = Unit
}
