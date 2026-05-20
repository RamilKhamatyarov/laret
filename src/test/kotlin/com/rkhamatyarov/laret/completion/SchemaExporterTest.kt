package com.rkhamatyarov.laret.completion

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.rkhamatyarov.laret.completion.generators.AnthropicSchemaExporter
import com.rkhamatyarov.laret.completion.generators.JsonSchemaParameters
import com.rkhamatyarov.laret.completion.generators.OpenAiSchemaExporter
import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.Argument
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup
import com.rkhamatyarov.laret.model.Option
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("SchemaExporter")
class SchemaExporterTest {
    private val mapper = jacksonObjectMapper()

    private fun parse(json: String): Map<String, Any?> = mapper.readValue(json)

    private fun appWith(vararg commands: Command, groupName: String = "server"): CliApp = CliApp(
        name = "laret",
        groups = listOf(CommandGroup(name = groupName, commands = commands.toList())),
    )

    private fun openAiParameters(model: Map<String, Any?>): Map<*, *> {
        val entry = (model["functions"] as List<*>).first() as Map<*, *>
        val function = entry["function"] as Map<*, *>
        return function["parameters"] as Map<*, *>
    }

    @Test
    fun `wraps each command as a function entry`() {
        val app = appWith(Command(name = "start", description = "Start server"))

        val model = OpenAiSchemaExporter().exportAsModel(app)

        val functions = model["functions"] as List<*>
        assertEquals(1, functions.size)
        val entry = functions.first() as Map<*, *>
        assertEquals("function", entry["type"])
        val function = entry["function"] as Map<*, *>
        assertEquals("laret_server_start", function["name"])
        assertEquals("Start server", function["description"])
        assertTrue(function.containsKey("parameters"))
    }

    @Test
    fun `infers string type and default for value options`() {
        val port = Option(short = "p", long = "port", description = "Bind port", default = "8080")
        val app = appWith(Command(name = "start", options = listOf(port)))

        val properties = openAiParameters(OpenAiSchemaExporter().exportAsModel(app))["properties"] as Map<*, *>

        val portProperty = properties["port"] as Map<*, *>
        assertEquals("string", portProperty["type"])
        assertEquals("8080", portProperty["default"])
    }

    @Test
    fun `infers boolean type for value-less flags`() {
        val verbose = Option(short = "v", long = "verbose", takesValue = false)
        val app = appWith(Command(name = "start", options = listOf(verbose)))

        val properties = openAiParameters(OpenAiSchemaExporter().exportAsModel(app))["properties"] as Map<*, *>

        val verboseProperty = properties["verbose"] as Map<*, *>
        assertEquals("boolean", verboseProperty["type"])
        assertFalse(verboseProperty.containsKey("default"))
    }

    @Test
    fun `uses input_schema under a tools array`() {
        val app = appWith(Command(name = "start", description = "Start server"))

        val json = AnthropicSchemaExporter().export(app)

        assertTrue(json.contains("\"input_schema\""))
        assertFalse(json.contains("\"parameters\""))
        val tools = parse(json)["tools"] as List<*>
        val tool = tools.first() as Map<*, *>
        assertEquals("laret_server_start", tool["name"])
        assertTrue((tool["input_schema"] as Map<*, *>).containsKey("type"))
    }

    @Test
    fun `required non-optional arguments are listed as required`() {
        val source = Argument(name = "source", required = true, optional = false)
        val command = Command(name = "copy", arguments = listOf(source))

        val parameters = JsonSchemaParameters.build(command)

        assertEquals(listOf("source"), parameters["required"])
    }

    @Test
    fun `options are never required`() {
        val force = Option(short = "f", long = "force", takesValue = false)
        val command = Command(name = "delete", options = listOf(force))

        val parameters = JsonSchemaParameters.build(command)

        assertEquals(emptyList<String>(), parameters["required"])
    }

    @Test
    fun `blank command descriptions fall back to generated text`() {
        val app = appWith(Command(name = "status"), groupName = "ops")

        val model = OpenAiSchemaExporter().exportAsModel(app)

        val entry = (model["functions"] as List<*>).first() as Map<*, *>
        val function = entry["function"] as Map<*, *>
        assertEquals("Execute the status command.", function["description"])
    }

    @Test
    fun `traverses every group and command`() {
        val app = CliApp(
            name = "laret",
            groups = listOf(
                CommandGroup("server", commands = listOf(Command("start"), Command("stop"))),
                CommandGroup("config", commands = listOf(Command("show"))),
            ),
        )

        val functions = OpenAiSchemaExporter().exportAsModel(app)["functions"] as List<*>

        assertEquals(3, functions.size)
    }
}
