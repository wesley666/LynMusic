package top.iwesley.lyn.music.feature.settings

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.uiErrorText
import top.iwesley.lyn.music.core.model.plus

import top.iwesley.lyn.music.core.model.LynMusicUpdateLinks

enum class AppUpdateUiStatus {
    Idle,
    Checking,
    Error,
    UpdateAvailable,
    UpToDate,
}

data class AppUpdateUiModel(
    val status: AppUpdateUiStatus,
    val message: UiText? = null,
    val latestVersion: String? = null,
    val downloadUrl: String = LynMusicUpdateLinks.RELEASES_URL,
    val errorMessage: UiText? = null,
)

fun SettingsState.toAppUpdateUiModel(): AppUpdateUiModel {
    val release = appUpdateLatestRelease
    return when {
        appUpdateChecking -> AppUpdateUiModel(
            status = AppUpdateUiStatus.Checking,
            message = uiText(Res.string.update_checking_latest_version),
        )

        release != null && appUpdateHasNewVersion == true -> AppUpdateUiModel(
            status = AppUpdateUiStatus.UpdateAvailable,
            message = uiText(Res.string.update_download_channels_hint),
            latestVersion = release.tagName,
            downloadUrl = release.htmlUrl.takeIf { it.isNotBlank() } ?: LynMusicUpdateLinks.RELEASES_URL,
            errorMessage = appUpdateError,
        )

        appUpdateError != null -> AppUpdateUiModel(
            status = AppUpdateUiStatus.Error,
            message = appUpdateError,
        )

        release != null && appUpdateHasNewVersion == false -> AppUpdateUiModel(
            status = AppUpdateUiStatus.UpToDate,
            message = uiText(Res.string.update_already_latest),
        )

        else -> AppUpdateUiModel(status = AppUpdateUiStatus.Idle)
    }
}
