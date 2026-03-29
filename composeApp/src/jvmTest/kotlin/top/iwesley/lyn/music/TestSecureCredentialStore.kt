package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.SecureCredentialStore

object EmptySecureCredentialStore : SecureCredentialStore {
    override suspend fun put(key: String, value: String) = Unit

    override suspend fun get(key: String): String? = null

    override suspend fun remove(key: String) = Unit
}

class MapSecureCredentialStore(
    private val values: MutableMap<String, String> = linkedMapOf(),
) : SecureCredentialStore {
    override suspend fun put(key: String, value: String) {
        values[key] = value
    }

    override suspend fun get(key: String): String? = values[key]

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}
