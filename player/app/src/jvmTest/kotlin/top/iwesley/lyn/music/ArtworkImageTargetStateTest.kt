package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import top.iwesley.lyn.music.core.model.ArtworkCacheStore

class ArtworkImageTargetStateTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `changed artwork request rejects previously resolved target`() {
        val previousRequest = requestKey(locator = "https://example.com/previous.jpg")
        val currentRequest = requestKey(locator = "https://example.com/current.jpg")
        val previousTargetState = LynArtworkTargetState(
            requestKey = previousRequest,
            target = LynResolvedArtworkTarget(
                locator = previousRequest.normalized.orEmpty(),
                target = "C:/cache/previous.jpg",
                isLocalFile = true,
            ),
            resolved = true,
        )
        val initialCurrentState = initialLynArtworkTargetState(
            requestKey = currentRequest,
            initialTarget = null,
        )

        val selectedState = selectLynArtworkTargetState(
            currentRequestKey = currentRequest,
            initialTargetState = initialCurrentState,
            producedTargetState = previousTargetState,
        )

        assertSame(initialCurrentState, selectedState)
        assertNull(selectedState.target)
        assertFalse(selectedState.resolved)
    }

    @Test
    fun `matching artwork request uses asynchronously resolved target`() {
        val request = requestKey(locator = "https://example.com/current.jpg")
        val initialState = initialLynArtworkTargetState(requestKey = request, initialTarget = null)
        val resolvedState = LynArtworkTargetState(
            requestKey = request,
            target = LynResolvedArtworkTarget(
                locator = request.normalized.orEmpty(),
                target = "C:/cache/current.jpg",
                isLocalFile = true,
            ),
            resolved = true,
        )

        assertSame(
            resolvedState,
            selectLynArtworkTargetState(
                currentRequestKey = request,
                initialTargetState = initialState,
                producedTargetState = resolvedState,
            ),
        )
    }

    private fun requestKey(locator: String): LynArtworkTargetRequestKey {
        return LynArtworkTargetRequestKey(
            normalized = locator,
            requestCacheKey = "album:$locator",
            cacheRemote = true,
            cacheVersion = 0L,
            artworkCacheStore = TestArtworkCacheStore,
        )
    }
}

private object TestArtworkCacheStore : ArtworkCacheStore {
    override suspend fun cache(locator: String, cacheKey: String, replaceExisting: Boolean): String? = locator
}
