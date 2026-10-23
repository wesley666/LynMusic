package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.core.model.PlatformDescriptor

internal enum class SettingsSection {
    General,
    Theme,
    Lyrics,
    Storage,
    AboutDevice,
    AboutApp,
    Help,
}

internal fun settingsSections(): List<SettingsSection> {
    return SettingsSection.entries.filter { section ->
        section != SettingsSection.Help
    }
}

internal fun defaultSettingsSection(): SettingsSection {
    return settingsSections().first()
}

internal fun resolveSettingsSection(sectionName: String?): SettingsSection? {
    return SettingsSection.entries.firstOrNull { it.name == sectionName }
}

internal fun shouldShowCustomDataLocation(platform: PlatformDescriptor): Boolean {
    return platform.capabilities.supportsCustomDataLocation
}
