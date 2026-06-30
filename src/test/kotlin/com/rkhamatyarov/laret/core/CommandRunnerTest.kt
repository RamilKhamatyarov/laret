package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.dsl.cli
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandRunnerTest {

    @Test
    fun test_dryRunFlag_sets_context_isDryRun() {
        var observedDryRun = false
        val app = cli(name = "test", version = "1.0.0") {
            group(name = "file") {
                command(name = "create") {
                    action { ctx -> observedDryRun = ctx.isDryRun }
                }
            }
        }

        val exitCode = app.runForTest(arrayOf("file", "create", "--dry-run"))

        assertEquals(0, exitCode)
        assertTrue(observedDryRun)
    }

    @Test
    fun test_shortN_is_not_a_global_dryRun_flag_and_stays_command_scoped() {
        var observedDryRun = true
        var observedName = ""
        val app = cli(name = "test", version = "1.0.0") {
            group(name = "new") {
                command(name = "project") {
                    option("n", "name", "Project name", "", true)
                    action { ctx ->
                        observedDryRun = ctx.isDryRun
                        observedName = ctx.option("name")
                    }
                }
            }
        }

        val exitCode = app.runForTest(arrayOf("new", "project", "-n", "demo"))

        assertEquals(0, exitCode)
        assertFalse(observedDryRun)
        assertEquals("demo", observedName)
    }
}
