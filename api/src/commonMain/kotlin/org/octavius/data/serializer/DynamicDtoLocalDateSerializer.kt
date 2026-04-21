package org.octavius.data.serializer

import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.octavius.data.type.DISTANT_FUTURE
import org.octavius.data.type.DISTANT_PAST

/**
 * Serializer for [LocalDate] that supports PostgreSQL's `infinity` and `-infinity` values.
 *
 * This serializer ensures that [LocalDate.Companion.DISTANT_FUTURE] and [LocalDate.Companion.DISTANT_PAST]
 * are correctly converted to `infinity` and `-infinity` strings respectively,
 * which are understood by PostgreSQL.
 */
object DynamicDtoLocalDateSerializer : KSerializer<LocalDate> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("org.octavius.data.serializer.DynamicDtoLocalDateSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        when (value) {
            LocalDate.DISTANT_FUTURE -> encoder.encodeString("infinity")
            LocalDate.DISTANT_PAST -> encoder.encodeString("-infinity")
            else -> encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): LocalDate {
        return when (val string = decoder.decodeString()) {
            "infinity" -> LocalDate.DISTANT_FUTURE
            "-infinity" -> LocalDate.DISTANT_PAST
            else -> LocalDate.parse(string)
        }
    }
}