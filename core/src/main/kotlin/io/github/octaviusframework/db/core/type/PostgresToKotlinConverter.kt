package io.github.octaviusframework.db.core.type

import io.github.octaviusframework.db.api.mapper.PgCompositeMapper
import io.github.octaviusframework.db.api.exception.TypeMappingException
import io.github.octaviusframework.db.api.exception.TypeMappingExceptionMessage
import io.github.octaviusframework.db.api.exception.TypeRegistryException
import io.github.octaviusframework.db.api.exception.TypeRegistryExceptionMessage
import io.github.octaviusframework.db.api.toDataObject
import io.github.octaviusframework.db.core.type.registry.*
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Converts values from PostgreSQL (as `String`) to appropriate Kotlin types.
 *
 * Supports standard types, enums, composites, and arrays, using metadata
 * from `TypeRegistry` for dynamic mapping.
 *
 * @param typeRegistry Registry containing metadata about PostgreSQL types.
 */
internal class PostgresToKotlinConverter(
    private val typeRegistry: TypeRegistry
) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    /**
     * Main conversion function that delegates to specialized handlers.
     *
     * Supports all type categories: STANDARD, ENUM, ARRAY, COMPOSITE, and DYNAMIC.
     *
     * @param value Value from database as `String` (can be `null`).
     * @param oid PostgreSQL type OID.
     * @param options Query-specific options.
     * @return Converted value or `null` if `value` was `null`.
     * @throws TypeRegistryException if type is unknown.
     * @throws TypeMappingException if conversion fails.
     */
    fun convert(value: String?, oid: Int, options: InternalQueryOptions): Any? {
        if (value == null) {
            logger.trace { "Converting null value for OID: $oid" }
            return null
        }

        logger.trace { "Converting value '$value' from PostgreSQL OID: $oid" }
        val category = typeRegistry.getCategory(oid)

        return when (category) {
            TypeCategory.STANDARD -> {
                logger.trace { "Converting standard value '$value' for OID $oid" }
                convertStandardType(value, oid, options)
            }

            TypeCategory.ENUM -> {
                logger.trace { "Converting enum value '$value' for OID $oid" }
                val def = typeRegistry.getEnumDefinition(oid)
                convertEnum(value, def)
            }

            TypeCategory.ARRAY -> {
                logger.trace { "Converting array value for OID $oid" }
                val def = typeRegistry.getArrayDefinition(oid)
                convertArray(value, def, options)
            }

            TypeCategory.COMPOSITE -> {
                logger.trace { "Converting composite value for OID $oid" }
                val def = typeRegistry.getCompositeDefinition(oid)
                convertCompositeType(value, def, options)
            }

            TypeCategory.DYNAMIC -> {
                logger.trace { "Converting dynamic DTO value for OID $oid" }
                convertDynamicType(value, options)
            }
        }
    }

    /**
     * Converts standard PostgreSQL types to appropriate Kotlin types.
     *
     * Delegates to `TypeRegistry`, which is now the single source of truth.
     *
     * @param value Value from database as String.
     * @param oid OID of standard PostgreSQL type.
     * @param options Query-specific options.
     * @return Converted value.
     * @throws TypeMappingException if conversion fails.
     */
    private fun convertStandardType(value: String, oid: Int, options: InternalQueryOptions): Any { // null handled in convert method

        // 1. Specific match: Check if any handler in QueryOptions matches this OID
        val customHandler = options.customHandlersByOid[oid]

        val handlerToUse = customHandler ?: typeRegistry.getHandlerByOid(oid)

        if (handlerToUse == null) {
            logger.warn { "No type handler found for PostgreSQL OID '$oid'. Returning raw string value." }
            return value // Default behavior: return string if type is unknown
        }

        // 3. Use the 'fromString' function from the handler for conversion
        return try {
            handlerToUse.fromPgString(value)
        } catch (e: Exception) {
            throw TypeMappingException(
                messageEnum = TypeMappingExceptionMessage.VALUE_CONVERSION_FAILED,
                value = value,
                targetType = handlerToUse.kotlinClass.simpleName ?: oid.toString(),
                cause = e
            )
        }
    }

    /**
     * Converts enum value from PostgreSQL to Kotlin enum.
     *
     * Maps value names according to conventions specified in TypeRegistry:
     *
     * @param value Enum value from database.
     * @param typeInfo Enum type information from TypeRegistry.
     * @return Kotlin enum instance.
     * @throws TypeMappingException if conversion fails.
     */
    private fun convertEnum(value: String, typeInfo: PgEnumDefinition): Any { // null handled in convert method

        return typeInfo.valueToEnumMap[value]
            //Should this be RegistryException?
            ?: throw TypeMappingException(
                messageEnum = TypeMappingExceptionMessage.ENUM_CONVERSION_FAILED,
                value = value,
                targetType = typeInfo.typeName.toString()
            )
    }

    /**
     * Converts PostgreSQL array to `List<Any?>`.
     *
     * Supports nested arrays and recursively processes elements
     * according to element type specified in TypeRegistry.
     *
     * @param value String representing PostgreSQL array (format: {elem1,elem2,...}).
     * @param typeInfo Array type information from TypeRegistry.
     * @param options Query-specific options.
     * @return List of converted elements.
     * @throws TypeMappingException if parsing fails.
     */
    private fun convertArray(value: String, typeInfo: PgArrayDefinition, options: InternalQueryOptions): List<Any?> {

        logger.trace { "Parsing PostgreSQL array ${typeInfo.typeName} with element OID: ${typeInfo.elementOid}" }

        val results = mutableListOf<Any?>()

        parseNestedStructure(value, mapNullLiteralToNull = true) { elementValue, isQuoted ->
            // Check if the string representing the element ITSELF is an array.
            val isNestedArray = !isQuoted && elementValue?.startsWith('{') == true
            // If it's a nested array, recursively invoke conversion
            // for the ENTIRE array type (OID), not its element.
            // Otherwise, continue with standard elementType logic.
            val oidToUse = if (isNestedArray) typeInfo.oid else typeInfo.elementOid
            // Recursively convert each array element using the main conversion function
            results.add(convert(elementValue, oidToUse, options))
        }
        logger.trace { "Parsed ${results.size} array elements" }
        return results
    }

    private fun convertCompositeType(value: String, typeInfo: PgCompositeDefinition, options: InternalQueryOptions): Any {
        logger.trace { "Converting composite type ${typeInfo.typeName} (OID: ${typeInfo.oid}) to class: ${typeInfo.kClass.qualifiedName}" }

        val dbAttributes = typeInfo.dbAttributes
        val constructorArgsMap = mutableMapOf<String, Any?>()
        var index = 0

        parseNestedStructure(value, mapNullLiteralToNull = false) { elementValue, _ ->
            val (dbAttributeName, dbAttributeOid) = dbAttributes[index]
            constructorArgsMap[dbAttributeName] = convert(elementValue, dbAttributeOid, options)
            index++
        }

        if (index != dbAttributes.size) {
            throw TypeRegistryException(
                TypeRegistryExceptionMessage.WRONG_FIELD_NUMBER_IN_COMPOSITE,
                typeName = typeInfo.typeName.toString(),
                oid = typeInfo.oid,
                expectedCategory = "COMPOSITE"
            )
        }

        // --- Respect QueryOptions for Composite Mapping ---

        val customMapper = options.options.customCompositeMappers[typeInfo.typeName]

        val result = when {
            // 1. Force return as Map if requested or class is not mapped
            typeInfo.kClass == Map::class || options.options.returnAllCompositesAsMaps || typeInfo.typeName in options.options.compositeAsMapTypes -> {
                logger.trace { "Returning composite type ${typeInfo.typeName} as Map" }
                constructorArgsMap
            }
            customMapper != null -> {
                // 2. Use custom mapper from QueryOptions if provided
                logger.trace { "Using query-specific custom mapper for ${typeInfo.typeName}" }
                try {
                    @Suppress("UNCHECKED_CAST")
                    (customMapper as PgCompositeMapper<Any>).toDataObject(constructorArgsMap)
                } catch (e: Exception) {
                    throw TypeMappingException(
                        TypeMappingExceptionMessage.COMPOSITE_MAPPER_FAILED,
                        targetType = typeInfo.typeName.toString(),
                        rowData = constructorArgsMap,
                        cause = e
                    )
                }
            }
            // 3. Mapper from annotation
            typeInfo.mapper != null -> {
                logger.trace { "Using default mapper for ${typeInfo.typeName}" }
                try {
                    typeInfo.mapper.toDataObject(constructorArgsMap)
                } catch (e: Exception) {
                    throw TypeMappingException(
                        TypeMappingExceptionMessage.COMPOSITE_MAPPER_FAILED,
                        targetType = typeInfo.typeName.toString(),
                        rowData = constructorArgsMap,
                        cause = e
                    )
                }
            }
            // 4. Reflection fallback
            else -> {
                constructorArgsMap.toDataObject(typeInfo.kClass)
            }
        }
        logger.trace { "Successfully created instance of ${typeInfo.kClass.simpleName}" }
        return result
    }

    /**
     * Deserializes the special `dynamic_dto` type to an appropriate Kotlin class.
     *
     * @param value Raw value from database in composite format `("typeName", "jsonData")`.
     * @return Instance of appropriate `data class` with `@DynamicallyMappable` annotation.
     */
    private fun convertDynamicType(value: String, options: InternalQueryOptions): Any {
        // null handled in convert method
        // json itself cannot be null in composite - this is also consistent with the write where value cannot be null
        var typeName: String? = null
        var jsonDataString: String? = null
        var count = 0

        parseNestedStructure(value, mapNullLiteralToNull = false) { elementValue, _ ->
            if (count == 0) typeName = elementValue
            else if (count == 1) jsonDataString = elementValue
            count++
        }

        if (count != 2) throw TypeRegistryException(
            TypeRegistryExceptionMessage.WRONG_FIELD_NUMBER_IN_COMPOSITE,
            typeName = "public.dynamic_dto",
            expectedCategory = "DYNAMIC"
        )
        if (typeName == null || jsonDataString == null) throw TypeMappingException(
            TypeMappingExceptionMessage.INVALID_DYNAMIC_DTO_FORMAT,
            value = value
        )


        // Use TypeRegistry to safely find the serializer
        val serializer = typeRegistry.getDynamicSerializer(typeName)

        return try {
            options.json.decodeFromString(serializer, jsonDataString)
        } catch (e: Exception) {
            throw TypeMappingException(
                TypeMappingExceptionMessage.JSON_DESERIALIZATION_FAILED,
                targetType = typeName,
                rowData = mapOf("json" to jsonDataString),
                cause = e
            )
        }
    }

    /**
     * Universal inline parser for nested structures (arrays and composites).
     * Handles quotes, escaping, `NULL` values, and nesting.
     * Yields elements directly via a lambda.
     */
    private inline fun parseNestedStructure(
        input: String,
        mapNullLiteralToNull: Boolean,
        onElement: (value: String?, isQuoted: Boolean) -> Unit
    ) {
        // Minimum length is 2 for empty array "{}" or composite "()"
        if (input.length <= 2) return

        val endIndex = input.length - 1
        var i = 1 // Start reading inside the bounds
        var inQuotes = false
        var nestingLevel = 0
        var currentElementStart = 1

        while (i < endIndex) {
            val char = input[i]

            if (inQuotes) {
                if (char == '\\') {
                    i++ // Skip escaped character
                } else if (char == '"') {
                    // When it's a quote escaping another (i.e., "")
                    // on the next loop iteration the parser will simply change state back
                    // Additional handling is pointless since splitting is done by comma
                    inQuotes = false
                }
            } else {
                when (char) {
                    '"' -> inQuotes = true
                    '{', '(' -> nestingLevel++
                    '}', ')' -> nestingLevel--
                    ',' -> {
                        if (nestingLevel == 0) {
                            onElement(unescapeValue(input, currentElementStart, i, mapNullLiteralToNull),
                                currentElementStart < i && input[currentElementStart] == '"')
                            currentElementStart = i + 1
                        }
                    }
                }
            }
            i++
        }

        // Emit the last element
        if (currentElementStart <= endIndex) {
            val isQuoted = currentElementStart < endIndex && input[currentElementStart] == '"'
            onElement(unescapeValue(input, currentElementStart, endIndex, mapNullLiteralToNull), isQuoted)
        }
    }

    private fun unescapeValue(source: String, start: Int, end: Int, mapNullLiteralToNull: Boolean): String? {
        // NULL in COMPOSITE
        if (start >= end) return null

        return if (source[start] == '"') {
            // Quoted value - escape quotes inside (") and backslash (\)
            buildString(end - start) {
                var i = start + 1
                while (i < end - 1) {
                    val char = source[i]
                    if (char == '"' || char == '\\') {
                        i++
                        append(source[i])
                    } else {
                        append(char)
                    }
                    i++
                }
            }
        } else {
            // Field without quotes
            val len = end - start
            // NULL in ARRAY
            if (mapNullLiteralToNull && len == 4 &&
                    source[start] == 'N' && source[start+1] == 'U' &&
                    source[start+2] == 'L' && source[start+3] == 'L'
            ) {
                null
            } else {
                source.substring(start, end)
            }
        }
    }
}
