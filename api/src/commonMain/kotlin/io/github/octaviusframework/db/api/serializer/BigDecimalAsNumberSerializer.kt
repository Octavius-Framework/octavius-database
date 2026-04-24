package io.github.octaviusframework.db.api.serializer

import io.github.octaviusframework.db.api.model.BigDecimal
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * JSON serializer for [BigDecimal][BigDecimal] that preserves numeric precision.
 *
 * Encodes BigDecimal as an unquoted JSON number literal (not a string),
 * which is important for PostgreSQL's JSONB type to correctly interpret
 * the value as a number.
 *
 * ### Usage Example
 * ```kotlin
 * @Serializable
 * @DynamicallyMappable("price_data")
 * data class PriceData(
 *     @Serializable(with = BigDecimalAsNumberSerializer::class)
 *     val price: BigDecimal
 * )
 * ```
 */
object BigDecimalAsNumberSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.github.octaviusframework.db.api.serializer.BigDecimalAsNumberSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encodeBigDecimalNative(encoder, value)
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        return decodeBigDecimalNative(decoder)
    }
}

internal expect fun encodeBigDecimalNative(encoder: Encoder, value: BigDecimal)
internal expect fun decodeBigDecimalNative(decoder: Decoder): BigDecimal