package io.github.laretframework.model

import java.util.Collections

/** Scalar input types exposed by neutral contracts and MCP tool schemas. */
enum class CommandValueType(val jsonSchemaType: String) {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    BOOLEAN("boolean"),
}

/** Coarse, declarative impact category for a command. */
enum class ImpactCategory(val wireValue: String) {
    UNDECLARED("undeclared"),
    NO_EFFECT("no_effect"),
    READ_ONLY("read_only"),
    LOCAL_MUTATION("local_mutation"),
    EXTERNAL_INTERACTION("external_interaction"),
}

/** Declarative command impact; capability needs are reserved and inert in v1. */
data class ImpactProfile(val category: ImpactCategory = ImpactCategory.UNDECLARED) {
    val capabilityNeeds: List<String>
        get() = Collections.emptyList()
}
