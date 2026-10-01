package top.iwesley.lyn.music.domain

import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.UiTextArgumentException
import top.iwesley.lyn.music.core.model.UiTextException

/** Classification data is separate from both UI resources and diagnostic messages. */
internal interface RemoteSourceHttpFailure {
    val httpStatusCode: Int?
}

internal class RemoteSourceHttpException(
    override val httpStatusCode: Int,
    text: UiText,
) : UiTextException(text), RemoteSourceHttpFailure

internal class RemoteSourceHttpArgumentException(
    override val httpStatusCode: Int,
    text: UiText,
) : UiTextArgumentException(text), RemoteSourceHttpFailure

internal class RemoteSourceRequestException(text: UiText, cause: Throwable) : UiTextException(text, cause)
