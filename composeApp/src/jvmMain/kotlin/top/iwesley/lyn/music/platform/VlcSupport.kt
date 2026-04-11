package top.iwesley.lyn.music.platform

import java.nio.file.Files
import java.nio.file.Path
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.LinuxNativeDiscoveryStrategy
import uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy
import uk.co.caprica.vlcj.factory.discovery.strategy.OsxNativeDiscoveryStrategy
import uk.co.caprica.vlcj.factory.discovery.strategy.WindowsNativeDiscoveryStrategy

internal fun resolveDesktopVlcEffectivePath(
    manualPath: String?,
    autoDetectedPath: String?,
): String? {
    return manualPath?.trim()?.takeIf { it.isNotBlank() }
        ?: autoDetectedPath?.trim()?.takeIf { it.isNotBlank() }
}

internal fun normalizeDesktopVlcSelection(
    selection: Path,
    osName: String = System.getProperty("os.name"),
    listEntries: (Path) -> List<String> = ::listDesktopVlcDirectoryEntries,
): Path? {
    val normalizedSelection = selection.toAbsolutePath().normalize()
    val candidate = when {
        isDesktopMacOs(osName) && normalizedSelection.fileName?.toString()?.endsWith(".app", ignoreCase = true) == true ->
            normalizedSelection.resolve("Contents").resolve("MacOS").resolve("lib")

        else -> normalizedSelection
    }
    return candidate.takeIf { containsDesktopVlcLibraries(it, osName, listEntries) }
}

internal fun desktopVlcInvalidSelectionMessage(osName: String = System.getProperty("os.name")): String {
    return if (isDesktopMacOs(osName)) {
        "请选择 VLC.app，或直接选择包含 libvlc.dylib 和 libvlccore.dylib 的目录。"
    } else {
        "请选择包含 libvlc 和 libvlccore 的 VLC 安装目录。"
    }
}

internal fun createDesktopVlcDiscovery(manualPath: String?): NativeDiscovery {
    val normalizedManualPath = manualPath?.trim()?.takeIf { it.isNotBlank() } ?: return NativeDiscovery()
    val delegate = desktopVlcDiscoveryStrategy() ?: return NativeDiscovery()
    return NativeDiscovery(FixedPathNativeDiscoveryStrategy(normalizedManualPath, delegate))
}

private fun containsDesktopVlcLibraries(
    directory: Path,
    osName: String,
    listEntries: (Path) -> List<String>,
): Boolean {
    val entries = listEntries(directory)
    return when {
        isDesktopMacOs(osName) -> {
            entries.any { it.equals("libvlc.dylib", ignoreCase = true) } &&
                entries.any { it.equals("libvlccore.dylib", ignoreCase = true) }
        }

        isDesktopWindows(osName) -> {
            entries.any { it.equals("libvlc.dll", ignoreCase = true) } &&
                entries.any { it.equals("libvlccore.dll", ignoreCase = true) }
        }

        else -> {
            entries.any { it.startsWith("libvlc.so", ignoreCase = true) } &&
                entries.any { it.startsWith("libvlccore.so", ignoreCase = true) }
        }
    }
}

private fun listDesktopVlcDirectoryEntries(directory: Path): List<String> {
    return runCatching {
        if (!Files.isDirectory(directory)) {
            emptyList()
        } else {
            Files.newDirectoryStream(directory).use { stream ->
                stream.map { it.fileName?.toString().orEmpty() }.toList()
            }
        }
    }.getOrDefault(emptyList())
}

private fun desktopVlcDiscoveryStrategy(osName: String = System.getProperty("os.name")): NativeDiscoveryStrategy? {
    return when {
        isDesktopMacOs(osName) -> OsxNativeDiscoveryStrategy()
        isDesktopWindows(osName) -> WindowsNativeDiscoveryStrategy()
        else -> LinuxNativeDiscoveryStrategy()
    }
}

private fun isDesktopMacOs(osName: String): Boolean {
    return osName.contains("mac", ignoreCase = true)
}

private fun isDesktopWindows(osName: String): Boolean {
    return osName.contains("win", ignoreCase = true)
}

private class FixedPathNativeDiscoveryStrategy(
    private val nativeLibraryPath: String,
    private val delegate: NativeDiscoveryStrategy,
) : NativeDiscoveryStrategy {
    override fun supported(): Boolean = delegate.supported()

    override fun discover(): String = nativeLibraryPath

    override fun onFound(path: String): Boolean = delegate.onFound(path)

    override fun onSetPluginPath(path: String): Boolean = delegate.onSetPluginPath(path)
}
