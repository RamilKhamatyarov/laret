package com.rkhamatyarov.laret.core

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Paths
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
        if (envLocale != null) return parseLocaleTag(envLocale)
        val persisted = readPersistedTag()
        if (persisted != null) return parseLocaleTag(persisted)
        return Locale.getDefault()
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

    fun setLocale(tag: String) = setLocale(parseLocaleTag(tag))

    /** Persist [tag] to `~/.laret/locale` and apply it for the current session. */
    fun saveLocale(tag: String) {
        setLocale(tag)
        try {
            val path = prefsFile()
            Files.createDirectories(path.parent)
            Files.writeString(path, tag)
        } catch (_: Exception) {}
    }

    /** Delete the persisted locale file and revert to system default for the current session. */
    fun clearLocale() {
        try { Files.delete(prefsFile()) } catch (_: NoSuchFileException) {}
        currentLocale = Locale.getDefault()
    }

    /** The tag stored in `~/.laret/locale`, or null if none is persisted. */
    fun persistedTag(): String? = readPersistedTag()

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

    private fun readPersistedTag(): String? = try {
        Files.readString(prefsFile()).trim().takeIf { it.isNotEmpty() }
    } catch (_: Exception) { null }

    private fun prefsFile() = Paths.get(
        System.getProperty("user.home") ?: ".",
        ".laret", "locale",
    )
}
