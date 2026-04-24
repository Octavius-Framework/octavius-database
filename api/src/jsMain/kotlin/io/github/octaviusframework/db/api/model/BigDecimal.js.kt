package io.github.octaviusframework.db.api.model

/**
 * JavaScript implementation of [BigDecimal].
 * 
 * Since JS `Number` is a 64-bit float, we store the value as a `String` 
 * to preserve full precision of PostgreSQL's `numeric` type.
 */
actual class BigDecimal(val value: String) {
    override fun toString(): String {
        return value
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class.js != other::class.js) return false

        other as BigDecimal

        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}