/**
 * 记住主题、语言，以及用户有没有自己开始过扫描。
 *
 * 扫描进度不写在这里。这里只记三种里程：还没扫过、扫到一半、已经扫完。
 *
 * @file
 */
package com.arturoyi.albumcleaner.sample.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** 示例自己的偏好文件。 */
internal const val SAMPLE_PREFS: String = "albumcleaner_sample"

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
 * 用户有没有自己开始过扫描。
 *
 * 进程起来时只初始化 SDK，不会因为这个值去调用扫描。
 */
internal enum class ScanMilestone {
    /** 还没点过开始。 */
    Never,

    /** 点过开始，但上一轮没有走到完成。 */
    Partial,

    /** 上一轮已经扫完。 */
    Done,
    ;

    val key: String
        get() = when (this) {
            Never -> "never"
            Partial -> "partial"
            Done -> "done"
        }

    companion object {
        fun fromKey(key: String?): ScanMilestone = when (key) {
            "partial" -> Partial
            "done" -> Done
            else -> Never
        }
    }
}

/**
 * 主题、语言和扫描里程。改完立刻反映到界面，并写进偏好。
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

    /** 扫描里程。界面从相册仓库读，这里负责记住。 */
    var scan by mutableStateOf(readScan())
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

    /**
     * 记下扫描里程。
     *
     * 用 [android.content.SharedPreferences.Editor.commit]，避免进程刚被划掉时把「已经开始」弄丢。
     */
    fun saveScan(value: ScanMilestone) {
        if (scan == value) return
        scan = value
        prefs.edit().putString(KEY_SCAN, value.key).commit()
    }

    private fun readTheme(): AppTheme = AppTheme.fromKey(prefs.getString(KEY_THEME, null))

    private fun readLanguage(): AppLanguage {
        return AppLanguage.fromKey(prefs.getString(KEY_LANGUAGE, null)) ?: AppLanguage.fromSystem()
    }

    private fun readScan(): ScanMilestone = ScanMilestone.fromKey(prefs.getString(KEY_SCAN, null))

    private companion object {
        const val KEY_THEME: String = "theme"
        const val KEY_LANGUAGE: String = "language"
        const val KEY_SCAN: String = "scan"
    }
}
