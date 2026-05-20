package io.github.octaviusframework.db.core.jdbc

import io.github.octaviusframework.db.api.mapper.DataMapper
import io.github.octaviusframework.db.api.exception.TypeMappingException
import io.github.octaviusframework.db.api.exception.TypeMappingExceptionMessage
import io.github.octaviusframework.db.api.toDataObject
import io.github.octaviusframework.db.api.validateValue
import io.github.octaviusframework.db.core.type.InternalQueryOptions
import io.github.octaviusframework.db.core.type.ResultSetValueExtractor
import io.github.octaviusframework.db.core.type.registry.TypeRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Factory providing high-level [RowMapper] implementations for converting PostgreSQL `ResultSet`
 * rows into Kotlin data structures.
 *
 * This class coordinates between JDBC's mapping infrastructure and Octavius's custom
 * type conversion logic provided by [io.github.octaviusframework.db.core.type.ResultSetValueExtractor].
 *
 * @param typeRegistry The registry containing OID-to-type mappings and custom type definitions.
 */
@Suppress("FunctionName")
internal class RowMappers(
    typeRegistry: TypeRegistry,
    json: Json
) {
    private val valueExtractor = ResultSetValueExtractor(typeRegistry, json)

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    /**
     * Creates a mapper that converts each row into a `Map<String, Any?>`.
     *
     * Keys in the map correspond to column labels (aliases) from the SQL query.
     * Values are automatically converted to their appropriate Kotlin types.
     *
     * Ideal for:
     * - Dynamic queries where the result structure is not known at compile time.
     * - Reporting and ad-hoc data analysis.
     * - Simple queries where defining a data class is unnecessary.
     */
    fun ColumnNameMapper(options: InternalQueryOptions): RowMapper<Map<String, Any?>> {
        return RowMapper { rs ->
            val data = mutableMapOf<String, Any?>()
            val metaData = rs.metaData

            logger.trace { "Mapping row with ${metaData.columnCount} columns using ColumnNameMapper" }
            for (i in 1..metaData.columnCount) {
                val columnName = metaData.getColumnLabel(i)
                data[columnName] = valueExtractor.extract(rs, i, options)
            }
            data
        }
    }

    /**
     * Creates a mapper that extracts a single value from the first column of the result set.
     *
     * This mapper is highly optimized for "scalar" queries.
     *
     * @param kType The expected Kotlin type of the field, used for validation and nullability checks.
     * @return A mapper returning a single value or throwing [io.github.octaviusframework.db.api.exception.TypeMappingException] on type mismatch or unexpected null.
     */
    fun SingleValueMapper(kType: KType, options: InternalQueryOptions): RowMapper<Any?> {
        return RowMapper { rs ->
            val value = valueExtractor.extract(rs, 1, options)
            if (value == null) {
                if (!kType.isMarkedNullable) {
                    throw TypeMappingException(
                        messageEnum = TypeMappingExceptionMessage.UNEXPECTED_NULL_VALUE,
                        value = null,
                        targetType = kType.toString()
                    )
                }
                return@RowMapper null
            }

            validateValue(value, kType)
        }
    }

    /**
     * Creates a mapper that converts each row into an instance of a specified Kotlin `data class`.
     *
     * The mapping process follows these steps:
     * 1. Extracts the row as a [Map] using [ColumnNameMapper].
     * 2. Uses [toDataObject][toDataObject] (reflection) to instantiate the class.
     *
     * Naming conventions:
     * Column names in SQL (e.g., `user_id`) are automatically matched to class properties (e.g., `userId`)
     * using the standard `snake_case` -> `camelCase` transformation,
     * for custom name mapping use [MapKey][io.github.octaviusframework.db.api.annotation.MapKey] annotation.
     *
     * @param T The target type.
     * @param kClass The Kotlin class to map into.
     */
    fun <T : Any> DataObjectMapper(kClass: KClass<T>, options: InternalQueryOptions): RowMapper<T> {
        val baseMapper = ColumnNameMapper(options)
        return RowMapper { rs ->
            logger.trace { "Mapping row to ${kClass.simpleName} using DataObjectMapper" }
            val map = baseMapper.mapRow(rs)

            val result = map.toDataObject(kClass)
            logger.trace { "Successfully mapped row to ${kClass.simpleName}" }
            result
        }
    }

    /**
     * Creates a mapper that converts each row using a provided manual [mapper].
     *
     * This is the most efficient way to map rows as it bypasses reflection entirely.
     */
    fun <T : Any> CustomObjectMapper(mapper: DataMapper<T>, options: InternalQueryOptions): RowMapper<T> {
        val baseMapper = ColumnNameMapper(options)
        return RowMapper { rs ->
            val map = baseMapper.mapRow(rs)
            try {
                mapper.toDataObject(map)
            } catch (e: Exception) {
                throw TypeMappingException(
                    messageEnum = TypeMappingExceptionMessage.OBJECT_MAPPING_FAILED,
                    rowData = map,
                    cause = e
                )
            }
        }
    }
}
