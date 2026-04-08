package top.iwesley.lyn.music

internal enum class SettingsSection {
    Theme,
    Lyrics,
}

internal sealed interface SettingsMobileNavigation {
    data object List : SettingsMobileNavigation

    data class Detail(val section: SettingsSection) : SettingsMobileNavigation
}

internal fun defaultDesktopSettingsSection(): SettingsSection {
    return SettingsSection.Theme
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
