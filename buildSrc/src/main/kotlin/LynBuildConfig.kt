import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project
import java.util.Properties

private const val ANDROID_SIGNING_STORE_FILE = "ANDROID_SIGNING_STORE_FILE"
private const val ANDROID_SIGNING_STORE_PASSWORD = "ANDROID_SIGNING_STORE_PASSWORD"
private const val ANDROID_SIGNING_KEY_ALIAS = "ANDROID_SIGNING_KEY_ALIAS"
private const val ANDROID_SIGNING_KEY_PASSWORD = "ANDROID_SIGNING_KEY_PASSWORD"
private const val ANDROID_LINT_ON_ASSEMBLE = "lyn.android.lintOnAssemble"

fun Project.readSharedVersionConfig(): Map<String, String> =
    rootProject.file("app-version.xcconfig")
        .readLines()
        .map(String::trim)
        .filter { line ->
            line.isNotEmpty() &&
                !line.startsWith("//") &&
                !line.startsWith("#")
        }
        .mapNotNull { line ->
            val separatorIndex = line.indexOf('=')
            if (separatorIndex < 0) {
                null
            } else {
                line.substring(0, separatorIndex).trim() to
                    line.substring(separatorIndex + 1).trim()
            }
        }
        .toMap()

fun Project.isAndroidLintOnAssembleEnabled(): Boolean {
    return rootProject.readLocalProperties()
        .getProperty(ANDROID_LINT_ON_ASSEMBLE)
        ?.trim()
        ?.toBooleanStrictOrNull()
        ?: true
}

// Inline into the application script: buildSrc must not load AGP's plugin implementation.
@Suppress("NOTHING_TO_INLINE")
inline fun ApplicationExtension.configureLynReleaseSigning(project: Project) {
    val signingConfig = project.readAndroidReleaseSigningConfig() ?: return
    val releaseSigningConfig = signingConfigs.maybeCreate("release").apply {
        storeFile = project.rootProject.file(signingConfig.storeFile)
        storePassword = signingConfig.storePassword
        keyAlias = signingConfig.keyAlias
        keyPassword = signingConfig.keyPassword
    }
    buildTypes.getByName("release").signingConfig = releaseSigningConfig
}

@PublishedApi
internal data class AndroidReleaseSigningConfig(
    val storeFile: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

@PublishedApi
internal fun Project.readAndroidReleaseSigningConfig(): AndroidReleaseSigningConfig? {
    val entries = mapOf(
        ANDROID_SIGNING_STORE_FILE to secret(ANDROID_SIGNING_STORE_FILE),
        ANDROID_SIGNING_STORE_PASSWORD to secret(ANDROID_SIGNING_STORE_PASSWORD),
        ANDROID_SIGNING_KEY_ALIAS to secret(ANDROID_SIGNING_KEY_ALIAS),
        ANDROID_SIGNING_KEY_PASSWORD to secret(ANDROID_SIGNING_KEY_PASSWORD),
    )
    val missingKeys = entries
        .filterValues(String?::isNullOrBlank)
        .keys

    if (missingKeys.size == entries.size) {
        return null
    }
    if (missingKeys.isNotEmpty()) {
        error(
            "Incomplete Android release signing config. Missing: " +
                missingKeys.joinToString(", "),
        )
    }

    return AndroidReleaseSigningConfig(
        storeFile = requireNotNull(entries[ANDROID_SIGNING_STORE_FILE]),
        storePassword = requireNotNull(entries[ANDROID_SIGNING_STORE_PASSWORD]),
        keyAlias = requireNotNull(entries[ANDROID_SIGNING_KEY_ALIAS]),
        keyPassword = requireNotNull(entries[ANDROID_SIGNING_KEY_PASSWORD]),
    )
}

private fun Project.secret(name: String): String? =
    providers.environmentVariable(name).orNull
        ?: rootProject.readLocalProperties().getProperty(name)

private fun Project.readLocalProperties(): Properties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use(::load)
        }
    }
