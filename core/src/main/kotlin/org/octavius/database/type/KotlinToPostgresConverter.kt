package org.octavius.database.type

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.*
import kotlinx.serialization.json.JsonElement
import org.octavius.data.exception.ConversionException
import org.octavius.data.exception.ConversionExceptionMessage
import org.octavius.data.exception.TypeRegistryException
import org.octavius.data.exception.TypeRegistryExceptionMessage
import org.octavius.data.toMap
import org.octavius.data.type.DISTANT_FUTURE
import org.octavius.data.type.DISTANT_PAST
import org.octavius.data.type.DynamicDto
import org.octavius.data.type.PgTyped
import org.octavius.data.util.clean
import org.octavius.database.config.DynamicDtoSerializationStrategy
import org.octavius.database.type.registry.TypeRegistry
import org.postgresql.util.PGobject
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * Result of query expansion to JDBC positional format.
 * @param sql Query with '?' placeholders instead of named parameters.
 * @param params Ordered list of parameters for use in `PreparedStatement`.
 */
data class PositionalQuery(
    val sql: String,
    val params: List<Any?>
)

/**
 * Converts complex Kotlin types to appropriate SQL constructs for PostgreSQL.
 *
 * Enables using advanced types in queries without manual conversion.
 *
 * **Supported transformations:**
 * - `List<T>` → `ARRAY[...]`
 * - `data class` → `ROW(...)::type_name` (if registered as `@PgComposite`) or `dynamic_dto(...)` (if `@DynamicallyMappable`)
 * - `Enum` → `PGobject` (if registered as `@PgEnum`) or `dynamic_dto(...)` (if `@DynamicallyMappable`)
 * - `value class` → `dynamic_dto(...)` (must have `@DynamicallyMappable`, otherwise exception)
 * - `JsonElement` → `JSONB`
 * - `PgTyped<T>` → wraps value and adds explicit `::type_name` cast (highest priority)
 * - Date/time types → `java.sql.*` equivalents
 * - `Duration` → PostgreSQL `interval`
 *
 * **Dynamic DTO Strategy:**
 * Controls automatic conversion to `dynamic_dto` for `@DynamicallyMappable` types:
 * - `EXPLICIT_ONLY`: Only explicit `DynamicDto` wrappers are serialized as `dynamic_dto`
 * - `AUTOMATIC_WHEN_UNAMBIGUOUS` (default): Automatically converts to `dynamic_dto` if type is NOT registered
 *   as a formal PostgreSQL type (`@PgComposite`/`@PgEnum`). Avoids conflicts between formal and dynamic types.
 *
 */
