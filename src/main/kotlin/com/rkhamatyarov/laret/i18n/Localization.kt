package com.rkhamatyarov.laret.i18n

import java.text.MessageFormat
import java.util.Locale
import java.util.MissingResourceException
import java.util.ResourceBundle

object Localization {

    const val BUNDLE_BASE_NAME: String = "i18n.messages"

    @Volatile
    private var currentLocale: Locale = resolveInitialLocale()

    internal fun resolveInitialLocale(): Locale {
        val envLocale = System.getenv("LARET_LOCALE")?.trim()?.takeIf { it.isNotEmpty() }
        return if (envLocale != null) parseLocaleTag(envLocale) else Locale.getDefault()
    }

    internal fun parseLocaleTag(tag: String): Locale {
        val parts = tag.split('_', '-')
        return when (parts.size) {
            1 -> Locale.of(parts[0])
            2 -> Locale.of(parts[0], parts[1])
            else -> Locale.of(parts[0], parts[1], parts.drop(2).joinToString("_"))
        }
    }

    fun getLocale(): Locale = currentLocale

    fun setLocale(locale: Locale) {
        currentLocale = locale
    }

    fun setLocale(tag: String) {
        setLocale(parseLocaleTag(tag))
    }

    fun t(key: String, vararg args: Any?): String {
        val rawPattern = lookup(key) ?: return key
        if (args.isEmpty()) return rawPattern
        return try {
            MessageFormat(rawPattern, currentLocale).format(args)
        } catch (_: IllegalArgumentException) {
            rawPattern
        }
    }

    internal fun lookup(key: String): String? {
        val tryLocale = { loc: Locale ->
            try {
                ResourceBundle.getBundle(BUNDLE_BASE_NAME, loc).getString(key)
            } catch (_: MissingResourceException) {
                null
            }
        }
        return tryLocale(currentLocale) ?: tryLocale(Locale.ENGLISH)
    }

    fun hasKey(key: String): Boolean = lookup(key) != null
}
