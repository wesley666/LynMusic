package top.iwesley.lyn.music.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import top.iwesley.lyn.music.platform.AndroidActivityActionHost

abstract class TvComponentActivity : ComponentActivity() {
    private lateinit var activityActionHost: AndroidActivityActionHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityActionHost = (application as LynMusicApplication).createActivityActionHost(this)
    }

    override fun onStart() {
        super.onStart()
        activityActionHost.bind()
    }

    override fun onResume() {
        super.onResume()
        top.iwesley.lyn.music.platform.refreshAndroidSystemAppLanguage(resources.configuration)
        activityActionHost.notifyResumed()
    }

    override fun onStop() {
        activityActionHost.unbind()
        super.onStop()
    }

    override fun onDestroy() {
        activityActionHost.close()
        super.onDestroy()
    }
}

internal fun ComponentActivity.tvAppComponentResult() =
    (application as LynMusicApplication).getOrCreateAppComponent()
