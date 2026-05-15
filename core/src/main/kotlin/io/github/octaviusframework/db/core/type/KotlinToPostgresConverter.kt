package io.github.octaviusframework.db.core.type

import io.github.octaviusframework.db.api.exception.*
import io.github.octaviusframework.db.api.type.DynamicDto
import io.github.octaviusframework.db.api.type.PgTyped
import io.github.octaviusframework.db.api.type.QualifiedName
import io.github.octaviusframework.db.api.type.TypeHandler
import io.github.octaviusframework.db.core.config.DynamicDtoSerializationStrategy
import io.github.octaviusframework.db.core.type.registry.TypeRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import org.postgresql.util.PGobject
import kotlin.reflect.KClass

/**
 * Result of parameter expansion: SQL with positional markers and the converted values.
 */
data class PositionalQuery(val sql: String, val params: List<Any?>)

/**
 * Result of a single parameter conversion.
 */
private data class ParameterConversion(val placeholder: String, val value: Any?)

/**
 * Orchestrates conversion of Kotlin objects to PostgreSQL JDBC parameters.
 * Delegates complex serialization to [PgTextSerializer].
 */
internal class KotlinToPostgresConverter(
    private val typeRegistry: TypeRegistry,
    private val dynamicDtoStrategy: DynamicDtoSerializationStrategy = DynamicDtoSerializationStrategy.AUTOMATIC_WHEN_UNAMBIGUOUS
) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val serializer = PgTextSerializer(typeRegistry, dynamicDtoStrategy)

    /**
     * Entry point for query transformation. Parses named parameters and converts values.
     */
    fun toPositionalQuery(
        sql: String,
        params: Map<String, Any?>,
        options: InternalQueryOptions
    ): PositionalQuery {
        logger.trace { "Converting named parameters to positional for query" }
        val parsedParameters = PostgresSqlPreprocessor.parse(sql)
        if (parsedParameters.isEmpty()) {
            return PositionalQuery(PostgresSqlPreprocessor.escapeQuestionMarks(sql), emptyList())
        }

        val finalParams = ArrayList<Any?>(parsedParameters.size)
        val transformedSql = buildString(sql.length + 256) {
            var lastIndex = 0
            for (parsedParam in parsedParameters) {
                val paramName = parsedParam.name
                requireStatement(
                    params.containsKey(paramName),
                    BadStatementExceptionMessage.MISSING_PARAMETERS
                ) { "Missing value for parameter: $paramName" }
                val paramValue = params[paramName]

                logger.trace { "Processing parameter: @$paramName" }
                val conversion = convertParameter(paramValue, appendTypeCast = true, options = options)

                // Escape question marks in the literal SQL parts between parameters
                val partBefore = sql.substring(lastIndex, parsedParam.startIndex)
                append(PostgresSqlPreprocessor.escapeQuestionMarks(partBefore))

                append(conversion.placeholder)
                finalParams.add(conversion.value)
                lastIndex = parsedParam.endIndex
            }
            // Escape question marks in the remaining literal SQL part
            val partAfter = sql.substring(lastIndex, sql.length)
            append(PostgresSqlPreprocessor.escapeQuestionMarks(partAfter))
        }

        return PositionalQuery(transformedSql, finalParams)
    }

    // --- INTERNAL CONVERSION LOGIC ---

    private fun convertParameter(
        value: Any?,
        appendTypeCast: Boolean,
        skipDynamicDto: Boolean = false,
        options: InternalQueryOptions
    ): ParameterConversion {
        if (value == null) {
            logger.trace { "Converting null parameter" }
            return ParameterConversion("?", null)
        }

        val (unwrappedValue, pgType, updatedSkipDynamicDto) = unpackPgTyped(value, skipDynamicDto)
        if (unwrappedValue == null) {
            logger.trace { "Converting null parameter (unwrapped from PgTyped with type $pgType)" }
            val castSuffix = if (appendTypeCast && pgType != null) "::${pgType.quote()}" else ""
            return ParameterConversion("?$castSuffix", null)
        }

        logger.trace { "Converting parameter of type ${unwrappedValue::class.qualifiedName ?: unwrappedValue::class.simpleName} (explicit type: $pgType)" }

        // 1. Try Dynamic DTO conversion
        if (!updatedSkipDynamicDto) {
            tryConvertAsDynamicDto(unwrappedValue, appendTypeCast, options)?.let {
                logger.trace { "Converted parameter as Dynamic DTO" }
                return it
            }
        }

        // 2. Delegate to handlers (Priority: QueryOptions -> Registry)
        val customHandler = options.customHandlersByClass[unwrappedValue::class]
        val registryHandler = typeRegistry.getHandlerByClass(unwrappedValue::class)

        val handler = customHandler ?: registryHandler

        if (handler != null) {
            val finalType = pgType ?: QualifiedName(handler.pgSchema, handler.pgTypeName)
            val castSuffix = if (appendTypeCast) "::${finalType.quote()}" else ""
            logger.trace { "Using handler [${handler.pgTypeName}] for parameter conversion (from ${if (customHandler != null) "QueryOptions" else "Registry"})" }
            @Suppress("UNCHECKED_CAST")
            val typedHandler = handler as TypeHandler<Any>

            val jdbcValue = typedHandler.toJdbc?.invoke(unwrappedValue)
                ?: pgObject("text", typedHandler.toPgString(unwrappedValue))

            return ParameterConversion("?$castSuffix", jdbcValue)
        }

        // 3. Handle specialized types
        val resolvedType: QualifiedName =
            pgType ?: if (appendTypeCast) resolveSqlType(unwrappedValue) else QualifiedName("pg_catalog", "text")
        val jdbcValue = when (unwrappedValue) {
            is Array<*> -> {
                logger.trace { "Handling parameter as Array" }
                return handleArray(unwrappedValue)
            }

            is List<*> -> {
                logger.trace { "Handling parameter as List" }
                handleList(unwrappedValue, updatedSkipDynamicDto)
            }

            is Enum<*> -> {
                logger.trace { "Handling parameter as Enum" }
                handleEnum(unwrappedValue)
            }

            else -> {
                when {
                    unwrappedValue::class.isData -> {
                        logger.trace { "Handling parameter as Composite (Data Class)" }
                        pgObject("text", serializer.serializeComposite(unwrappedValue, updatedSkipDynamicDto))
                    }

                    unwrappedValue::class.isValue -> throw TypeRegistryException(
                        TypeRegistryExceptionMessage.KOTLIN_CLASS_NOT_MAPPED,
                        unwrappedValue::class.qualifiedName ?: unwrappedValue::class.simpleName ?: "unknown",
                        expectedCategory = "DYNAMIC"
                    )

                    else -> {
                        logger.trace { "Falling back to default conversion for type ${unwrappedValue::class.simpleName}" }
                        unwrappedValue
                    }
                }
            }
        }

        return ParameterConversion(if (appendTypeCast) "?::${resolvedType.quote()}" else "?", jdbcValue)
    }

    private fun unpackPgTyped(value: Any, initialSkip: Boolean): Triple<Any?, QualifiedName?, Boolean> {
        var current = value
        var pgType: QualifiedName? = null
        var skipDynamicDto = initialSkip

        while (current is PgTyped) {
            if (pgType == null) pgType = current.pgType
            val nextValue = current.value ?: return Triple(null, pgType, true)
            current = nextValue
            skipDynamicDto = true
        }
        return Triple(current, pgType, skipDynamicDto)
    }

    private fun tryConvertAsDynamicDto(
        value: Any,
        appendTypeCast: Boolean,
        options: InternalQueryOptions
    ): ParameterConversion? {
        if (dynamicDtoStrategy == DynamicDtoSerializationStrategy.EXPLICIT_ONLY && value !is DynamicDto) return null
        if (dynamicDtoStrategy == DynamicDtoSerializationStrategy.AUTOMATIC_WHEN_UNAMBIGUOUS && typeRegistry.isPgType(
                value::class
            )
        ) return null

        val dynamicTypeName = typeRegistry.getDynamicTypeNameForClass(value::class) ?: return null
        val dtSerializer = typeRegistry.getDynamicSerializer(dynamicTypeName)

        val dynamicDto = DynamicDto.from(value, dynamicTypeName, dtSerializer)
        return convertParameter(dynamicDto, appendTypeCast, options = options)
    }

    private fun handleArray(array: Array<*>): ParameterConversion {
        val componentType = array::class.java.componentType!!.kotlin
        if (componentType.isData || componentType == Map::class || componentType == List::class) {
            throw TypeMappingException(
                TypeMappingExceptionMessage.UNSUPPORTED_COMPONENT_TYPE_IN_ARRAY,
                array,
                componentType.qualifiedName ?: componentType.simpleName ?: "unknown"
            )
        }
        return ParameterConversion("?", array)
    }

    private fun handleList(list: List<*>, skipDynamicDto: Boolean): PGobject {
        return pgObject("text", serializer.serializeList(list, skipDynamicDto))
    }

    private fun handleEnum(enum: Enum<*>): PGobject {
        val typeName = typeRegistry.getPgTypeNameForClass(enum::class)
        val oid = typeRegistry.getOidForName(typeName)
        val typeInfo = typeRegistry.getEnumDefinition(oid)
        // Set type to "text" because we append explicit cast in SQL
        return pgObject("text", typeInfo.enumToValueMap[enum] ?: enum.name)
    }

    private fun pgObject(type: String, value: String?) = PGobject().apply {
        this.type = type
        this.value = value
    }

    private fun resolveSqlType(value: Any): QualifiedName {
        return when (value) {
            is List<*> -> {
                val firstNonNull = value.firstOrNull { it != null }
                if (firstNonNull != null) resolveSqlType(firstNonNull).asArray() else QualifiedName(
                    "",
                    "text",
                    isArray = true
                )
            }

            else -> {
                val kClass = value::class
                when {
                    shouldUseDynamicDto(kClass) -> typeRegistry.getPgTypeNameForClass(DynamicDto::class)
                    typeRegistry.isPgType(kClass) -> typeRegistry.getPgTypeNameForClass(kClass)
                    else -> {
                        val standardName = typeRegistry.getHandlerByClass(kClass)?.pgTypeName ?: "text"
                        QualifiedName("", standardName)
                    }
                }
            }
        }
    }

    private fun shouldUseDynamicDto(kClass: KClass<*>): Boolean {
        if (typeRegistry.getDynamicTypeNameForClass(kClass) == null) return false
        return when (dynamicDtoStrategy) {
            DynamicDtoSerializationStrategy.PREFER_DYNAMIC_DTO -> true
            DynamicDtoSerializationStrategy.AUTOMATIC_WHEN_UNAMBIGUOUS -> !typeRegistry.isPgType(kClass)
            else -> false
        }
    }
}
