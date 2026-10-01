package top.iwesley.lyn.music.tv

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.tv.ui.TvMediaBrowserMode

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class TvMediaDetailTextTest {
    @org.junit.Before
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    fun initializeOfficialResourceReader() {
        org.jetbrains.compose.resources.setResourceReaderAndroidContext(org.robolectric.RuntimeEnvironment.getApplication())
    }

    @Test fun artistCountsResolveAtDisplayIncludingZeroAndSingular() = runTest {
        listOf(0, 1, 2).forEach { count ->
            val args = assertNotNull(buildTvMediaDetailArgs("Library", "Artists", "artist:target", "", null, count))
            val subtitle = assertNotNull(tvMediaDetailSubtitleText(args.subtitle, args.subtitleTrackCount))
            languages.forEach { language ->
                assertEquals(if (language == AppLanguage.English) "$count ${if (count == 1) "song" else "songs"}" else "$count 首歌曲", resolveUiText(subtitle, language))
            }
            assertEquals(count, args.subtitleTrackCount)
            assertEquals("artist:target", args.id)
        }
    }

    @Test fun emptyAlbumAndArtistTitlesResolveTheirOwnPlaceholders() = runTest {
        listOf(
            TvMediaBrowserMode.Albums to listOf("Unknown album", "未知专辑", "未知專輯"),
            TvMediaBrowserMode.Artists to listOf("Unknown artist", "未知艺人", "未知藝人"),
        ).forEach { (mode, expected) ->
            listOf("", " ").forEach { name ->
                val args = assertNotNull(buildTvMediaDetailArgs("Favorites", mode.name, "target", name, null))
                val title = tvMediaDetailTitleText(args.mode, args.title)
                languages.forEachIndexed { index, language -> assertEquals(expected[index % 3], resolveUiText(title, language)) }
                assertEquals(name, args.title)
                assertEquals(TvMediaDetailSource.Favorites, args.source)
            }
        }
    }

    @Test fun realNamesAndLegacySubtitlesAreNeverMatchedOrTranslated() = runTest {
        listOf(TvMediaBrowserMode.Albums, TvMediaBrowserMode.Artists).forEach { mode ->
            val args = assertNotNull(buildTvMediaDetailArgs("Library", mode.name, "target", "未知专辑", "2 首歌曲"))
            val title = tvMediaDetailTitleText(args.mode, args.title)
            val subtitle = assertNotNull(tvMediaDetailSubtitleText(args.subtitle, args.subtitleTrackCount))
            languages.forEach { language ->
                assertEquals("未知专辑", resolveUiText(title, language))
                assertEquals("2 首歌曲", resolveUiText(subtitle, language))
                assertEquals(TvMediaDetailArgs(TvMediaDetailSource.Library, mode, "target", "未知专辑", "2 首歌曲"), args)
            }
        }
        assertNull(tvMediaDetailSubtitleText(null, null))
        assertNull(tvMediaDetailSubtitleText(" ", null))
    }

    @Test fun newCountOverridesLegacySubtitleWithoutMutatingNavigationArguments() = runTest {
        val args = assertNotNull(buildTvMediaDetailArgs("Favorites", "Artists", "raw-id", " 用户名称 ", "旧说明", 0))
        languages.forEach { language ->
            assertEquals(" 用户名称 ", resolveUiText(tvMediaDetailTitleText(args.mode, args.title), language))
            assertEquals(if (language == AppLanguage.English) "0 songs" else "0 首歌曲", resolveUiText(assertNotNull(tvMediaDetailSubtitleText(args.subtitle, args.subtitleTrackCount)), language))
            assertEquals(TvMediaDetailArgs(TvMediaDetailSource.Favorites, TvMediaBrowserMode.Artists, "raw-id", " 用户名称 ", "旧说明", 0), args)
        }
    }

    @Test fun validatorAcceptsBlankTitlesButRejectsInvalidNavigationTargets() = runTest {
        assertNotNull(buildTvMediaDetailArgs("Library", "Albums", "id", "", null))
        assertNull(buildTvMediaDetailArgs(null, "Albums", "id", "name", null))
        assertNull(buildTvMediaDetailArgs("Other", "Albums", "id", "name", null))
        assertNull(buildTvMediaDetailArgs("Library", null, "id", "name", null))
        assertNull(buildTvMediaDetailArgs("Library", "Other", "id", "name", null))
        assertNull(buildTvMediaDetailArgs("Library", "Tracks", "id", "name", null))
        assertNull(buildTvMediaDetailArgs("Library", "Albums", null, "name", null))
        assertNull(buildTvMediaDetailArgs("Library", "Albums", " ", "name", null))
        assertNull(buildTvMediaDetailArgs("Library", "Albums", "id", null, null))
    }

    private val languages = listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese, AppLanguage.English)
}
