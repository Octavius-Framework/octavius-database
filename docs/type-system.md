# Type System

*The Roman mint at Lugdunum produced coins in gold, silver, and bronze — each a precise weight, stamped with the emperor's image, immediately recognizable as genuine. Octavius's type system does the same for data: each value moving between PostgreSQL and Kotlin is stamped with its true type, validated, and mapped without ambiguity. Counterfeits — type mismatches — are rejected at the boundary.*

Octavius Database provides automatic bidirectional mapping between PostgreSQL and Kotlin types. This includes support for:

- **Standard types** - primitives, dates, JSON, arrays.
- **Custom types** - statically mapped ENUMs and COMPOSITE types.
- **Dynamic types** - polymorphic storage and JSONB serialization via `dynamic_dto`.

---

## Table of Contents

1. [Standard Type Mapping](#standard-type-mapping)
    - [Arrays](#arrays)
    - [Infinity Values for Date/Time](#infinity-values-for-datetime)
    - [Duration and Interval Rules](#duration-and-interval-rules)
2. [Type Inference & Safety](#type-inference--safety)
    - [OID-Based Result Mapping](#oid-based-result-mapping)
3. [Static Custom Types](#static-custom-types)
    - [@PgEnum](#pgenum)
    - [@PgComposite](#pgcomposite)
4. [Dynamic Types (dynamic_dto)](#dynamic-types-dynamic_dto)
    - [Usage Patterns](#usage-patterns)
    - [Inserting Dynamic Data](#inserting-dynamic-data)
    - [Enum Serialization in dynamic_dto](#enum-serialization-in-dynamic_dto)
    - [Helper Serializers](#helper-serializers--multiplatform-types)
5. [Custom Type Handlers](#custom-type-handlers)
6. [Object Conversion Utilities](#object-conversion-utilities)

---

## Standard Type Mapping

Automatic conversion works out-of-the-box for the following types. Note that if a Kotlin type maps to multiple PostgreSQL types, Octavius uses a **priority-based inference** (see [Parameter Handling](parameter-handling.md#type-inference--safety)).

| PostgreSQL                  | Kotlin          | Notes                                            |
|-----------------------------|-----------------|--------------------------------------------------|
| `int2`, `smallserial`       | `Short`         |                                                  |
| `int4`, `serial`            | `Int`           |                                                  |
| `int8`, `bigserial`         | `Long`          |                                                  |
| `float4`                    | `Float`         |                                                  |
| `float8`                    | `Double`        |                                                  |
| `numeric`                   | `BigDecimal`    |                                                  |
| `text`, `varchar`, `bpchar` | `String`        |                                                  |
| `bool`                      | `Boolean`       |                                                  |
| `uuid`                      | `UUID`          | `java.util.UUID`                                 |
| `bytea`                     | `ByteArray`     |                                                  |
| `jsonb`, `json`             | `JsonElement`   | `kotlinx.serialization.json`                     |
| `void`                      | `Unit`          | Return type of void functions (e.g. `pg_notify`) |
| `date`                      | `LocalDate`     | `kotlinx.datetime` <sup>1</sup>                  |
| `time`                      | `LocalTime`     | `kotlinx.datetime`                               |
| `timestamp`                 | `LocalDateTime` | `kotlinx.datetime` <sup>1</sup>                  |
| `timestamptz`               | `Instant`       | `kotlin.time` <sup>1</sup>                       |
| `interval`                  | `Duration`      | `kotlin.time` <sup>2</sup>                       |

### Arrays

Arrays of all standard types are supported and naturally map to `List<T>`:

| PostgreSQL | Kotlin         |
|------------|----------------|
| `int4[]`   | `List<Int>`    |
| `text[]`   | `List<String>` |
| `uuid[]`   | `List<UUID>`   |
| etc.       | `List<T>`      |

### Infinity Values for Date/Time

<sup>1</sup> **PostgreSQL special values** (`infinity`, `-infinity`) are fully supported for date and timestamp types using provided constants:

| PostgreSQL Type | Special Values          | Kotlin Constants                                             |
|-----------------|-------------------------|--------------------------------------------------------------|
| `date`          | `infinity`, `-infinity` | `LocalDate.DISTANT_FUTURE`, `LocalDate.DISTANT_PAST`         |
| `timestamp`     | `infinity`, `-infinity` | `LocalDateTime.DISTANT_FUTURE`, `LocalDateTime.DISTANT_PAST` |
| `timestamptz`   | `infinity`, `-infinity` | `Instant.DISTANT_FUTURE`, `Instant.DISTANT_PAST`             |

**Usage Example:**

```kotlin
import io.github.octaviusframework.db.api.type.DISTANT_FUTURE

dataAccess.insertInto("mandates")
    .values(listOf("start_date", "end_date"))
    .execute(
        "start_date" to LocalDate.parse("0027-01-16"),
        "end_date" to LocalDate.DISTANT_FUTURE  // Stored as 'infinity' — the eternal mandate
    )
```

### Duration and Interval Rules

<sup>2</sup> **PostgreSQL `INTERVAL` type** maps to Kotlin's `Duration`.

**Infinity Values:**
- `Duration.INFINITE` → `'infinity'`
- `-Duration.INFINITE` → `'-infinity'`

**Conversion Logic:**
PostgreSQL `INTERVAL` values (without a specific date anchor point) are converted to Kotlin `Duration` (seconds) using these fixed rules:
- 1 year = 365.25 days (= 31,557,600 seconds)
- 1 month = 30 days (= 2,592,000 seconds)
- 1 day = 86,400 seconds

*Example:* `'1 year 2 months 5 days'` is converted to exactly `37,173,600` seconds.

**Important notes:**
- These conversion rules apply when converting PostgreSQL intervals **without a specific date anchor point**
- For date arithmetic with actual calendar dates, use native PostgreSQL date functions within your SQL queries
- The conversion preserves precision but normalizes to total seconds in Kotlin's `Duration` type

---

## Type Inference & Safety

Details regarding how Kotlin values are mapped to PostgreSQL types (parameters) can be found in [Parameter Handling](parameter-handling.md#type-inference--safety).

### OID-Based Result Mapping

When reading results from the database, Octavius uses PostgreSQL's internal **Object Identifiers (OIDs)** rather than string-based type names. The JDBC `ResultSet` provides the exact OID for each column. Octavius cross-references this OID with its internal `TypeRegistry` to map the incoming data directly to the correct Kotlin class.

This OID-based resolution:
- **Eliminates Ambiguity:** Bypasses issues with identical type names existing in multiple schemas.
- **Boosts Performance:** OID lookups are extremely fast integer lookups compared to string parsing.
- **Guarantees Type Safety:** Deeply nested composites, arrays, and enums are consistently deserialized to their exact Kotlin representations.

---

## Static Custom Types

### @PgEnum

Maps a Kotlin `enum class` to a PostgreSQL `ENUM`.

**Annotation Parameters:**
| Parameter          | Default            | Description                                    |
|--------------------|--------------------|------------------------------------------------|
| `name`             | `""`               | PostgreSQL type name (auto-generated if empty) |
| `schema`           | `""`               | Explicit PostgreSQL schema name                |
| `pgConvention`     | `SNAKE_CASE_UPPER` | How values are stored in PostgreSQL            |
| `kotlinConvention` | `PASCAL_CASE`      | How values are defined in Kotlin               |

**Example:**
```kotlin
@PgEnum(
    schema = "cursus_honorum",
    pgConvention = CaseConvention.SNAKE_CASE_LOWER,  // stored as 'pro_consul'
    kotlinConvention = CaseConvention.PASCAL_CASE    // defined as ProConsul
) // name defaults to magistrature
enum class Magistrature { Quaestor, Aedile, Praetor, Consul, ProConsul, Censor }

// PostgreSQL Migration:
// CREATE TYPE cursus_honorum.magistrature AS ENUM
//   ('quaestor', 'aedile', 'praetor', 'consul', 'pro_consul', 'censor');
```

### @PgComposite

Maps a Kotlin `data class` to a PostgreSQL `COMPOSITE` type.

**Annotation Parameters:**
| Parameter          | Default            | Description                                    |
|--------------------|--------------------|------------------------------------------------|
| `name`             | `""`               | PostgreSQL type name (auto-generated if empty) |
| `schema`           | `""`               | Explicit PostgreSQL schema name                |
| `mapper`           | `Default...`       | Optional custom `PgCompositeMapper`            |

**Example:**
```kotlin
@PgComposite(name = "province_type") // explicit name
data class Province(val name: String, val capital: String)

// PostgreSQL Migration:
// CREATE TYPE province_type AS (name TEXT, capital TEXT);
```

Composites fully support nesting and arrays:
```kotlin
@PgComposite // name defaults to geo_location
data class GeoLocation(val lat: Double, val lng: Double)

@PgComposite(schema = "public") // name defaults to fortified_position
data class FortifiedPosition(val name: String, val location: GeoLocation)

data class LegionaryFort(val id: Int, val outposts: List<FortifiedPosition>)
```

---

## Manual Composite Mapping (PgCompositeMapper)

By default, Octavius uses reflection for mapping between data classes and PostgreSQL composite types. While highly convenient, you can provide a manual mapper to:
1. **Improve performance:** Bypass reflection in high-volume operations.
2. **Transform types:** Change how data is represented in Kotlin vs. Database.
3. **Handle arrays:** Map PostgreSQL arrays to Kotlin `Array<T>` instead of `List<T>`.

### Basic Usage

Define an object or class implementing `PgCompositeMapper<T>` and reference it in the `@PgComposite` annotation.

```kotlin
@PgComposite(name = "legion_stats", mapper = LegionStatsMapper::class)
data class LegionStats(val strength: Int, val morale: Int)

object LegionStatsMapper : PgCompositeMapper<LegionStats> {
    override fun toDataObject(map: Map<String, Any?>) = LegionStats(
        strength = map["strength"] as Int,
        morale = map["morale"] as Int
    )

    override fun toDataMap(obj: LegionStats) = mapOf(
        "strength" to obj.strength,
        "morale" to obj.morale
    )
}
```

### Type Transformation: Collection Conversion

Octavius default mapping always returns collections as `List<T>`. If your data class requires a primitive array (e.g., for high-performance calculations), use a mapper to perform the conversion.

```kotlin
@PgComposite(name = "battle_signal", mapper = BattleSignalMapper::class)
data class BattleSignal(val legionId: Int, val drumBeats: DoubleArray)

object BattleSignalMapper : PgCompositeMapper<BattleSignal> {
    override fun toDataObject(map: Map<String, Any?>) = BattleSignal(
        legionId = map["legion_id"] as Int,
        // PostgreSQL array comes as List<Double>, convert it to DoubleArray
        drumBeats = (map["drum_beats"] as List<Double>).toDoubleArray()
    )

    override fun toDataMap(obj: BattleSignal) = mapOf(
        "legion_id" to obj.legionId,
        "drum_beats" to obj.drumBeats.toList()
    )
}
```

### Type Transformation: Custom Logic

Mappers can be used to handle types that don't have a direct 1:1 mapping or require special logic.
```kotlin
@PgComposite(name = "senate_event", mapper = SenateEventMapper::class)
data class SenateEvent(val title: String, val occurredAt: Instant)

object SenateEventMapper : PgCompositeMapper<SenateEvent> {
    override fun toDataObject(map: Map<String, Any?>) = SenateEvent(
        title = map["title"] as String,
        occurredAt = Instant.fromEpochMilliseconds(map["occurred_at"] as Long)
    )

    override fun toDataMap(obj: SenateEvent) = mapOf(
        "title" to obj.title,
        "occurred_at" to obj.occurredAt.toEpochMilliseconds()
    )
}
```

### Implementation Details
- **Singleton Support:** If the mapper is a Kotlin `object`, it will be used as a singleton.
- **Class Support:** If the mapper is a `class`, Octavius will instantiate it once using its public no-arg constructor.
- **Type Safety:** Mappers are called during both reading (from DB to Kotlin) and writing (from Kotlin to DB placeholders).

---

## Dynamic Types (dynamic_dto)

The `dynamic_dto` type is Octavius's solution for polymorphic data and ad-hoc nested objects. It relies on `kotlinx.serialization` and the `@DynamicallyMappable` annotation to bridge static SQL and Kotlin's type system.

### How It Works Under The Hood
On startup, Octavius automatically ensures this composite type exists in your `public` schema. See [Core Type Initialization](configuration.md#core-type-initialization) for more details.

```postgresql
CREATE TYPE public.dynamic_dto AS (
    type_name    TEXT,     -- Discriminator key (e.g., "citizen_profile")
    data_payload JSONB     -- Serialized data
);
```

When fetching data, the framework reads the `type_name`, finds the matching Kotlin class annotated with `@DynamicallyMappable(typeName = ...)`, and deserializes the `data_payload` JSONB into that class.

### Core Rules of `@DynamicallyMappable`
1. The class **must** be annotated with `@Serializable` from `kotlinx.serialization`.
2. The class **does not** require any specific superclass or interface (though interfaces are highly recommended).
3. You can use it for direct column storage (even arrays `dynamic_dto[]`) or ad-hoc SQL construction.

---

### Usage Patterns

#### Pattern A: Polymorphism with Interfaces (Recommended)
When storing multiple types in an array or single column, it's best practice to group them under a common Kotlin interface (or `sealed interface`).

```kotlin
sealed interface MonumentRecord

@DynamicallyMappable(typeName = "inscription")
@Serializable
data class Inscription(val text: String, val lang: String) : MonumentRecord

@DynamicallyMappable(typeName = "relief")
@Serializable
data class Relief(val subject: String) : MonumentRecord

// Database: CREATE TABLE monument_records (id INT, record dynamic_dto);

// Fetch directly to a list of your strongly-typed interface
val records = dataAccess.select("record")
    .from("monument_records")
    .toColumn<MonumentRecord>()
```

#### Pattern B: The "Wild West" Polymorphism (`Any`)
Because `@DynamicallyMappable` binds the class based on the annotation (not hierarchy), you *can* technically map values to `Any`. This is useful for extremely generic columns (or column arrays - `dynamic_dto[]`), but requires manual type checking (`is` or `as`) in Kotlin later.

```kotlin
// Database table has a column: records dynamic_dto[]
data class Monument(val id: Int, val records: List<Any>)

val monument = dataAccess.select("id", "records")
    .from("monuments")
    .toSingleOf<Monument>()

// Manual type checking required
monument.records.forEach { record ->
    when (record) {
        is Inscription -> println("Inscription in ${record.lang}")
        is Relief -> println("Relief depicting ${record.subject}")
        else -> println("Unknown record type")
    }
}
```

#### Pattern C: Ad-Hoc Object Mapping
You don't have to store `dynamic_dto` in your tables. You can assemble it dynamically in your queries to map nested results instantly using `jsonb_build_object`. This is perfect for complex JOINs and projections where you want nested results without schema changes.

```kotlin
@DynamicallyMappable(typeName = "citizen_profile")
@Serializable
data class CitizenProfile(val tribe: String, val rights: List<String>)

data class CitizenWithProfile(val id: Int, val name: String, val profile: CitizenProfile)

val citizens = dataAccess.rawQuery("""
    SELECT
        c.id,
        c.name,
        dynamic_dto(
            'citizen_profile',
            jsonb_build_object('tribe', p.tribe, 'rights', p.rights)
        ) AS profile
    FROM citizens c
    JOIN citizen_profiles p ON p.citizen_id = c.id
""").toListOf<CitizenWithProfile>()
```

#### Pattern D: Decoupled Storage (Architectural Purity)
Storing `dynamic_dto` directly in your tables (as seen in Pattern A) couples your database schema to Octavius's custom composite type. If you prefer strict database agnosticism and want to keep your schema perfectly standard (readable even by barbarian Python scripts without the framework), you can store the discriminator and the payload in plain columns, and assemble the `dynamic_dto` only on read.

```sql
-- Standard database schema (no framework lock-in)
CREATE TABLE imperial_archives (
   id SERIAL PRIMARY KEY,
   record_type TEXT NOT NULL,
   document_payload JSONB NOT NULL
);
```
```kotlin
interface ImperialRecord

// Database holds standard columns, Octavius maps them on the fly
val archives = dataAccess.rawQuery("""
    SELECT 
        id, 
        dynamic_dto(record_type, document_payload) AS record 
    FROM imperial_archives
""").toListOf<ImperialRecord>()
```
Trade-off: This decoupled approach keeps your database pristine and perfectly readable for non-Kotlin microservices or raw SQL scripts. 
However, it requires you to write custom INSERT and SELECT queries to split and merge the columns, 
sacrificing the "zero-maintenance" aspect of directly mapping a dynamic_dto column.

---

### Inserting Dynamic Data

Octavius makes it easy to persist polymorphic data by automatically wrapping objects into `dynamic_dto` structures.

```kotlin
val record: MonumentRecord = Inscription("Veni, Vidi, Vici", "Latin")

// Insert a single dynamic object
dataAccess.insertInto("monument_records")
    .value("record")
    .execute("record" to record)

// Bulk insert using unnest (Pattern D)
dataAccess.rawQuery("""
    INSERT INTO imperial_archives (record_type, document_payload)
    SELECT (r).type_name, (r).data_payload
    FROM (SELECT unnest(@records) as r) sub
""").execute("records" to archives)
```

For maximum performance in high-volume scenarios (using **Parallel Lists**), see the [High-Performance Bulk Operations](parameter-handling.md#high-performance-bulk-operations-unnest) section in Parameter Handling.

---

### Enum Serialization in dynamic_dto

When using enums inside `@DynamicallyMappable` classes, `kotlinx.serialization` defaults to outputting the exact Kotlin enum name. To match PostgreSQL conventions inside the JSON payload, you must use contextual serialization.

Octavius automatically generates serializers for all your `@PgEnum` annotated classes. You just need to:
1. Mark the enum property with `@Contextual` in your DTO.
2. Ensure your `Json` instance includes `dataAccess.enumSerializers`.

If you are using Octavius internally (e.g. passing objects to query builders or using `toDynamicDto()`), the framework's internal `Json` instance already includes these serializers.

```kotlin
@PgEnum(pgConvention = CaseConvention.SNAKE_CASE_LOWER)
enum class Magistrature { Quaestor, Aedile, Praetor, Consul }

@DynamicallyMappable(typeName = "appointment_record")
@Serializable
data class AppointmentRecord(
    @Contextual val office: Magistrature // @Contextual is required!
)

// Just insert it - Octavius automatically handles the enum serialization!
dataAccess.insertInto("appointments")
    .value("record")
    .execute("record" to AppointmentRecord(Magistrature.Consul))
// JSON Output inside DB: {"office": "consul"} (instead of "Consul")
```

#### Custom JSON Configuration

If you need to manually construct your own `Json` instance (e.g. for external API layers) and want to retain this automatic enum serialization, you **must** include the generated `enumSerializers` from your `DataAccess` instance:

```kotlin
val myCustomJson = Json {
    serializersModule = octaviusSerializersModule + dataAccess.enumSerializers
}
```

### Helper Serializers & Multiplatform Types

For detailed information on sharing DTOs with frontend applications and using multiplatform types like `BigDecimal`, see the **[Multiplatform Support](multiplatform.md)** guide.

It covers:
- **`BigDecimalAsNumberSerializer`** - Preserving precision in JSONB.
- **Date/Time Serializers** - Support for PostgreSQL `infinity`.
- **octaviusSerializersModule** - Pre-configured SerializersModule instance.

---

## Custom Type Handlers

For types not supported out-of-the-box — or when you need full control over how a value moves between PostgreSQL and Kotlin — you can implement a `TypeHandler<T>`.

Octavius provides two interfaces:

- **`GlobalTypeHandler<T>`** — discovered automatically during startup via classpath scanning. Registered globally and applied to every query in the application.
- **`TypeHandler<T>`** — the base interface. Used when you want to register a handler only for a specific query via `.options {}`, without affecting global state.

`GlobalTypeHandler<T>` extends `TypeHandler<T>` — every `GlobalTypeHandler` is also a `TypeHandler`.

---

### The Interface

```kotlin
interface TypeHandler<T : Any> {
    /** The PostgreSQL type name (e.g., "circle", "ltree", "geometry") */
    val pgTypeName: String

    /** The PostgreSQL schema. Defaults to "" (resolved via search_path). */
    val pgSchema: String get() = ""

    /** The Kotlin class this handler manages. */
    val kotlinClass: KClass<T>

    /**
     * Whether this handler is the default mapping for [kotlinClass].
     * When true, this handler is used when Octavius needs to decide how
     * to serialize a value of [kotlinClass] to PostgreSQL (e.g., in a List<T>
     * element or composite field). See [Handler Priority] below.
     */
    val isDefaultForKotlinType: Boolean get() = false

    /**
     * Optional: read value directly from JDBC ResultSet.
     * If null, Octavius falls back to [fromPgString].
     */
    val fromResultSet: ((ResultSet, Int) -> T?)? get() = null

    /** Parse a value from PostgreSQL text representation (e.g., `"<(1,2),3>"`) */
    val fromPgString: (String) -> T

    /**
     * Optional: convert value to a JDBC-compatible object.
     * If null, Octavius falls back to [toPgString].
     */
    val toJdbc: ((T) -> Any)? get() = null

    /** Convert value to PostgreSQL text representation (used inside literals and composite fields) */
    val toPgString: (T) -> String
}
```

---

### Reading from PostgreSQL (DB → Kotlin)

When Octavius reads a result set, it resolves each column's type using its **OID** — the internal PostgreSQL type identifier. If a custom handler is registered for that OID, it takes precedence over the built-in mapping.

The rules are simple:
- A custom handler silently overrides the default handler for the same OID (logged at `INFO`).
- Two custom handlers registered for the same OID throw `TypeRegistryException` with `AMBIGUOUS_TYPE_MAPPING`.

---

### Writing to PostgreSQL (Kotlin → DB)

When Octavius converts a Kotlin value to a PostgreSQL parameter, it must decide which `TypeHandler` to use. It applies the following priority chain to find the correct handler for a given value:

#### 1. `PgTyped` — explicit type cast

If a value is wrapped in `PgTyped` (via `.withPgType()`), that wrapper controls the final `::cast` appended to the placeholder. The handler still performs the actual serialization, but the PostgreSQL type used for casting is whatever `PgTyped` specifies, not what the handler declares.

```kotlin
// Handler serializes the value; PgTyped controls what type it's cast to
val param = myValue.withPgType("custom_schema", "my_type")
```

Use this when the handler's declared `pgTypeName` differs from what you need in a specific query.

#### 2. Resolving the Default Handler for a Class

When Octavius serializes a raw value (e.g., as a parameter, an element inside a `List<T>`, or a field in a composite), it looks up the handler associated with that Kotlin class. The priority is strictly determined by the `isDefaultForKotlinType` flag and the registration scope:

| Handler scope          | `isDefaultForKotlinType` | Priority                                          |
|------------------------|--------------------------|---------------------------------------------------|
| Per-query (`.options`) | `true`                   | **Highest** — overrides everything for that query |
| Global custom          | `true`                   | High — overrides built-ins and `false` customs    |
| Global custom          | `false`                  | Medium — overrides other `false` customs only     |
| Built-in standard      | `true`                   | Lowest — always overridable                       |

**Crucial Note on Per-Query Handlers:** A handler registered via `.options { registerTypeHandler(...) }` will **only** override the serialization of its Kotlin class if its `isDefaultForKotlinType` property is set to `true`. Otherwise, it is only used when explicitly requested (e.g., when reading a column of its specific PostgreSQL type).

```kotlin
// SpecialLegionHandler must have `isDefaultForKotlinType = true` to override Legion serialization
dataAccess.select("*").from("legions")
    .options { registerTypeHandler(SpecialLegionHandler) }
    .toListOf<Legion>()
```

*(Note: Two global custom handlers both marked `isDefaultForKotlinType = true` for the same class will throw an `AMBIGUOUS_TYPE_MAPPING` error during startup.)*

#### 3. Fallback

If no handler matches, Octavius falls back to composite serialization (for data classes), enum serialization, or `toString()`.

---

### A Note on Primitive Types

A `TypeHandler` is keyed on a Kotlin class. This means you cannot register two different handlers for `String` — one for column A and another for column B. There is only one `String` class, so there can only be one default handler for it.

If you need a type to be serialized differently in different contexts, **wrap it in a value class**:

```kotlin
// ❌ Won't work — can't have two different String handlers
object LtreeHandler : GlobalTypeHandler<String> { ... }     // conflicts with default String handler
object RegexpHandler : GlobalTypeHandler<String> { ... }    // also String — AMBIGUOUS_TYPE_MAPPING

// ✅ Correct — separate types, separate handlers
@JvmInline value class LTree(val path: String)
@JvmInline value class PgRegexp(val pattern: String)

object LTreeHandler : GlobalTypeHandler<LTree> {
    override val pgTypeName = "ltree"
    override val kotlinClass = LTree::class
    override val isDefaultForKotlinType = true
    override val fromPgString = { s: String -> LTree(s) }
    override val toPgString = { t: LTree -> t.path }
}
```

Value classes enforce strict type safety in your domain, giving Octavius a distinct class to key the handler on without heavy abstractions.

---

### Global Registration via `GlobalTypeHandler`

To register a handler globally, implement `GlobalTypeHandler<T>` (not just `TypeHandler<T>`) and place it within a package listed in `packagesToScan`. It must be either:
- A public `object`, or
- A public `class` with a public no-arg constructor.

```kotlin
object LTreeHandler : GlobalTypeHandler<LTree> {
    override val pgTypeName = "ltree"
    override val kotlinClass = LTree::class
    override val isDefaultForKotlinType = true
    override val fromPgString = { s: String -> LTree(s) }
    override val toPgString = { t: LTree -> t.path }
}
```

Once registered, `LTree` can be used as:
- A query parameter or result column.
- A field within a `@PgComposite` data class.
- An element in a PostgreSQL array (`List<LTree>`).

---

### Per-Query Configuration via `.options {}`

The `.options()` method on query builders provides a way to override global mapping configurations for a specific query execution. This is extremely useful when you need to handle ad-hoc data structures, bypass reflection, or temporarily redefine how a specific type is mapped without affecting the entire application.

Available configurations:

- **`returnCompositeAsMap(name, schema)`**: Directs the framework to return a specific PostgreSQL composite type as a nested `Map<String, Any?>` instead of attempting to map it to a Kotlin data class.
- **`returnAllCompositesAsMaps()`**: Forces all composite types encountered in the query result to be returned as Maps. Perfect for dynamic reporting or exporting raw data.
- **`registerCompositeMapper(name, schema, mapper)`**: Bypasses the default reflection-based mapper and uses your custom `PgCompositeMapper` just for this query. **This works symmetrically** — it is used both when reading the composite from the ResultSet and when serializing it as a query parameter.
- **`registerTypeHandler(handler)`**: Registers a custom `TypeHandler` that takes precedence over global handlers during this query's execution.
- **`json(json: Json)`**: Overrides the internal `Json` instance used for `dynamic_dto` serialization.

```kotlin
dataAccess.select("*").from("legions")
    .options {
        // Return the 'address' composite as a Map
        returnCompositeAsMap(name = "address")
        
        // Return all composites as Maps
        // returnAllCompositesAsMaps()
        
        // Register a custom TypeHandler for this query only
        // registerTypeHandler(MyCustomHandler)
    }
    .toListOf<Legion>()
```

Per-query configurations are resolved once at query construction and never affect global state.

## Object Conversion Utilities

For overriding the default `snake_case` ↔ `camelCase` mapping for individual properties, use the `@MapKey` annotation.

Utilities like `toDataObject()` and `toDataMap()` are available to convert between data classes and maps. See [Data Mapping](data-mapping.md) documentation for full details.
