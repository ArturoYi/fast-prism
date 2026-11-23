/**
 * 记住主题和语言。没选过时，主题用深色，语言跟着系统：英语用英语，其余用中文。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** 示例自己的偏好文件。通知是否拒绝过也写在这里。 */
internal const val SAMPLE_PREFS: String = "fastdownload_sample"

/**
 * 黑白两套界面。没保存过时用 [Dark]。
 */
internal enum class AppTheme {
    Dark,
    Light,
    ;

    val key: String
        get() = when (this) {
            Dark -> "dark"
            Light -> "light"
        }

    companion object {
        /** 认不出的值按深色处理。 */
        fun fromKey(key: String?): AppTheme = if (key == "light") Light else Dark
    }
}

/**
 * 界面语言。选项上的字固定写「中文」和 English，避免切错之后找不到路。
 */
internal enum class AppLanguage {
    Chinese,
    English,
    ;

    fun toLocale(): Locale = when (this) {
        Chinese -> Locale.SIMPLIFIED_CHINESE
        English -> Locale.ENGLISH
    }

    val key: String
        get() = when (this) {
            Chinese -> "zh"
            English -> "en"
        }

    companion object {
        fun fromKey(key: String?): AppLanguage? = when (key) {
            "zh" -> Chinese
            "en" -> English
            else -> null
        }

        /** 系统语言是英语时用英语，否则用中文。 */
        fun fromSystem(): AppLanguage {
            val language = Locale.getDefault().language
            return if (language.equals("en", ignoreCase = true)) English else Chinese
        }
    }
}

/**
 * 主题和语言的当前值。改完立刻反映到界面，并写进偏好。
 *
 * @param context 用来打开偏好。会使用 applicationContext。
 */
internal class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(SAMPLE_PREFS, Context.MODE_PRIVATE)

    /** 当前主题。读取发生在组合里时，变化会刷新界面。 */
    var theme by mutableStateOf(readTheme())
        private set

    /** 当前语言。读取发生在组合里时，变化会刷新界面。 */
    var language by mutableStateOf(readLanguage())
        private set

    /** 换成另一套黑白主题。已经是这套时什么也不做。 */
    fun chooseTheme(value: AppTheme) {
        if (theme == value) return
        theme = value
        prefs.edit().putString(KEY_THEME, value.key).apply()
    }

    /** 换成中文或英语。已经是这种语言时什么也不做。 */
    fun chooseLanguage(value: AppLanguage) {
        if (language == value) return
        language = value
        prefs.edit().putString(KEY_LANGUAGE, value.key).apply()
    }

    private fun readTheme(): AppTheme = AppTheme.fromKey(prefs.getString(KEY_THEME, null))

    private fun readLanguage(): AppLanguage {
        return AppLanguage.fromKey(prefs.getString(KEY_LANGUAGE, null)) ?: AppLanguage.fromSystem()
    }

    private companion object {
        const val KEY_THEME: String = "theme"
        const val KEY_LANGUAGE: String = "language"
    }
}
