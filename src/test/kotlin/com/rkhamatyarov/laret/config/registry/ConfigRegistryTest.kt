package com.rkhamatyarov.laret.config.registry

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfigRegistryTest {

    @Test
    fun test_cliFlag_overrides_env_file_and_default_for_key_server_port() {
        val registry = ConfigRegistry()
            .defaults(mapOf("server.port" to 3000))
            .files(
                reader = ConfigFileReader { mapOf("server" to mapOf("port" to 7000)) },
            )
            .env(
                bindings = mapOf("port" to "server.port"),
                envProvider = { mapOf("LARET_SERVER_PORT" to "8080") },
            )
            .flags(
                values = mapOf("port" to "9090"),
                bindings = mapOf("port" to "server.port"),
            )

        assertEquals(9090, registry.getInt("server.port"))
    }

    @Test
    fun test_envVar_overrides_fileConfig_for_key_server_port() {
        val registry = ConfigRegistry()
            .files(
                reader = ConfigFileReader { mapOf("server" to mapOf("port" to 7000)) },
            )
            .env(
                bindings = mapOf("port" to "server.port"),
                envProvider = { mapOf("LARET_SERVER_PORT" to "8080") },
            )

        assertEquals(8080, registry.getInt("server.port"))
    }

    @Test
    fun test_missingKey_returns_null() {
        assertNull(ConfigRegistry().getString("missing.key"))
    }

    @Test
    fun test_emptyEnvPrefix_uses_unprefixed_env_name() {
        val registry = ConfigRegistry()
            .env(
                prefix = "",
                envProvider = { mapOf("SERVER_PORT" to "9090") },
            )

        assertEquals(9090, registry.getInt("server.port"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["server-port", "server_port", "server.port"])
    fun test_normalizedKeys_resolve_same_value(key: String) {
        val registry = ConfigRegistry().defaults(mapOf("server.port" to "9090"))

        assertEquals("9090", registry.getString(key))
    }

    @Test
    fun test_invalidIntConversion_logsDebugAndReturnsFallback() {
        val registry = ConfigRegistry().defaults(mapOf("server.port" to "not-a-number"))

        assertEquals(3000, registry.getInt("server.port", default = 3000))
    }

    @Test
    fun test_direct_lookup_hyphenated_key_returns_value() {
        val layer = FlagLayer(values = mapOf("max-size" to "1024"))
        assertEquals("1024", layer.get("max.size"))
    }

    @Test
    fun test_direct_lookup_underscored_key_returns_value() {
        val layer = FlagLayer(values = mapOf("max-size" to "1024"))
        assertEquals("1024", layer.get("max_size"))
    }

    @Test
    fun test_direct_lookup_dotted_key_returns_value() {
        val layer = FlagLayer(values = mapOf("max-size" to "1024"))
        assertEquals("1024", layer.get("max-size"))
    }

    @Test
    fun test_registry_getString_hyphenated_flag_via_all_key_forms() {
        val registry = ConfigRegistry().flags(values = mapOf("max-size" to "1024"))
        assertEquals("1024", registry.getString("max.size"))
        assertEquals("1024", registry.getString("max-size"))
        assertEquals("1024", registry.getString("max_size"))
    }

    @Test
    fun test_bindings_fallback_still_resolves_after_normalization() {
        val registry = ConfigRegistry()
            .flags(
                values = mapOf("max-size" to "512"),
                bindings = mapOf("max-size" to "file.max.size"),
            )
        assertEquals("512", registry.getString("file.max.size"))
    }

    @Test
    fun test_flags_beat_defaults_when_flags_added_first() {
        val registry = ConfigRegistry()
            .flags(values = mapOf("port" to "9090"), bindings = mapOf("port" to "server.port"))
            .defaults(mapOf("server.port" to "3000"))
        assertEquals("9090", registry.getString("server.port"))
    }

    @Test
    fun test_env_beats_defaults_when_env_added_first() {
        val registry = ConfigRegistry()
            .env(
                envProvider = { mapOf("LARET_SERVER_PORT" to "8080") },
                bindings = mapOf("port" to "server.port"),
            )
            .defaults(mapOf("server.port" to "3000"))
        assertEquals("8080", registry.getString("server.port"))
    }

    @Test
    fun test_flags_beat_env_when_flags_added_first() {
        val registry = ConfigRegistry()
            .flags(values = mapOf("port" to "9090"), bindings = mapOf("port" to "server.port"))
            .env(
                envProvider = { mapOf("LARET_PORT" to "8080") },
                bindings = mapOf("port" to "server.port"),
            )
        assertEquals("9090", registry.getString("server.port"))
    }

    @Test
    fun test_flags_beat_file_when_flags_added_first() {
        val registry = ConfigRegistry()
            .flags(values = mapOf("port" to "9090"), bindings = mapOf("port" to "server.port"))
            .files(reader = ConfigFileReader { mapOf("server" to mapOf("port" to 7000)) })
        assertEquals("9090", registry.getString("server.port"))
    }

    @Test
    fun test_file_beats_defaults_when_file_added_first() {
        val fileLayer = FileLayer(
            reader = ConfigFileReader { mapOf("server" to mapOf("port" to 7000)) },
            exists = { it.name == ".laret.yml" },
            workDir = File("/work"),
            homeDir = File("/home/test"),
        )
        val registry = ConfigRegistry(mutableListOf(fileLayer))
            .defaults(mapOf("server.port" to "3000"))
        assertEquals("7000", registry.getString("server.port"))
    }

    @Test
    fun test_correct_full_precedence_regardless_of_insertion_order() {
        val registry = ConfigRegistry()
            .flags(values = mapOf("port" to "9090"), bindings = mapOf("port" to "server.port"))
            .env(
                envProvider = { mapOf("LARET_SERVER_PORT" to "8080") },
                bindings = mapOf("port" to "server.port"),
            )
            .files(reader = ConfigFileReader { mapOf("server" to mapOf("port" to 7000)) })
            .defaults(mapOf("server.port" to "3000"))
        assertEquals("9090", registry.getString("server.port"))
    }

    @Test
    fun test_getLong_from_long_value() {
        val registry = ConfigRegistry().defaults(mapOf("timeout.ms" to 5_000_000_000L))
        assertEquals(5_000_000_000L, registry.getLong("timeout.ms"))
    }

    @Test
    fun test_getLong_from_string_value() {
        val registry = ConfigRegistry().defaults(mapOf("timeout.ms" to "9999999999"))
        assertEquals(9_999_999_999L, registry.getLong("timeout.ms"))
    }

    @Test
    fun test_getLong_from_int_number_via_Number_branch() {
        val registry = ConfigRegistry().defaults(mapOf("count" to 42))
        assertEquals(42L, registry.getLong("count"))
    }

    @Test
    fun test_getLong_from_BigInteger() {
        val registry = ConfigRegistry().defaults(mapOf("big" to BigInteger.valueOf(Long.MAX_VALUE)))
        assertEquals(Long.MAX_VALUE, registry.getLong("big"))
    }

    @Test
    fun test_getLong_invalid_string_returns_default() {
        val registry = ConfigRegistry().defaults(mapOf("timeout.ms" to "not-a-number"))
        assertEquals(1000L, registry.getLong("timeout.ms", default = 1000L))
    }

    @Test
    fun test_getLong_missing_key_returns_null() {
        assertNull(ConfigRegistry().getLong("missing"))
    }

    @Test
    fun test_getLong_missing_key_returns_explicit_default() {
        assertEquals(500L, ConfigRegistry().getLong("missing", default = 500L))
    }

    @Test
    fun test_getDouble_from_double_value() {
        val registry = ConfigRegistry().defaults(mapOf("ratio" to 3.14))
        assertEquals(3.14, registry.getDouble("ratio"))
    }

    @Test
    fun test_getDouble_from_string() {
        val registry = ConfigRegistry().defaults(mapOf("ratio" to "2.718"))
        assertEquals(2.718, registry.getDouble("ratio"))
    }

    @Test
    fun test_getDouble_from_int_number_via_Number_branch() {
        val registry = ConfigRegistry().defaults(mapOf("scale" to 2))
        assertEquals(2.0, registry.getDouble("scale"))
    }

    @Test
    fun test_getDouble_from_BigDecimal() {
        val bd = BigDecimal("1.23456789")
        val registry = ConfigRegistry().defaults(mapOf("precision" to bd))
        assertEquals(bd.toDouble(), registry.getDouble("precision"))
    }

    @Test
    fun test_getDouble_invalid_string_returns_default() {
        val registry = ConfigRegistry().defaults(mapOf("ratio" to "not-a-number"))
        assertEquals(0.5, registry.getDouble("ratio", default = 0.5))
    }

    @Test
    fun test_getDouble_missing_key_returns_null() {
        assertNull(ConfigRegistry().getDouble("missing"))
    }

    @Test
    fun test_getDouble_missing_key_returns_explicit_default() {
        assertEquals(1.0, ConfigRegistry().getDouble("missing", default = 1.0))
    }

    @Test
    fun test_profileFile_missing_fallsBack_to_baseConfig() {
        val reader = mockk<ConfigFileReader>()
        every { reader.load(match { it.name == ".laret.yml" }) } returns
            mapOf("server" to mapOf("port" to 7000))
        val layer = FileLayer(
            profile = "prod",
            reader = reader,
            exists = { it.name == ".laret.yml" },
            workDir = File("/work"),
            homeDir = File("/home/test"),
        )
        val registry = ConfigRegistry(mutableListOf(layer))

        assertEquals(7000, registry.getInt("server.port"))
        assertEquals(".laret.yml", layer.selectedFile()?.name)
    }
}
