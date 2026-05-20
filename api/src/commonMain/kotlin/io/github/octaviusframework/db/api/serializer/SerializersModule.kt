package io.github.octaviusframework.db.api.serializer

import io.github.octaviusframework.db.api.model.BigDecimal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.time.Instant

/**
 * Creates [SerializersModule] required for Octavius features,
 * particularly for `dynamic_dto` serialization.
 *
 * This module includes serializers for:
 * - [BigDecimal] (preserving precision in JSON)
 * - [LocalDate], [LocalDateTime], [Instant] (with PostgreSQL infinity support)
 *
 * This function should be used to configure the [Json] instance
 * when working with Octavius-managed data types.
 */
val octaviusSerializersModule = SerializersModule {
    contextual(BigDecimal::class, BigDecimalAsNumberSerializer)
    contextual(LocalDate::class, LocalDateWithInfinitySerializer)
    contextual(LocalDateTime::class, LocalDateTimeWithInfinitySerializer)
    contextual(Instant::class, InstantWithInfinitySerializer)

}
