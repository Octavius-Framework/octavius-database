package org.octavius.data.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

/**
 * Serializer for [Instant] that supports PostgreSQL's `infinity` and `-infinity` values.
 *
 * This serializer is specifically designed for use within `dynamic_dto` (JSONB) columns,
 * ensuring that date-time values are correctly mapped to their PostgreSQL representations.
 */
object DynamicDtoInstantSerializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("org.octavius.data.serializer.DynamicDtoInstantSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        when (value) {
            Instant.DISTANT_FUTURE -> encoder.encodeString("infinity")
            Instant.DISTANT_PAST -> encoder.encodeString("-infinity")
            else -> encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): Instant {
        return when (val string = decoder.decodeString()) {
            "infinity" -> Instant.DISTANT_FUTURE
            "-infinity" -> Instant.DISTANT_PAST
            else -> Instant.parse(string)
        }
    }
}