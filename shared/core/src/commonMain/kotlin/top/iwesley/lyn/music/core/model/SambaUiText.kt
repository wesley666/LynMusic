package top.iwesley.lyn.music.core.model

import top.iwesley.lyn.music.resources.*

enum class SambaOperation(private val resource: org.jetbrains.compose.resources.StringResource) {
    Open(Res.string.samba_operation_open),
    Read(Res.string.samba_operation_read),
    ProbeSize(Res.string.samba_operation_probe_size);

    val text: UiText get() = uiText(resource)
}

fun sambaFailureText(operation: SambaOperation, throwable: Throwable, sourceReference: String? = null): UiText {
    val failure = uiText(Res.string.samba_operation_failed, operation.text, throwable.sambaFailureDetail())
    return sourceReference?.takeIf { it.isNotBlank() }?.let { uiText(Res.string.samba_failure_with_source, failure, UiText.Raw(it)) }
        ?: failure
}

fun sambaPlaybackFailureText(throwable: Throwable): UiText =
    uiText(Res.string.samba_playback_failed, throwable.sambaFailureDetail())

private fun Throwable.sambaFailureDetail(): UiText = uiFailureTextOrNull()
    ?: message?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) }
    ?: this::class.simpleName?.let { UiText.Raw(it) }
    ?: uiText(Res.string.samba_unknown_error)
