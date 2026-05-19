package io.github.octaviusframework.db.api.builder

import io.github.octaviusframework.db.api.mapper.DataMapper
import io.github.octaviusframework.db.api.transaction.TransactionStep
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Interface for StepBuilder - contains the same terminal methods as TerminalReturningMethods
 * and TerminalModificationMethods, but returns TransactionStep instead of executing queries.
 */
interface StepBuilderMethods {
    // --- Returning full rows ---

    /** Creates a TransactionStep with toList method */
    fun toList(params: Map<String, Any?> = emptyMap()): TransactionStep<List<Map<String, Any?>>>

    /** Creates a TransactionStep with toSingle method */
    fun toSingle(params: Map<String, Any?> = emptyMap()): TransactionStep<Map<String, Any?>?>

    /** Creates a TransactionStep with toSingleStrict method */
    fun toSingleStrict(params: Map<String, Any?> = emptyMap()): TransactionStep<Map<String, Any?>>

    // --- Returning scalar values ---

    /** Creates a TransactionStep with toField method */
    fun <T> toField(kType: KType, params: Map<String, Any?> = emptyMap()): TransactionStep<T>

    /** Creates a TransactionStep with toFieldStrict method */
    fun <T> toFieldStrict(kType: KType, params: Map<String, Any?> = emptyMap()): TransactionStep<T>

    /** Creates a TransactionStep with toColumn method */
    fun <T> toColumn(kType: KType, params: Map<String, Any?> = emptyMap()): TransactionStep<List<T>>

    // --- Returning data class objects ---

    /** Creates a TransactionStep with toListOf method */
    fun <T : Any> toListOf(kType: KType, params: Map<String, Any?> = emptyMap()): TransactionStep<List<T>>

    /** Creates a TransactionStep with toSingleOf method */
    fun <T> toSingleOf(kType: KType, params: Map<String, Any?> = emptyMap()): TransactionStep<T>

    /** Creates a TransactionStep with toListOf method using a manual [mapper] */
    fun <T : Any> toListOf(
        params: Map<String, Any?> = emptyMap(),
        mapper: DataMapper<T>
    ): TransactionStep<List<T>>

    /** Creates a TransactionStep with toSingleOf method using a manual [mapper] */
    fun <T> toSingleOf(kType: KType, params: Map<String, Any?> = emptyMap(), mapper: DataMapper<T & Any>): TransactionStep<T>

    // --- Modification method ---

    /** Creates a TransactionStep with execute method */
    fun execute(params: Map<String, Any?> = emptyMap()): TransactionStep<Int>
}

// To List / To Single

fun StepBuilderMethods.toList(vararg params: Pair<String, Any?>): TransactionStep<List<Map<String, Any?>>> =
    toList(params.toMap())

fun StepBuilderMethods.toSingle(vararg params: Pair<String, Any?>): TransactionStep<Map<String, Any?>?> =
    toSingle(params.toMap())

fun StepBuilderMethods.toSingleStrict(vararg params: Pair<String, Any?>): TransactionStep<Map<String, Any?>> =
    toSingleStrict(params.toMap())

// To Field / To Column

inline fun <reified T> StepBuilderMethods.toField(
    params: Map<String, Any?> = emptyMap()
): TransactionStep<T> = toField(typeOf<T>(), params)

inline fun <reified T> StepBuilderMethods.toField(
    vararg params: Pair<String, Any?>
): TransactionStep<T> = toField(typeOf<T>(), params.toMap())

inline fun <reified T> StepBuilderMethods.toFieldStrict(
    params: Map<String, Any?> = emptyMap()
): TransactionStep<T> = toFieldStrict(typeOf<T>(), params)

inline fun <reified T> StepBuilderMethods.toFieldStrict(
    vararg params: Pair<String, Any?>
): TransactionStep<T> = toFieldStrict(typeOf<T>(), params.toMap())

inline fun <reified T> StepBuilderMethods.toColumn(
    params: Map<String, Any?> = emptyMap()
): TransactionStep<List<T>> = toColumn(typeOf<T>(), params)

inline fun <reified T> StepBuilderMethods.toColumn(
    vararg params: Pair<String, Any?>
): TransactionStep<List<T>> = toColumn(typeOf<T>(), params.toMap())

// To List Of / To Single Of

inline fun <reified T : Any> StepBuilderMethods.toListOf(vararg params: Pair<String, Any?>): TransactionStep<List<T>> =
    toListOf(typeOf<T>(), params.toMap())


inline fun <reified T : Any> StepBuilderMethods.toListOf(params: Map<String, Any?> = emptyMap()): TransactionStep<List<T>> =
    toListOf(typeOf<T>(), params)


inline fun <reified T> StepBuilderMethods.toSingleOf(vararg params: Pair<String, Any?>): TransactionStep<T> =
    toSingleOf(typeOf<T>(), params.toMap())

inline fun <reified T> StepBuilderMethods.toSingleOf(params: Map<String, Any?> = emptyMap()): TransactionStep<T> =
    toSingleOf(typeOf<T>(), params)

// To Single Of / To List Of with Mappers

fun <T : Any> StepBuilderMethods.toListOf(
    vararg params: Pair<String, Any?>,
    mapper: DataMapper<T>
): TransactionStep<List<T>> = toListOf(params.toMap(), mapper)

inline fun <reified T> StepBuilderMethods.toSingleOf(
    vararg params: Pair<String, Any?>,
    mapper: DataMapper<T & Any>
): TransactionStep<T> = toSingleOf(typeOf<T>(), params.toMap(), mapper)

inline fun <reified T> StepBuilderMethods.toSingleOf(
    params: Map<String, Any?> = emptyMap(),
    mapper: DataMapper<T & Any>
): TransactionStep<T> = toSingleOf(typeOf<T>(), params, mapper)

// Execute

fun StepBuilderMethods.execute(vararg params: Pair<String, Any?>): TransactionStep<Int> =
    execute(params.toMap())
