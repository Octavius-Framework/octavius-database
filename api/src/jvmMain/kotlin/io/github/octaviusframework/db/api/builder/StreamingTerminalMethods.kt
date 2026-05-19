package io.github.octaviusframework.db.api.builder

import io.github.octaviusframework.db.api.DataResult
import io.github.octaviusframework.db.api.mapper.DataMapper
import kotlin.reflect.KClass

interface StreamingTerminalMethods {
    /**
     * Executes the query and processes each row individually using the provided action,
     * without loading the entire result into memory. Ideal for large datasets.
     * Returns DataResult.Success(Unit) if the operation succeeded or DataResult.Failure in case of error.
     *
     * @param params Query parameters.
     * @param action Function that will be executed for each row. The row is mapped to Map<String, Any?>.
     */
    fun forEachRow(params: Map<String, Any?> = emptyMap(), action: (row: Map<String, Any?>) -> Unit): DataResult<Unit>

    /**
     * Executes the query and processes each row individually using the provided action,
     * without loading the entire result into memory. Ideal for large datasets.
     * Returns DataResult.Success(Unit) if the operation succeeded or DataResult.Failure in case of error.
     *
     * @param params Query parameters.
     * @param mapper Manual mapper to use for converting the row to a data object.
     * @param action Function that will be executed for each row. The row is mapped to a data object.
     */
    fun <T : Any> forEachRowOf(
        mapper: DataMapper<T>,
        params: Map<String, Any?> = emptyMap(),
        action: (obj: T) -> Unit
    ): DataResult<Unit>

    /**
     * Executes the query and processes each row individually using the provided action,
     * without loading the entire result into memory. Ideal for large datasets.
     * Returns DataResult.Success(Unit) if the operation succeeded or DataResult.Failure in case of error.
     *
     * @param params Query parameters.
     * @param action Function that will be executed for each row. The row is mapped to a data class.
     */
    fun <T : Any> forEachRowOf(kClass: KClass<T>, params: Map<String, Any?>, action: (obj: T) -> Unit): DataResult<Unit>

}

fun StreamingTerminalMethods.forEachRow(
    vararg params: Pair<String, Any?>,
    action: (row: Map<String, Any?>) -> Unit
): DataResult<Unit> =
    forEachRow(params.toMap(), action)

fun <T : Any> StreamingTerminalMethods.forEachRowOf(
    mapper: DataMapper<T>,
    vararg params: Pair<String, Any?>,
    action: (obj: T) -> Unit
): DataResult<Unit> =
    forEachRowOf(mapper, params.toMap(), action)

inline fun <reified T : Any> StreamingTerminalMethods.forEachRowOf(
    params: Map<String, Any?> = emptyMap(),
    noinline action: (obj: T) -> Unit
): DataResult<Unit> =
    forEachRowOf(T::class, params, action)

inline fun <reified T : Any> StreamingTerminalMethods.forEachRowOf(
    vararg params: Pair<String, Any?>,
    noinline action: (obj: T) -> Unit
): DataResult<Unit> =
    forEachRowOf(T::class, params.toMap(), action)
