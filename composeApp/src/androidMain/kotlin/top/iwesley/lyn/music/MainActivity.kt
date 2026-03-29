package top.iwesley.lyn.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.iwesley.lyn.music.platform.createAndroidAppComponent

class MainActivity : ComponentActivity() {
    private lateinit var appComponent: LynMusicAppComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appComponent = createAndroidAppComponent(this)

        setContent {
            App(appComponent)
        }
    }
}
