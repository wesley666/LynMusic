package top.iwesley.lyn.music

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

internal sealed interface SettingsMobileNavigation {
    data object List : SettingsMobileNavigation

    data class Detail(val section: SettingsSection) : SettingsMobileNavigation
}

internal fun settingsSectionsForPlatform(platform: PlatformDescriptor): List<SettingsSection> {
    return SettingsSection.entries.filter { section ->
        section != SettingsSection.Help || platform.name == ANDROID_PLATFORM_NAME
    }
}

internal fun defaultSettingsSection(platform: PlatformDescriptor): SettingsSection {
    return settingsSectionsForPlatform(platform).first()
}

internal fun resolveSettingsSection(sectionName: String?): SettingsSection? {
    return SettingsSection.entries.firstOrNull { it.name == sectionName }
}

internal fun toSettingsMobileNavigation(sectionName: String?): SettingsMobileNavigation {
    val section = resolveSettingsSection(sectionName) ?: return SettingsMobileNavigation.List
    return SettingsMobileNavigation.Detail(section)
}

internal fun openSettingsMobileNavigation(section: SettingsSection): SettingsMobileNavigation {
    return SettingsMobileNavigation.Detail(section)
}

internal fun closeSettingsMobileNavigation(): SettingsMobileNavigation {
    return SettingsMobileNavigation.List
}
