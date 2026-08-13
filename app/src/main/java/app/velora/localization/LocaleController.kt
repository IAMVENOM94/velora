package app.velora.localization

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLanguage(val languageTag: String?) {
    System(null),
    Russian("ru"),
    English("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage = when (tag?.substringBefore('-')?.lowercase()) {
            "ru" -> Russian
            "en" -> English
            else -> System
        }
    }
}

object LocaleController {
    fun current(): AppLanguage = AppLanguage.fromTag(
        AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag()
    )

    fun apply(language: AppLanguage) {
        val locales = language.languageTag
            ?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
