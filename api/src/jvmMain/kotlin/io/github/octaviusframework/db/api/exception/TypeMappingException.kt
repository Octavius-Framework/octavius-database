package io.github.octaviusframework.db.api.exception

/**
 * Messages for [TypeMappingException] occurring during conversion between Kotlin and PostgreSQL types.
 */
enum class TypeMappingExceptionMessage {
    /** General failure when converting a database value to a Kotlin property. */
    VALUE_CONVERSION_FAILED,

    /** Database enum string does not match any entry in the Kotlin enum class. */
    ENUM_CONVERSION_FAILED,

    /** 
     * Attempted to use a native array (Array<T>) for complex types. 
     * Framework requires List<T> for structured types.
     */
    UNSUPPORTED_COMPONENT_TYPE_IN_ARRAY,

    /** The internal format of a dynamic_dto column is invalid or corrupted. */
    INVALID_DYNAMIC_DTO_FORMAT,

    /** Collection element type does not match the expected generic type. */
    INCOMPATIBLE_COLLECTION_ELEMENT_TYPE,

    /** General type mismatch during mapping. */
    INCOMPATIBLE_TYPE,

    // Mapping errors
    /** A required (non-null) property in a data class was missing from the query result. */
    MISSING_REQUIRED_PROPERTY,

    /** Critical failure during instantiation of a data class or object mapping. */
    OBJECT_MAPPING_FAILED,


    /** JSON deserialization error in dynamic_dto */
    JSON_DESERIALIZATION_FAILED,

    /** Object to JSON serialization error for dynamic_dto */
    JSON_SERIALIZATION_FAILED,

    /** A non-nullable Kotlin field received a NULL value from the database. */
    UNEXPECTED_NULL_VALUE,

    /** Expected a single row but the query returned multiple rows. */
    TOO_MANY_ROWS,

    /** User-defined PgCompositeMapper implementation threw an exception. */
    COMPOSITE_MAPPER_FAILED
}

/**
 * Exception thrown when data cannot be mapped between the database and Kotlin objects.
 *
 * This covers issues with `kotlinx.serialization`, reflection-based mapping to data classes,
 * and custom type converters.
 *
 * As a [FatalDatabaseException], it usually indicates a mismatch between the SQL query result
 * and the target Kotlin model (e.g., missing column, incompatible type, or unexpected null).
 */
class TypeMappingException(
    val messageEnum: TypeMappingExceptionMessage,
    val value: Any? = null,
    val targetType: String? = null,
    val rowData: Map<String, Any?>? = null,
    val propertyName: String? = null,
    cause: Throwable? = null
) : FatalDatabaseException(messageEnum.name, null, cause) {
    override fun getDetailedMessage(): String {
        return """
message: ${generateDeveloperMessage(this.messageEnum, value, targetType, propertyName)}
value: $value
targetType: $targetType
rowData: $rowData
propertyName: $propertyName
"""
    }
}

private fun generateDeveloperMessage(
    messageEnum: TypeMappingExceptionMessage,
    value: Any?,
    targetType: String?,
    propertyName: String?
): String {
    return when (messageEnum) {
        TypeMappingExceptionMessage.VALUE_CONVERSION_FAILED -> "Cannot convert value '$value' to type '$targetType'."
        TypeMappingExceptionMessage.ENUM_CONVERSION_FAILED -> "Cannot convert enum value '$value' to type '$targetType'."
        TypeMappingExceptionMessage.INVALID_DYNAMIC_DTO_FORMAT -> "Invalid dynamic_dto format: '$value'."
        TypeMappingExceptionMessage.INCOMPATIBLE_COLLECTION_ELEMENT_TYPE ->
            "An element within a collection has an incorrect type. Expected elements compatible with '$targetType', but found an element of type '${value?.let { it::class.simpleName }}'."

        TypeMappingExceptionMessage.INCOMPATIBLE_TYPE -> "Element has an incompatible type. Expected elements compatible with '$targetType', but found an element of type '${value?.let { it::class.simpleName }}'."
        TypeMappingExceptionMessage.OBJECT_MAPPING_FAILED -> "Failed to map data to object of class '$targetType'."
        TypeMappingExceptionMessage.MISSING_REQUIRED_PROPERTY -> "Missing required field '$propertyName' (key: '$value') when mapping to class '$targetType'."
        TypeMappingExceptionMessage.JSON_DESERIALIZATION_FAILED -> "Failed to deserialize JSON for dynamic type '$targetType'."
        TypeMappingExceptionMessage.UNSUPPORTED_COMPONENT_TYPE_IN_ARRAY ->
            "Native JDBC arrays (Array<*>) do not support complex types (e.g., data class, List, Map). " +
                    "Detected type: '${targetType}'. Use List<DataClass> so the library can generate ARRAY[ROW(...)] syntax."

        TypeMappingExceptionMessage.JSON_SERIALIZATION_FAILED -> "Failed to serialize object of class '$targetType' to JSON format. " +
                "Ensure that the class and all its nested types have the @Serializable annotation."

        TypeMappingExceptionMessage.UNEXPECTED_NULL_VALUE ->
            "Query returned null but target type '$targetType' is non-nullable. Use a nullable type (e.g. toField<Int?>()) if null values are expected."

        TypeMappingExceptionMessage.TOO_MANY_ROWS ->
            "Query returned multiple rows but only a single row was expected (target type: '$targetType'). Use toList() or toColumn() for multi-row results, or add LIMIT 1 to your query."

        TypeMappingExceptionMessage.COMPOSITE_MAPPER_FAILED ->
            "Custom PgCompositeMapper failed for type '$targetType'. Check the 'cause' for implementation-specific error."
    }
}
