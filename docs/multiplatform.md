# Multiplatform Support

The denarius was minted once in Rome, yet it crossed every border in the empire without losing its value. A merchant in Alexandria, a legionnaire in Britannia, a tax collector in Gaul — each accepted it at face value, no conversion required. A data class in commonMain works the same way: defined once, it carries the same meaning to your JVM backend and your JavaScript frontend alike.
While the core execution engine of Octavius is JVM-based (utilizing JDBC and HikariCP), the **data model layer** is **Kotlin Multiplatform (KMP)**. This allows you to share your entity definitions, validation logic, and serialization rules across the entire stack.

---

## Availability Matrix

The `:api` module is divided into a common part and platform-specific implementations.

| Component                         | KMP (Common) | JVM (Desktop) | JS Target |
|-----------------------------------|--------------|---------------|-----------|
| **Annotations** (`@PgEnum`, etc.) | ✅            | ✅             | ✅         |
| **Shared DTOs** (Data Classes)    | ✅            | ✅             | ✅         |
| **Multiplatform BigDecimal**      | ✅            | ✅             | ✅         |
| **Custom Serializers**            | ✅            | ✅             | ✅         |
| **DataAccess & Builders**         | ❌            | ✅             | ❌         |
| **Transaction Plan API**          | ❌            | ✅             | ❌         |
| **LISTEN / NOTIFY Client**        | ❌            | ✅             | ❌         |

---

## Shared Data Models

By placing your data classes in the `commonMain` source set of a KMP project, you can use the same classes for:
1. **Persistence:** Octavius Core on the backend maps them to PostgreSQL types.
2. **Communication:** Serialize them to JSON (via `kotlinx.serialization`) to send to a web frontend.
3. **Frontend Logic:** Use the same classes in your Kotlin/JS apps.

### Example: Shared Entity
```kotlin
// commonMain/src/.../Citizen.kt
@Serializable
@PgComposite(name = "citizen_type")
data class Citizen(
    val id: Int,
    val name: String,
    @Contextual val balance: BigDecimal // Multiplatform BigDecimal!
)
```

---

## Multiplatform BigDecimal

Standard `java.math.BigDecimal` is not available in Multiplatform `commonMain`. Octavius provides its own `org.octavius.data.model.BigDecimal` wrapper to ensure consistency:

- **JVM:** A `typealias` to `java.math.BigDecimal`. Full performance and integration with the standard library.
- **JS:** A wrapper around `String`. Since JavaScript's `Number` is a 64-bit float (which loses precision for large decimals), Octavius stores the value as a string to preserve the full precision of PostgreSQL's `numeric` type.

This allows you to safely pass high-precision values (like currency or scientific measurements) from your backend to a JS frontend without accidentally rounding them.

---

## Serializers for PostgreSQL & Multiplatform

Octavius provides specialized serializers in `org.octavius.data.serializer`. Their primary purpose is to ensure that Kotlin types are correctly represented inside PostgreSQL's JSONB format when using **`dynamic_dto`**. 

Because these serializers are part of the Multiplatform `:api` module, they automatically provide the same consistent behavior when sharing your DTOs with a frontend.

### BigDecimal: Preserving Precision in JSONB
By default, `kotlinx.serialization` might serialize large numbers in ways that lead to precision loss in JavaScript or standard JSON parsers. `BigDecimalAsNumberSerializer` ensures the value is stored as a numeric literal in PostgreSQL's JSONB, while maintaining its `String` representation on the JS target for safety.

```kotlin
@Serializable
@DynamicallyMappable("denarii_amount")
data class Tribute(
    @Serializable(with = BigDecimalAsNumberSerializer::class)
    val amount: BigDecimal
)
```

### Date/Time: Handling 'infinity'
Standard `kotlinx-datetime` serializers fail when encountering PostgreSQL's `infinity` values. These serializers are essential for any `@DynamicallyMappable` class that uses dates:
- `DynamicDtoLocalDateSerializer`
- `DynamicDtoLocalDateTimeSerializer`
- `DynamicDtoInstantSerializer`

### Consistent API between Backend and Frontend
By using these serializers, you guarantee that:
1. **In the Database:** `dynamic_dto` payloads are compatible with PostgreSQL's numeric and date logic.
2. **On the Frontend:** Your JS/TS code receives data in a predictable format that matches the backend model.

### The Octavius Serializers Module
To ensure these rules are applied globally to your `dynamic_dto` fields and shared models, use the provided configuration:

```kotlin
// Use in common code for both Octavius initialization and Frontend JSON parsing
val octaviusJson = Json {
    serializersModule = createOctaviusSerializersModule()
    // recommended for JS compatibility with large numbers
    encodeDefaults = true 
}
```

---

## See Also
- [Type System](type-system.md) - How shared types map to PostgreSQL.
- [Data Mapping](data-mapping.md) - Using shared DTOs with query results.
