package top.iwesley.lyn.music.testing

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.resolveUiText

/** Legacy wording fixtures explicitly exercise Simplified Chinese UI resolution. */
suspend fun assertLocalizedEquals(expected: String?, actual: UiText?, message: String? = null) {
    kotlin.test.assertEquals(expected, actual?.let { resolveUiText(it, AppLanguage.SimplifiedChinese) }, message)
}

fun <T> assertLocalizedEquals(expected: T, actual: T, message: String? = null) {
    kotlin.test.assertEquals(expected, actual, message)
}
