package io.github.octaviusframework.db.domain.test.weird

import io.github.octaviusframework.db.api.annotation.MapKey
import io.github.octaviusframework.db.api.annotation.PgComposite
import io.github.octaviusframework.db.api.annotation.PgEnum

@PgEnum(name = "weird enum.with space", schema = "weird schema.with dots")
enum class WeirdEnum {
    Val1, Val2
}

@PgComposite(name = "weird composite.with \"quotes\"", schema = "weird schema.with dots")
data class WeirdComposite(
    @MapKey("field.one") val fieldOne: String,
    @MapKey("field two") val fieldTwo: Int
)