internal class KotlinToPostgresConverter(
    private val typeRegistry: TypeRegistry,
    private val dynamicDtoStrategy: DynamicDtoSerializationStrategy = DynamicDtoSerializationStrategy.AUTOMATIC_WHEN_UNAMBIGUOUS
) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    /**
     * Maps Kotlin types to their PostgreSQL representations for JDBC.
     *
     * This map handles standard type conversions including special support for PostgreSQL infinity values:
     * - **DATE**: [LocalDate.DISTANT_FUTURE]/[LocalDate.DISTANT_PAST] → 'infinity'/'-infinity'
     * - **TIMESTAMP**: [LocalDateTime.DISTANT_FUTURE]/[LocalDateTime.DISTANT_PAST] → 'infinity'/'-infinity'
     * - **TIMESTAMPTZ**: [Instant.DISTANT_FUTURE]/[Instant.DISTANT_PAST] → 'infinity'/'-infinity'
     * - **INTERVAL**: [Duration.INFINITE]/[-Duration.INFINITE][Duration.INFINITE] → 'infinity'/'-infinity'
     *
     * For finite values, standard conversions apply:
     * - kotlinx.datetime types → java.sql types
     * - Duration → ISO 8601 interval string format
     */
    @OptIn(ExperimentalTime::class)
    private val KOTLIN_TO_JDBC_CONVERTERS: Map<KClass<*>, (Any) -> Any> = mapOf(
        LocalDate::class to { v ->
            when (val date = v as LocalDate) {
                LocalDate.DISTANT_FUTURE -> PGobject().apply { type = "date"; value = "infinity" }
                LocalDate.DISTANT_PAST -> PGobject().apply { type = "date"; value = "-infinity" }
                else -> java.sql.Date.valueOf(date.toJavaLocalDate())
            }
        },
        LocalDateTime::class to { v ->
            when (val dateTime = v as LocalDateTime) {
                LocalDateTime.DISTANT_FUTURE -> PGobject().apply { type = "timestamp"; value = "infinity" }
                LocalDateTime.DISTANT_PAST -> PGobject().apply { type = "timestamp"; value = "-infinity" }
                else -> java.sql.Timestamp.valueOf(dateTime.toJavaLocalDateTime())
            }
        },
        LocalTime::class to { v -> java.sql.Time.valueOf((v as LocalTime).toJavaLocalTime()) },
        Instant::class to { v ->
            when (val instant = v as Instant) {
                Instant.DISTANT_FUTURE -> PGobject().apply { type = "timestamptz"; value = "infinity" }
                Instant.DISTANT_PAST -> PGobject().apply { type = "timestamptz"; value = "-infinity" }
                else -> java.sql.Timestamp.from(instant.toJavaInstant())
            }
        },
        Duration::class to { v ->
            val duration = v as Duration
            PGobject().apply {
                type = "interval"
                value = when (duration) {
                    Duration.INFINITE -> "infinity"
                    -Duration.INFINITE -> "-infinity"
                    else -> duration.toIsoString()
                }
            }
        }
    )


    /**
     * Processes SQL query, expanding complex parameters into PostgreSQL constructs.
     *
     * Handles types as described in class KDoc
     *
     * @param sql Query with named parameters (e.g., `:param`).
     * @param params Parameter map for expansion, may contain complex Kotlin types.
     * @return `PositionalQuery` with processed SQL and flattened parameters.
     */
    fun expandParametersInQuery(sql: String, params: Map<String, Any?>): PositionalQuery {
        logger.debug { "Expanding parameters to positional query. Original params count: ${params.size}" }
        logger.trace { "Original SQL: $sql" }

        val parsedParameters = PostgresNamedParameterParser.parse(sql)

        if (parsedParameters.isEmpty()) {
            logger.debug { "No named parameters found, returning original query." }
            // Return empty parameter list since none were used
            return PositionalQuery(sql, emptyList())
        }

        val expandedSqlBuilder = StringBuilder(sql.length)
        val expandedParamsList = mutableListOf<Any?>()
        var lastIndex = 0

        parsedParameters.forEach { parsedParam ->
            val paramName = parsedParam.name

            if (!params.containsKey(paramName)) {
                throw IllegalArgumentException("Missing value for required SQL parameter: $paramName")
            }

            val paramValue = params[paramName]

            val (newPlaceholder, newParams) = expandParameter(paramValue)

            expandedSqlBuilder.append(sql, lastIndex, parsedParam.startIndex)
            expandedSqlBuilder.append(newPlaceholder)

            expandedParamsList.addAll(newParams)

            lastIndex = parsedParam.endIndex
        }

        expandedSqlBuilder.append(sql, lastIndex, sql.length)

        logger.debug { "Parameter expansion completed. Positional params count: ${expandedParamsList.size}" }
        logger.trace { "Expanded SQL: $expandedSqlBuilder" }

        return PositionalQuery(expandedSqlBuilder.toString(), expandedParamsList)
    }

    /**
     * Expands a single parameter into the appropriate SQL construct.
     * @param paramValue Parameter value to convert.
     * @param appendTypeCast Whether to append type cast (e.g., `::type_name`).
     * @return Pair: SQL placeholder (with `?`) and list of flattened parameters.
     */
    private fun expandParameter(
        paramValue: Any?,
        appendTypeCast: Boolean = true,
        explicitPgType: String? = null,
        allowDynamicDto: Boolean = true
    ): Pair<String, List<Any?>> {
        if (paramValue == null) {
            return "?" to listOf(null)
        }

        if (paramValue is PgTyped) {
            // Rozpakowanie PgTyped: wyłączamy dynamiczne mapowanie, by zachować wymuszony kompozyt
            val (innerPlaceholder, innerParams) = expandParameter(
                paramValue.value,
                appendTypeCast = false,
                explicitPgType = paramValue.pgType,
                allowDynamicDto = false
            )
            val finalPlaceholder = if (appendTypeCast) "$innerPlaceholder::${paramValue.pgType}" else innerPlaceholder
            return finalPlaceholder to innerParams
        }

        // Fast-path dla typów natywnych sterownika (tylko na najwyższym poziomie)
        KOTLIN_TO_JDBC_CONVERTERS[paramValue::class]?.let { converter ->
            return "?" to listOf(converter(paramValue))
        }

        val resultValue: Any = when {
            paramValue is JsonElement -> PGobject().apply {
                type = "jsonb"
                value = paramValue.toString()
            }
            isDataClass(paramValue) -> {
                if (allowDynamicDto) {
                    tryExpandAsDynamicDto(paramValue)?.let { return "?" to listOf(it) }
                }
                createCompositeParameter(paramValue, explicitPgType)
            }
            paramValue is Array<*> -> validateTypedArrayParameter(paramValue).second.first()!!
            paramValue is List<*> -> createArrayParameter(paramValue, explicitPgType)
            paramValue is Enum<*> -> {
                if (allowDynamicDto) {
                    tryExpandAsDynamicDto(paramValue)?.let { return "?" to listOf(it) }
                }
                val dbTypeName = explicitPgType ?: typeRegistry.getPgTypeNameForClass(paramValue::class)
                val typeInfo = typeRegistry.getEnumDefinition(dbTypeName)
                PGobject().apply {
                    type = dbTypeName
                    value = typeInfo.enumToValueMap[paramValue] ?: paramValue.name
                }
            }
            isValueClass(paramValue) -> {
                if (allowDynamicDto) {
                    tryExpandAsDynamicDto(paramValue)?.let { return "?" to listOf(it) }
                }
                throw TypeRegistryException(
                    messageEnum = TypeRegistryExceptionMessage.KOTLIN_CLASS_NOT_MAPPED,
                    typeName = paramValue::class.qualifiedName,
                    cause = IllegalStateException("Value class must be annotated with @DynamicallyMappable.")
                )
            }
            paramValue is String -> paramValue.clean()
            else -> paramValue
        }

        return "?" to listOf(resultValue)
    }

    // =================================================================
    // --- TOP LEVEL BUILDERS (Zwracające pojedyncze obiekty PGobject) ---
    // =================================================================

    private fun createCompositeParameter(compositeValue: Any, explicitType: String?): PGobject {
        val dbTypeName = explicitType ?: typeRegistry.getPgTypeNameForClass(compositeValue::class)
        return PGobject().apply {
            type = dbTypeName
            value = createCompositeString(compositeValue, dbTypeName)
        }
    }

    private fun createArrayParameter(arrayValue: List<*>, explicitType: String?): PGobject {
        val dbTypeName = explicitType ?: determineArrayType(arrayValue)
        return PGobject().apply {
            type = dbTypeName
            value = createArrayString(arrayValue)
        }
    }

    private fun tryExpandAsDynamicDto(paramValue: Any): PGobject? {
        val strValue = buildDynamicDtoString(paramValue) ?: return null
        return PGobject().apply {
            type = "dynamic_dto"
            value = strValue
        }
    }

    // =================================================================
    // --- LEVEL 2 STRINGIFIERS (Generatory Stringów) ---
    // =================================================================

    private fun createCompositeString(compositeValue: Any, dbTypeName: String): String {
        val typeInfo = typeRegistry.getCompositeDefinition(dbTypeName)
        val valueMap = compositeValue.toMap()

        val fields = typeInfo.attributes.keys.map { dbAttributeName ->
            val value = valueMap[dbAttributeName]
            toPostgresLiteral(value, inComposite = true)
        }
        return "(${fields.joinToString(",")})"
    }

    private fun createArrayString(arrayValue: List<*>): String {
        if (arrayValue.isEmpty()) return "{}"
        val fields = arrayValue.map { value ->
            toPostgresLiteral(value, inComposite = false)
        }
        return "{${fields.joinToString(",")}}"
    }

    private fun buildDynamicDtoString(paramValue: Any): String? {
        if (dynamicDtoStrategy == DynamicDtoSerializationStrategy.EXPLICIT_ONLY && paramValue !is DynamicDto) return null
        if (dynamicDtoStrategy == DynamicDtoSerializationStrategy.AUTOMATIC_WHEN_UNAMBIGUOUS && typeRegistry.isPgType(paramValue::class)) return null

        val dynamicTypeName = typeRegistry.getDynamicTypeNameForClass(paramValue::class) ?: return null
        val serializer = typeRegistry.getDynamicSerializer(dynamicTypeName)

        val dynamicDtoWrapper = if (paramValue is DynamicDto) paramValue else DynamicDto.from(paramValue, dynamicTypeName, serializer)
        return createCompositeString(dynamicDtoWrapper, "dynamic_dto")
    }

    /**
     * Konwertuje wartość zagnieżdżoną na bezpieczny tekst w standardzie PostgreSQL (Literal).
     * Uwzględnia zasady cytowania oraz zachowanie nulla (puste pola dla ROW, lub literał NULL dla ARRAY).
     */
    @OptIn(ExperimentalTime::class)
    private fun toPostgresLiteral(value: Any?, inComposite: Boolean): String {
        if (value == null) return if (inComposite) "" else "NULL"

        // Bezpiecznie rozwiązujemy typy na drugim poziomie
        val actualValue = if (value is PgTyped) value.value else value
        if (actualValue == null) return if (inComposite) "" else "NULL"

        return when {
            actualValue is String -> escapeAndQuote(actualValue.clean())
            actualValue is Enum<*> -> {
                val dbTypeName = typeRegistry.getPgTypeNameForClass(actualValue::class)
                val mappedValue = typeRegistry.getEnumDefinition(dbTypeName).enumToValueMap[actualValue]
                escapeAndQuote(mappedValue ?: actualValue.name)
            }
            actualValue is JsonElement -> escapeAndQuote(actualValue.toString())
            isDataClass(actualValue) -> {
                // Rekurencyjna obsługuje zagnieżdżone kompozyty
                val compStr = buildDynamicDtoString(actualValue)
                    ?: createCompositeString(actualValue, typeRegistry.getPgTypeNameForClass(actualValue::class))
                escapeAndQuote(compStr) // Zagnieżdżony kompozyt zawsze musi być w cudzysłowie
            }
            actualValue is List<*> -> {
                val arrStr = createArrayString(actualValue)
                // PostgreSQL nie stosuje cudzysłowów dla tablic wewnątrz tablic (zwraca {{1,2}}).
                // Jednak tablice zagnieżdżone wew. kompozytu muszą zostać objęte cudzysłowem!
                if (inComposite) escapeAndQuote(arrStr) else arrStr
            }
            actualValue is LocalDate -> formatInfinity(actualValue, LocalDate.DISTANT_FUTURE, LocalDate.DISTANT_PAST) { it.toString() }
            actualValue is LocalDateTime -> formatInfinity(actualValue, LocalDateTime.DISTANT_FUTURE, LocalDateTime.DISTANT_PAST) { it.toString().replace('T', ' ') }
            actualValue is Instant -> formatInfinity(actualValue, Instant.DISTANT_FUTURE, Instant.DISTANT_PAST) { it.toString() }
            actualValue is Duration -> {
                val str = when (actualValue) {
                    Duration.INFINITE -> "infinity"
                    -Duration.INFINITE -> "-infinity"
                    else -> actualValue.toIsoString()
                }
                escapeAndQuote(str)
            }
            actualValue is Boolean -> if (actualValue) "t" else "f"
            else -> actualValue.toString() // Standardowe liczby, UUID itp.
        }
    }

    private fun <T> formatInfinity(value: T, plusInf: T, minusInf: T, format: (T) -> String): String {
        return when (value) {
            plusInf -> "infinity"
            minusInf -> "-infinity"
            else -> format(value)
        }
    }

    /**
     * Cytuje element do wstawienia jako String Literał wg reguł Postgresa.
     */
    private fun escapeAndQuote(value: String): String {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }

    /**
     * Inteligentne przypisanie nazwy typu w formacie PostgreSQL Array (np. `_int4` lub `_text`).
     */
    private fun determineArrayType(arrayValue: List<*>): String {
        val firstNonNull = arrayValue.firstOrNull { it != null } ?: return "text[]"

        val kClass = firstNonNull::class
        return when {
            firstNonNull is String -> "_text"
            firstNonNull is Int -> "_int4"
            firstNonNull is Long -> "_int8"
            firstNonNull is Double -> "_float8"
            firstNonNull is Float -> "_float4"
            firstNonNull is Boolean -> "_bool"
            firstNonNull is UUID -> "_uuid"
            firstNonNull is LocalDate -> "_date"
            firstNonNull is LocalDateTime -> "_timestamp"
            firstNonNull is Instant -> "_timestamptz"
            firstNonNull  is Duration -> "_interval"
            firstNonNull is JsonElement -> "_jsonb"
            firstNonNull is Enum<*> -> "_${typeRegistry.getPgTypeNameForClass(kClass)}"
            isDataClass(firstNonNull) -> {
                if (buildDynamicDtoString(firstNonNull) != null) "_dynamic_dto"
                else "_${typeRegistry.getPgTypeNameForClass(kClass)}"
            }
            else -> "_text"
        }
    }

    private fun validateTypedArrayParameter(arrayValue: Array<*>): Pair<String, List<Any?>> {
        val componentType = arrayValue::class.java.componentType!!.kotlin
        if (componentType.isData || componentType == Map::class || componentType == List::class) {
            throw ConversionException(
                ConversionExceptionMessage.UNSUPPORTED_COMPONENT_TYPE_IN_ARRAY,
                arrayValue,
                targetType = componentType.qualifiedName ?: "unknown"
            )
        }
        return "?" to listOf(arrayValue)
    }

    private fun isDataClass(obj: Any): Boolean {
        return obj::class.isData
    }

    private fun isValueClass(obj: Any): Boolean {
        return obj::class.isValue
    }
}