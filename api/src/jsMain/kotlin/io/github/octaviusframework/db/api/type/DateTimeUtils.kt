package io.github.octaviusframework.db.api.type

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

// max and min Year from class LocalDate
internal const val YEAR_MIN = -999_999_999
internal const val YEAR_MAX = 999_999_999

// max and min time from class LocalTime
internal const val NANOS_PER_ONE = 1_000_000_000
internal val MIN_TIME: LocalTime = LocalTime(0, 0, 0, 0)
internal val MAX_TIME: LocalTime = LocalTime(23, 59, 59, NANOS_PER_ONE - 1)
// max and min time from class LocalDateTime
internal val MIN_DATETIME: LocalDateTime = LocalDateTime(LocalDate.DISTANT_PAST, MIN_TIME)
internal val MAX_DATETIME: LocalDateTime = LocalDateTime(LocalDate.DISTANT_FUTURE, MAX_TIME)

/**
 * Extension properties for kotlinx.datetime types to support PostgreSQL infinity values.
 *
 * PostgreSQL's DATE, TIMESTAMP, and TIMESTAMPTZ types support special values 'infinity' and '-infinity'
 * to represent unbounded dates. These extensions provide corresponding constants for Kotlin types.
 *
 * ## Usage with PostgreSQL
 *
 * ```kotlin
 * // Insert a contract with no end date
 * dataAccess.insertInto("contracts")
 *     .values(listOf("end_date"))
 *     .execute("end_date" to LocalDate.DISTANT_FUTURE)  // Stored as 'infinity'
 *
 * // Query returns LocalDate.DISTANT_FUTURE for infinity values
 * val contract = dataAccess.select("end_date")
 *     .from("contracts")
 *     .toSingleOf<Contract>()
 *     .getOrThrow()!!
 * ```
 *
 * ## Notes
 *
 * - [kotlin.time.Instant.DISTANT_PAST] and [kotlin.time.Instant.DISTANT_FUTURE] are provided
 *   by the Kotlin standard library and map to PostgreSQL TIMESTAMPTZ infinity values.
 * - [kotlin.time.Duration.INFINITE] and `-Duration.INFINITE` map to PostgreSQL INTERVAL infinity values.
 */

/**
 * The minimum LocalDate value, maps to PostgreSQL '-infinity' for DATE type.
 */
actual val LocalDate.Companion.DISTANT_PAST: LocalDate
    get() = LocalDate(YEAR_MIN, 1,1)

/**
 * The maximum LocalDate value, maps to PostgreSQL 'infinity' for DATE type.
 */
actual val LocalDate.Companion.DISTANT_FUTURE: LocalDate
    get() = LocalDate(YEAR_MAX, 12,31)

/**
 * The minimum LocalDateTime value, maps to PostgreSQL '-infinity' for TIMESTAMP type.
 */
actual val LocalDateTime.Companion.DISTANT_PAST: LocalDateTime
    get() = MIN_DATETIME

/**
 * The maximum LocalDateTime value, maps to PostgreSQL 'infinity' for TIMESTAMP type.
 */
actual val LocalDateTime.Companion.DISTANT_FUTURE: LocalDateTime
    get() = MAX_DATETIME
