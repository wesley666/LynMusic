@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package top.iwesley.lyn.music.testing

import android.app.Application
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
actual abstract class UiResourceTest actual constructor() {
    @Before
    @OptIn(ExperimentalResourceApi::class)
    fun initializeUiResourceReader() {
        setResourceReaderAndroidContext(RuntimeEnvironment.getApplication())
    }
}
