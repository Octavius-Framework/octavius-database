package io.github.octaviusframework.db.api.exception

enum class TypeRegistryExceptionMessage {
    WRONG_FIELD_NUMBER_IN_COMPOSITE, // Registry <-> database mismatch
    PG_TYPE_NOT_FOUND,               // Registry lookup failed (e.g. converting DB value -> Kotlin)
    KOTLIN_CLASS_NOT_MAPPED,         // Registry lookup failed (e.g. Kotlin param -> SQL)
    DYNAMIC_TYPE_NOT_FOUND,           // Dynamic DTO key lookup failed
    // --- Schema Consistency errors ---
    TYPE_DEFINITION_MISSING_IN_DB,     // Code has @PgType, Database is missing CREATE TYPE
    DUPLICATE_PG_TYPE_DEFINITION,      // Conflict between @PgEnum and/or @PgComposite names
    DUPLICATE_DYNAMIC_TYPE_DEFINITION, // Conflict between @DynamicallyMappable names
}

/**
 * Exception thrown when there is a mismatch or missing definition in the type registry.
 *
 * The type registry maintains mappings for PostgreSQL enums and composite types.
 * This exception can occur during startup (schema validation) or at runtime
 * (when a type is used but not registered).
 */
class TypeRegistryException(
    val messageEnum: TypeRegistryExceptionMessage,
    val typeName: String,
    val oid: Int? = null,
    val expectedCategory: String? = null,
    val details: String? = null,
    cause: Throwable? = null,
    queryContext: QueryContext? = null
) : FatalDatabaseException(
    queryContext = queryContext,
    message = messageEnum.name,
    cause = cause
) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("message: ${generateDeveloperMessage(messageEnum)}")
        appendLine("Related Type: $typeName")
        if (oid != null) appendLine("OID: $oid")
        if (expectedCategory != null) appendLine("Expected Category: $expectedCategory")
        if (details != null) appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(messageEnum: TypeRegistryExceptionMessage): String =
    when (messageEnum) {
        TypeRegistryExceptionMessage.WRONG_FIELD_NUMBER_IN_COMPOSITE ->
            "Schema mismatch. Composite type in the database has a different number of fields than defined in the registry."

        TypeRegistryExceptionMessage.PG_TYPE_NOT_FOUND ->
            "Runtime lookup failed. PostgreSQL type was not found in the loaded registry."

        TypeRegistryExceptionMessage.KOTLIN_CLASS_NOT_MAPPED ->
            "Runtime lookup failed. Class is not mapped to any PostgreSQL type. Ensure it has @PgEnum/@PgComposite annotation and is scanned."

        TypeRegistryExceptionMessage.DYNAMIC_TYPE_NOT_FOUND ->
            "Runtime lookup failed. No registered @DynamicallyMappable class found for key ."

        TypeRegistryExceptionMessage.TYPE_DEFINITION_MISSING_IN_DB ->
            "Startup validation failed. Please check your SQL migrations and ensure the PostgreSQL type exists in one of the scanned schemas."
        TypeRegistryExceptionMessage.DUPLICATE_PG_TYPE_DEFINITION ->
            "Configuration error. The PostgreSQL type is defined more than once in the codebase (detected duplicate or collision between @PgEnum and @PgComposite). Postgres requires unique type names within a schema."
        TypeRegistryExceptionMessage.DUPLICATE_DYNAMIC_TYPE_DEFINITION ->
            "Configuration error. The Dynamic DTO key is defined more than once. Check your @DynamicallyMappable(typeName=...) annotations."
    }
