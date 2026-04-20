package org.octavius.data.type

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

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
expect val LocalDate.Companion.DISTANT_PAST: LocalDate

/**
 * The maximum LocalDate value, maps to PostgreSQL 'infinity' for DATE type.
 */
expect val LocalDate.Companion.DISTANT_FUTURE: LocalDate

/**
 * The minimum LocalDateTime value, maps to PostgreSQL '-infinity' for TIMESTAMP type.
 */
expect val LocalDateTime.Companion.DISTANT_PAST: LocalDateTime

/**
 * The maximum LocalDateTime value, maps to PostgreSQL 'infinity' for TIMESTAMP type.
 */
expect val LocalDateTime.Companion.DISTANT_FUTURE: LocalDateTime
