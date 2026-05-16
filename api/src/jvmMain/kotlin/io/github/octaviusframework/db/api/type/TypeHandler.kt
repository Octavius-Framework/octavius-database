package io.github.octaviusframework.db.api.type

import java.sql.ResultSet
import kotlin.reflect.KClass

/**
 * Interface for handling conversion between Kotlin types and PostgreSQL types.
 *
 * Registered converters are automatically used for:
 * - Single values in queries
 * - Elements in PostgreSQL arrays
 * - Fields within composite types
 *
 * @param T The Kotlin type this converter handles.
 */
interface TypeHandler<T : Any> {
    val pgTypeName: String
    val pgSchema: String get() = ""
    val kotlinClass: KClass<T>
    val isDefaultForKotlinType: Boolean get() = false
    val fromResultSet: ((ResultSet, Int) -> T?)? get() = null
    val fromPgString: (String) -> T
    val toJdbc: ((T) -> Any)? get() = null
    val toPgString: (T) -> String
}

/**
 * Interface for handling conversion between Kotlin types and PostgreSQL types.
 * Classes extending this interface are automatically scanned at the initialization
 *
 * Registered converters are automatically used for:
 * - Single values in queries
 * - Elements in PostgreSQL arrays
 * - Fields within composite types
 *
 * @param T The Kotlin type this converter handles.
 */
interface GlobalTypeHandler<T : Any>: TypeHandler<T>