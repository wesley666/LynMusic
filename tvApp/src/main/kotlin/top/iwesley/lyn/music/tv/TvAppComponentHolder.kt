package top.iwesley.lyn.music.tv

import top.iwesley.lyn.music.LynMusicAppComponent

internal object TvAppComponentHolder {
    private var component: LynMusicAppComponent? = null

    @Synchronized
    fun attach(value: LynMusicAppComponent) {
        component = value
    }

    @Synchronized
    fun detach(value: LynMusicAppComponent) {
        if (component === value) {
            component = null
        }
    }

    @Synchronized
    fun current(): LynMusicAppComponent? = component
}
