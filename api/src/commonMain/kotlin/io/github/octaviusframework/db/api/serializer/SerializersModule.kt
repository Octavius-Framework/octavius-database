package io.github.octaviusframework.db.api.serializer

import io.github.octaviusframework.db.api.model.BigDecimal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.time.Instant

/**
 * A pre-configured [SerializersModule] required for Octavius Database features,
 * particularly ensuring correct serialization behavior for `dynamic_dto`.
 *
 * This module provides specific contextual serializers for types that require
 * special handling when mapped to PostgreSQL via JSON:
 * - [BigDecimal]: Preserves exact numeric precision during JSON serialization.
 * - [LocalDate], [LocalDateTime], [Instant]: Support for PostgreSQL's special `infinity` and `-infinity` date/time values.
 *
 * This module should be registered in your [Json] configuration when creating
 * custom JSON formats in the application layer that interact with Octavius-managed data types.
 */
val octaviusSerializersModule = SerializersModule {
    contextual(BigDecimal::class, BigDecimalAsNumberSerializer)
    contextual(LocalDate::class, LocalDateWithInfinitySerializer)
    contextual(LocalDateTime::class, LocalDateTimeWithInfinitySerializer)
    contextual(Instant::class, InstantWithInfinitySerializer)

}
