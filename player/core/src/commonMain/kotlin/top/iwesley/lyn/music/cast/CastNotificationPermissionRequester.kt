package top.iwesley.lyn.music.cast

interface CastNotificationPermissionRequester {
    fun isRequestNeeded(): Boolean

    suspend fun requestIfNeeded(): Boolean
}

object UnsupportedCastNotificationPermissionRequester : CastNotificationPermissionRequester {
    override fun isRequestNeeded(): Boolean = false

    override suspend fun requestIfNeeded(): Boolean = true
}
