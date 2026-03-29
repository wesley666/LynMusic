package top.iwesley.lyn.music.platform

import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import java.util.Properties
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.warn

internal fun createJvmSecureCredentialStore(logger: DiagnosticLogger): SecureCredentialStore {
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        osName.contains("mac") -> MacOsKeychainCredentialStore()
        osName.contains("win") -> WindowsDpapiCredentialStore()
        else -> {
            logger.warn(CREDENTIALS_LOG_TAG) {
                "unsupported-os os=${System.getProperty("os.name").orEmpty()} fallback=in-memory"
            }
            InMemoryJvmCredentialStore()
        }
    }
}

private class MacOsKeychainCredentialStore : SecureCredentialStore {
    override suspend fun put(key: String, value: String) {
        val result = runSecurity(
            "add-generic-password",
            "-U",
            "-s", MAC_KEYCHAIN_SERVICE,
            "-a", key,
            "-w", value,
        )
        check(result.exitCode == 0) { result.error.ifBlank { "Failed to write credential to macOS Keychain." } }
    }

    override suspend fun get(key: String): String? {
        val result = runSecurity(
            "find-generic-password",
            "-s", MAC_KEYCHAIN_SERVICE,
            "-a", key,
            "-w",
        )
        return when {
            result.exitCode == 0 -> result.output.ifEmpty { null }
            result.error.contains("could not be found", ignoreCase = true) -> null
            else -> error(result.error.ifBlank { "Failed to read credential from macOS Keychain." })
        }
    }

    override suspend fun remove(key: String) {
        val result = runSecurity(
            "delete-generic-password",
            "-s", MAC_KEYCHAIN_SERVICE,
            "-a", key,
        )
        if (result.exitCode == 0 || result.error.contains("could not be found", ignoreCase = true)) {
            return
        }
        error(result.error.ifBlank { "Failed to delete credential from macOS Keychain." })
    }

    private fun runSecurity(vararg args: String): CommandResult {
        val process = ProcessBuilder(listOf("security", *args))
            .redirectErrorStream(false)
            .start()
        val output = process.inputStream.bufferedReader(UTF_8).use { it.readText().trim() }
        val error = process.errorStream.bufferedReader(UTF_8).use { it.readText().trim() }
        return CommandResult(
            exitCode = process.waitFor(),
            output = output,
            error = error,
        )
    }
}

private class WindowsDpapiCredentialStore : SecureCredentialStore {
    private val storeFile = File(File(System.getProperty("user.home")), ".lynmusic/credentials.secure.properties").apply {
        parentFile?.mkdirs()
    }
    private val lock = Any()

    override suspend fun put(key: String, value: String) {
        synchronized(lock) {
            val properties = loadProperties()
            val protectedBytes = Crypt32Util.cryptProtectData(
                value.toByteArray(UTF_8),
                DPAPI_ENTROPY,
                0,
                null,
                null,
            )
            properties.setProperty(key, Base64.getEncoder().encodeToString(protectedBytes))
            storeFile.outputStream().use { output ->
                properties.store(output, "LynMusic secure credentials")
            }
        }
    }

    override suspend fun get(key: String): String? {
        return synchronized(lock) {
            val encoded = loadProperties().getProperty(key) ?: return@synchronized null
            val decrypted = Crypt32Util.cryptUnprotectData(
                Base64.getDecoder().decode(encoded),
                DPAPI_ENTROPY,
                0,
                null,
            )
            String(decrypted, UTF_8)
        }
    }

    override suspend fun remove(key: String) {
        synchronized(lock) {
            val properties = loadProperties()
            if (properties.remove(key) != null) {
                storeFile.outputStream().use { output ->
                    properties.store(output, "LynMusic secure credentials")
                }
            }
        }
    }

    private fun loadProperties(): Properties {
        return Properties().also { properties ->
            if (storeFile.exists()) {
                storeFile.inputStream().use { input -> properties.load(input) }
            }
        }
    }
}

private class InMemoryJvmCredentialStore : SecureCredentialStore {
    private val values = linkedMapOf<String, String>()

    override suspend fun put(key: String, value: String) {
        values[key] = value
    }

    override suspend fun get(key: String): String? = values[key]

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}

private data class CommandResult(
    val exitCode: Int,
    val output: String,
    val error: String,
)

private const val MAC_KEYCHAIN_SERVICE = "top.iwesley.lyn.music.credentials"
private const val CREDENTIALS_LOG_TAG = "Credentials"
private val DPAPI_ENTROPY = "LynMusic Desktop Credential Store".toByteArray(UTF_8)
