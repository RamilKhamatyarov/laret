package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.config.registry.ConfigRegistry
import com.rkhamatyarov.laret.dsl.cli
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConfigRegistryWiringTest {
    private val savedOut = System.out
    private val savedErr = System.err
    private lateinit var outBuf: ByteArrayOutputStream

    @BeforeEach
    fun redirectStreams() {
        outBuf = ByteArrayOutputStream()
        val ps = PrintStream(outBuf)
        System.setOut(ps)
        System.setErr(ps)
    }

    @AfterEach
    fun restoreStreams() {
        System.setOut(savedOut)
        System.setErr(savedErr)
    }

    @Test
    fun `ctx_config is populated (not empty) after command executes`() {
        var captured: ConfigRegistry? = null
        val app = cli("test") {
            group("grp") {
                command("run") {
                    option("f", "format", default = "plain")
                    action { ctx -> captured = ctx.config }
                }
            }
        }
        app.init()
        app.runForTest(arrayOf("grp", "run"))

        val registry = assertNotNull(captured)
        assertEquals("plain", registry.getString("grp.format"))
    }

    @Test
    fun `ctx_config resolves the declared default for a non-persistent option`() {
        var captured: ConfigRegistry? = null
        val app = cli("test") {
            group("grp") {
                command("run") {
                    option("f", "format", default = "plain")
                    action { ctx -> captured = ctx.config }
                }
            }
        }
        app.init()
        app.runForTest(arrayOf("grp", "run"))

        assertEquals("plain", captured!!.getString("grp.format"))
    }

    @Test
    fun `declared default is used when no flag and no config file`() {
        var port: String? = null
        val app = cli("test") {
            group("srv") {
                command("start") {
                    option("p", "port", default = "3000")
                    action { ctx -> port = ctx.option("port") }
                }
            }
        }
        app.init()
        app.runForTest(arrayOf("srv", "start"))

        assertEquals("3000", port)
    }

    @Test
    fun `cli flag overrides declared default`() {
        var port: String? = null
        val app = cli("test") {
            group("srv") {
                command("start") {
                    option("p", "port", default = "3000")
                    action { ctx -> port = ctx.option("port") }
                }
            }
        }
        app.init()
        app.runForTest(arrayOf("srv", "start", "--port", "9090"))

        assertEquals("9090", port)
    }

    @Test
    fun `short flag is resolved to the same option as long flag`() {
        var format: String? = null
        val app = cli("test") {
            group("out") {
                command("show") {
                    option("f", "format", default = "plain")
                    action { ctx -> format = ctx.option("format") }
                }
            }
        }
        app.init()
        app.runForTest(arrayOf("out", "show", "-f", "json"))

        assertEquals("json", format)
    }

    @Test
    fun `config file value overrides declared default when no cli flag`(@TempDir tmp: File) {
        val cfg = File(tmp, "config.yml").also {
            it.writeText("srv:\n  port: \"7070\"\n")
        }
        var port: String? = null
        val app = cli("test") {
            group("srv") {
                command("start") {
                    option("p", "port", default = "3000")
                    action { ctx -> port = ctx.option("port") }
                }
            }
        }
        app.init(configPath = cfg.absolutePath)
        app.runForTest(arrayOf("srv", "start"))

        assertEquals("7070", port)
    }

    @Test
    fun `cli flag overrides config file value`(@TempDir tmp: File) {
        val cfg = File(tmp, "config.yml").also {
            it.writeText("srv:\n  port: \"7070\"\n")
        }
        var port: String? = null
        val app = cli("test") {
            group("srv") {
                command("start") {
                    option("p", "port", default = "3000")
                    action { ctx -> port = ctx.option("port") }
                }
            }
        }
        app.init(configPath = cfg.absolutePath)
        app.runForTest(arrayOf("srv", "start", "--port", "9090"))

        assertEquals("9090", port)
    }

    @Test
    fun `config file value is also visible via ctx_config registry`(@TempDir tmp: File) {
        val cfg = File(tmp, "config.yml").also {
            it.writeText("srv:\n  port: \"7070\"\n")
        }
        var captured: ConfigRegistry? = null
        val app = cli("test") {
            group("srv") {
                command("start") {
                    option("p", "port", default = "3000")
                    action { ctx -> captured = ctx.config }
                }
            }
        }
        app.init(configPath = cfg.absolutePath)
        app.runForTest(arrayOf("srv", "start"))

        assertEquals("7070", captured!!.getString("srv.port"))
    }

    @Test
    fun `custom configKey maps option to non-default registry path`(@TempDir tmp: File) {
        val cfg = File(tmp, "config.yml").also {
            it.writeText("custom:\n  output:\n    format: table\n")
        }
        var format: String? = null
        val app = cli("test") {
            group("report") {
                command("show") {
                    option("f", "format", default = "plain", configKey = "custom.output.format")
                    action { ctx -> format = ctx.option("format") }
                }
            }
        }
        app.init(configPath = cfg.absolutePath)
        app.runForTest(arrayOf("report", "show"))

        assertEquals("table", format)
    }

    @Test
    fun `cli flag still wins over custom configKey file value`(@TempDir tmp: File) {
        val cfg = File(tmp, "config.yml").also {
            it.writeText("custom:\n  output:\n    format: table\n")
        }
        var format: String? = null
        val app = cli("test") {
            group("report") {
                command("show") {
                    option("f", "format", default = "plain", configKey = "custom.output.format")
                    action { ctx -> format = ctx.option("format") }
                }
            }
        }
        app.init(configPath = cfg.absolutePath)
        app.runForTest(arrayOf("report", "show", "--format", "yaml"))

        assertEquals("yaml", format)
    }

    @Test
    fun `hyphenated option accessible via all normalized key forms in ctx_config`() {
        var captured: ConfigRegistry? = null
        val app = cli("test") {
            group("file") {
                command("list") {
                    option("m", "max-size", "Max file size", "0", true)
                    action { ctx -> captured = ctx.config }
                }
            }
        }
        app.init()
        app.runForTest(arrayOf("file", "list", "--max-size", "1024"))

        val cfg = assertNotNull(captured)
        assertEquals("1024", cfg.getString("file.max.size"))
        assertEquals("1024", cfg.getString("max-size"))
        assertEquals("1024", cfg.getString("max.size"))
        assertEquals("1024", cfg.getString("max_size"))
    }

    @Test
    fun `hyphenated option without flag uses default from ctx_config`() {
        var captured: ConfigRegistry? = null
        val app = cli("test") {
            group("file") {
                command("list") {
                    option("m", "max-size", "Max file size", "0", true)
                    action { ctx -> captured = ctx.config }
                }
            }
        }
        app.init()
        app.runForTest(arrayOf("file", "list"))

        assertEquals("0", assertNotNull(captured).getString("file.max.size"))
    }

    @Test
    fun `init throws RuntimeException with clear message on validation failure`(@TempDir tmp: File) {
        val cfg = File(tmp, "config.yml").also {
            it.writeText("output:\n  format: invalid_format\n")
        }
        val app = cli("test") { }
        val ex = assertFailsWith<RuntimeException> {
            app.init(configPath = cfg.absolutePath)
        }
        assertEquals("Configuration validation failed", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun `config loaded via init is not overwritten by dispatch when no global flags in args`(@TempDir tmp: File) {
        val cfg = File(tmp, "config.yml").also {
            it.writeText("app:\n  version: \"2.0.0\"\n")
        }
        var capturedOption: String? = null
        val app = cli("test") {
            group("grp") {
                command("run") {
                    option("f", "format", default = "plain")
                    action { ctx -> capturedOption = ctx.option("format") }
                }
            }
        }
        app.init(configPath = cfg.absolutePath)
        val versionAfterInit = app.getAppConfig().app.version

        app.runForTest(arrayOf("grp", "run"))

        assertEquals("2.0.0", versionAfterInit)
        assertEquals(
            versionAfterInit,
            app.getAppConfig().app.version,
            "dispatch() must not re-init when no global flags present",
        )
        assertEquals("plain", capturedOption)
    }

    @Test
    fun `profile config file is resolved and not lost after run`(@TempDir tmp: File) {
        val cfg = File(tmp, ".laret.prod.yml").also {
            it.writeText("app:\n  version: \"prod-1.0\"\n")
        }
        var capturedOption: String? = null
        val app = cli("test") {
            group("grp") {
                command("run") {
                    option("f", "format", default = "plain")
                    action { ctx -> capturedOption = ctx.option("format") }
                }
            }
        }
        app.init(configPath = cfg.absolutePath, profile = "prod")
        val versionAfterInit = app.getAppConfig().app.version

        app.runForTest(arrayOf("grp", "run"))

        assertEquals("prod-1.0", versionAfterInit)
        assertEquals(versionAfterInit, app.getAppConfig().app.version)
        assertEquals("plain", capturedOption)
    }

    @Test
    fun `boolean option from config file is resolved via ctx_optionBool`(@TempDir tmp: File) {
        val cfg = File(tmp, "config.yml").also {
            it.writeText("mygrp:\n  verbose: true\n")
        }
        var verbose: Boolean? = null
        val app = cli("test") {
            group("mygrp") {
                command("run") {
                    option("v", "verbose", default = "false", takesValue = true)
                    action { ctx -> verbose = ctx.optionBool("verbose") }
                }
            }
        }
        app.init(configPath = cfg.absolutePath)
        app.runForTest(arrayOf("mygrp", "run"))

        assertEquals(true, verbose)
    }

    @Test
    fun `cli boolean flag overrides config file boolean`(@TempDir tmp: File) {
        val cfg = File(tmp, "config.yml").also {
            it.writeText("mygrp:\n  verbose: true\n")
        }
        var verbose: Boolean? = null
        val app = cli("test") {
            group("mygrp") {
                command("run") {
                    option("v", "verbose", default = "false", takesValue = true)
                    action { ctx -> verbose = ctx.optionBool("verbose") }
                }
            }
        }
        app.init(configPath = cfg.absolutePath)
        app.runForTest(arrayOf("mygrp", "run", "--verbose", "false"))

        assertEquals(false, verbose)
    }
}
