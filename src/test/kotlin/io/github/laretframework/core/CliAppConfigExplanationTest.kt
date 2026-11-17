package io.github.laretframework.core

import io.github.laretframework.model.Command
import io.github.laretframework.model.CommandGroup
import io.github.laretframework.model.Option
import kotlin.test.Test
import kotlin.test.assertEquals

class CliAppConfigExplanationTest {
    @Test
    fun test_public_config_facade_explains_command_bound_flag() {
        val command = Command(
            name = "serve",
            options = listOf(Option(short = "p", long = "port", default = "80", configKey = "server.port")),
        )
        val app = CliApp("test", groups = listOf(CommandGroup("server", commands = listOf(command))))

        val explanation = app.explainConfig("server-port", "server serve", mapOf("port" to "9090"))

        assertEquals("server.port", explanation.key)
        assertEquals("9090", explanation.winner?.value)
        assertEquals("port", explanation.winner?.source)
    }
}
