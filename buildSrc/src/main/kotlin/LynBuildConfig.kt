import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import java.util.Properties

private const val ANDROID_SIGNING_STORE_FILE = "ANDROID_SIGNING_STORE_FILE"
private const val ANDROID_SIGNING_STORE_PASSWORD = "ANDROID_SIGNING_STORE_PASSWORD"
private const val ANDROID_SIGNING_KEY_ALIAS = "ANDROID_SIGNING_KEY_ALIAS"
private const val ANDROID_SIGNING_KEY_PASSWORD = "ANDROID_SIGNING_KEY_PASSWORD"

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

fun Any.configureLynReleaseSigning(project: Project) {
    val signingConfig = project.readAndroidReleaseSigningConfig() ?: return
    val releaseSigningConfig = namedContainer("signingConfigs").let { signingConfigs ->
        signingConfigs.findByName("release")
            ?: signingConfigs.create("release")
    }

    releaseSigningConfig.invokeSetter("setStoreFile", project.rootProject.file(signingConfig.storeFile))
    releaseSigningConfig.invokeSetter("setStorePassword", signingConfig.storePassword)
    releaseSigningConfig.invokeSetter("setKeyAlias", signingConfig.keyAlias)
    releaseSigningConfig.invokeSetter("setKeyPassword", signingConfig.keyPassword)

    namedContainer("buildTypes")
        .getByName("release")
        .invokeSetter("setSigningConfig", releaseSigningConfig)
}

private data class AndroidReleaseSigningConfig(
    val storeFile: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

private fun Project.readAndroidReleaseSigningConfig(): AndroidReleaseSigningConfig? {
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

@Suppress("UNCHECKED_CAST")
private fun Any.namedContainer(name: String): NamedDomainObjectContainer<Any> =
    invokeGetter(name) as? NamedDomainObjectContainer<Any>
        ?: error("Android DSL property '$name' is not a named domain object container.")

private fun Any.invokeGetter(name: String): Any {
    val methodName = "get" + name.replaceFirstChar(Char::uppercase)
    val method = javaClass.methods.firstOrNull { method ->
        method.name == methodName && method.parameterCount == 0
    } ?: error("Android DSL getter '$methodName' was not found.")

    return method.invoke(this)
}

private fun Any.invokeSetter(name: String, value: Any) {
    val method = javaClass.methods.firstOrNull { method ->
        method.name == name &&
            method.parameterCount == 1 &&
            method.parameterTypes[0].isAssignableFrom(value.javaClass)
    } ?: error("Android DSL setter '$name' was not found.")

    method.invoke(this, value)
}
