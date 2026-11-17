package io.github.laretframework.core

import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.ResourceBundle
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ConfigLocalizationTest {
    @Test
    fun test_config_message_keys_exist_in_english_and_spanish() {
        val english = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH)
        val spanish = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("es"))
        val configKeys = english.keySet().filter { it.startsWith("config.") }.toSet()

        assertEquals(configKeys, spanish.keySet().filter { it.startsWith("config.") }.toSet())
        assertNotEquals(english.getString("config.explain.missing"), spanish.getString("config.explain.missing"))
    }
}
