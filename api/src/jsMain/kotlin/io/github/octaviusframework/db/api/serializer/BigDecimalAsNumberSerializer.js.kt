package io.github.octaviusframework.db.api.serializer

import io.github.octaviusframework.db.api.model.BigDecimal
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

internal actual fun encodeBigDecimalNative(
    encoder: Encoder,
    value: BigDecimal
) = (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toString()))

internal actual fun decodeBigDecimalNative(decoder: Decoder): BigDecimal {
    val element = (decoder as JsonDecoder).decodeJsonElement()
    if (element is JsonNull) {
        throw SerializationException("Unexpected null value for non-nullable BigDecimal")
    }
    return BigDecimal(element.jsonPrimitive.content)
}
