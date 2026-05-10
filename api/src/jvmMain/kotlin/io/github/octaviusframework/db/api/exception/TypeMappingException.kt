package io.github.octaviusframework.db.api.exception

enum class TypeMappingExceptionMessage {
    /** General standard type conversion error */
    VALUE_CONVERSION_FAILED,

    /** Database value doesn't match any enum */
    ENUM_CONVERSION_FAILED,

    UNSUPPORTED_COMPONENT_TYPE_IN_ARRAY,

    /** dynamic_dto parsing error */
    INVALID_DYNAMIC_DTO_FORMAT,

    INCOMPATIBLE_COLLECTION_ELEMENT_TYPE,

    INCOMPATIBLE_TYPE,

    // Mapping errors
    /** Missing key for required field in data class */
    MISSING_REQUIRED_PROPERTY,

    /** General error during data class instantiation */
    OBJECT_MAPPING_FAILED,


    /** JSON deserialization error in dynamic_dto */
    JSON_DESERIALIZATION_FAILED,

    /** Object to JSON serialization error for dynamic_dto */
    JSON_SERIALIZATION_FAILED,

    /** When a non-null value was expected but null was received */
    UNEXPECTED_NULL_VALUE,

    /** When a single-row method received more than one row */
    TOO_MANY_ROWS,

    /** Error during manual mapping via PgCompositeMapper */
    COMPOSITE_MAPPER_FAILED
}

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
