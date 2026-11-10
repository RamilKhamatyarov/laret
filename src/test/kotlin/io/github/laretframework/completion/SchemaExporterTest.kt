package io.github.laretframework.completion

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.laretframework.completion.generators.AnthropicSchemaExporter
import io.github.laretframework.completion.generators.JsonSchemaParameters
import io.github.laretframework.completion.generators.LaretContractExporter
import io.github.laretframework.completion.generators.OpenAiSchemaExporter
import io.github.laretframework.core.CliApp
import io.github.laretframework.dsl.CommandBuilder
import io.github.laretframework.model.Argument
import io.github.laretframework.model.Command
import io.github.laretframework.model.CommandGroup
import io.github.laretframework.model.CommandValueType
import io.github.laretframework.model.ImpactCategory
import io.github.laretframework.model.ImpactProfile
import io.github.laretframework.model.Option
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `laret contract exports declared types and impact profile`() {
        val command = Command(
            name = "create",
            arguments = listOf(Argument("count", valueType = CommandValueType.INTEGER)),
            impactProfile = ImpactProfile(ImpactCategory.LOCAL_MUTATION),
        )
        val model = LaretContractExporter().exportAsModel(appWith(command))
        val entry = (model["commands"] as List<*>).single() as Map<*, *>
        val properties = (entry["inputSchema"] as Map<*, *>)["properties"] as Map<*, *>
        val count = properties["count"] as Map<*, *>
        val profile = entry["impactProfile"] as Map<*, *>

        assertEquals("integer", count["type"])
        assertEquals("local_mutation", profile["category"])
        assertEquals(emptyList<String>(), profile["capabilityNeeds"])
    }

    @Test
    fun `laret contract keeps undeclared category and excludes hidden commands`() {
        val app = appWith(Command("visible"), Command("hidden", hidden = true))

        val commands = LaretContractExporter().exportAsModel(app)["commands"] as List<*>
        val profile = ((commands.single() as Map<*, *>)["impactProfile"] as Map<*, *>)

        assertEquals("undeclared", profile["category"])
        assertEquals(emptyList<String>(), profile["capabilityNeeds"])
    }

    @Test
    fun `schema export command routes the laret format`() {
        val app = appWith(Command("status"))

        val contract = parse(SchemaExportCommand(app).export(SchemaFormat.LARET))

        assertEquals("1", contract["contractVersion"])
        val command = (contract["commands"] as List<*>).single() as Map<*, *>
        assertEquals("laret.server.status", command["name"])
    }

    @Test
    fun `provider schemas remain string typed for value options`() {
        val app = appWith(
            Command(
                "start",
                options = listOf(Option("p", "port", default = "8080", valueType = CommandValueType.INTEGER)),
            ),
        )

        val properties = openAiParameters(OpenAiSchemaExporter().exportAsModel(app))["properties"] as Map<*, *>

        assertEquals("string", (properties["port"] as Map<*, *>)["type"])
    }

    @Test
    fun `builder declares input types and command impact`() {
        val builder = CommandBuilder("resize")
        builder.argument("width", CommandValueType.INTEGER)
        builder.option("f", "factor", CommandValueType.NUMBER)
        builder.impact(ImpactCategory.LOCAL_MUTATION)

        val command = builder.build()

        assertEquals(CommandValueType.INTEGER, command.arguments.single().valueType)
        assertEquals(CommandValueType.NUMBER, command.options.single().valueType)
        assertEquals(ImpactCategory.LOCAL_MUTATION, command.impactProfile.category)
    }

    @Test
    fun `contract preserves no effect and external interaction categories`() {
        val app = appWith(
            Command("pure", impactProfile = ImpactProfile(ImpactCategory.NO_EFFECT)),
            Command("remote", impactProfile = ImpactProfile(ImpactCategory.EXTERNAL_INTERACTION)),
        )

        val commands = LaretContractExporter().exportAsModel(app)["commands"] as List<*>
        val categories = commands.map { (it as Map<*, *>)["impactProfile"] as Map<*, *> }
            .map { it["category"] }

        assertEquals(listOf("no_effect", "external_interaction"), categories)
    }
}
