package org.octavius.data.serializer

import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal


internal actual fun encodeBigDecimalNative(
    encoder: Encoder, value: BigDecimal
) = (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))

internal actual fun decodeBigDecimalNative(decoder: Decoder): BigDecimal =
    BigDecimal((decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content)
