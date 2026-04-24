package io.github.octaviusframework.db.api.serializer

import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive
import io.github.octaviusframework.db.api.model.BigDecimal

internal actual fun encodeBigDecimalNative(
    encoder: Encoder,
    value: BigDecimal
) = (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toString()))

internal actual fun decodeBigDecimalNative(decoder: Decoder): BigDecimal =
    BigDecimal((decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content)