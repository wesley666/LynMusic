package top.iwesley.lyn.music.tv

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory

@Composable
internal fun ConfigureTvImageLoader() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .diskCache(null)
            .build()
    }
}
