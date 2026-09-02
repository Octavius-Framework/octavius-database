# Octavius Database

<div align="center">

**An explicit, SQL-first data access layer for Kotlin & PostgreSQL**

[![KDoc API](https://img.shields.io/badge/KDoc-api-7F52FF?logo=kotlin&logoColor=white)](https://octavius-framework.github.io/octavius-database/api)
[![KDoc Core](https://img.shields.io/badge/KDoc-core-7F52FF?logo=kotlin&logoColor=white)](https://octavius-framework.github.io/octavius-database/core)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.octavius-framework/database-core.svg?label=Maven%20Central)](https://central.sonatype.com/search?namespace=io.github.octavius-framework)
[![Status](https://img.shields.io/badge/status-deprecated-red)](https://github.com/Octavius-Framework/octavius-postgresql)

*It's not an ORM. It's a **ROME** (Relational-Object Mapping Engine). Because all queries lead to ROME.*

</div>

---

> [!WARNING]
> **This project is legacy and is no longer actively developed.** It has been superseded by
> **[octavius-postgresql](https://github.com/Octavius-Framework/octavius-postgresql)** — a PostgreSQL driver that
> speaks Wire Protocol v3.2 directly, a client (data access layer) built on top of it, and a migrator, published
> as separate artifacts.
>
> Much of what lives here exists to work around pgjdbc — text-protocol composites, enums the library had to be
> taught about, a stateful `ResultSet`, no named parameters — and the new driver answers all of that natively.
> What was left over is the client, deliberately a much smaller thing than a port would have been.
>
> Nothing is being taken away: the released artifacts stay on Maven Central and the code below still works. But
> new work happens in `octavius-postgresql`, and new projects should start there.

## Where Everything Went

---

Nothing here was dropped for want of a home. Most of it was a workaround for pgjdbc and the new driver answers
it natively; the rest moved, and a few things changed shape enough to be worth a sentence.

| Here                                                                                                                                                      | There                                                                                                                                                                                  |
|:----------------------------------------------------------------------------------------------------------------------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SelectQueryBuilder` and the other three — `with()`, `recursive()`, `fromSubquery`, `page`, `forUpdate`, `onConflict`, `fromSelect`, `using`, `returning` | [The four builders](https://github.com/Octavius-Framework/octavius-postgresql/blob/master/docs/client/queries.md#the-four-builders), method for method                                 |
| `QueryFragment`, `withParam`, `join`                                                                                                                      | [`QueryFragment`](https://github.com/Octavius-Framework/octavius-postgresql/blob/master/docs/client/queries.md#queryfragment)                                                          |
| `dynamic_dto`                                                                                                                                             | [`dynamic_dto`](https://github.com/Octavius-Framework/octavius-postgresql/blob/master/docs/client/dynamic-dto.md) — same idea, a real composite instead of a text-protocol one         |
| `dynamic_map`                                                                                                                                             | plain `ROW(...)` — see below                                                                                                                                                           |
| `.options { }`                                                                                                                                            | [per-query converters](https://github.com/Octavius-Framework/octavius-postgresql/blob/master/docs/client/queries.md#per-query-converters) — see below                                  |
| `TypeHandler<T>`, `GlobalTypeHandler<T>`                                                                                                                  | `ResultConverter` + `ParameterConverter` — see below                                                                                                                                   |
| `@MapKey`                                                                                                                                                 | `@PgName`, and it applies in both directions                                                                                                                                           |
| `@PgEnum`, `@PgComposite`, `@DynamicallyMappable`                                                                                                         | `@PgEnumType`, `@PgCompositeType`, `@DynamicallyMappable`, scanned by [`client-scanner`](https://github.com/Octavius-Framework/octavius-postgresql/blob/master/docs/client/scanner.md) |
| `toDataObject()`, `toDataMap()`                                                                                                                           | [the same two](https://github.com/Octavius-Framework/octavius-postgresql/blob/master/docs/driver/composites-reflection.md#maps-in-and-out-todataobject-and-todatamap)                  |
| `DataMapper`, `.toListOf { map -> … }`                                                                                                                    | a `ResultConverter<Row, T>` — see below                                                                                                                                                |
| `TransactionPlan`, `TransactionStep`, `StepHandle`                                                                                                        | [Transaction Plans](https://github.com/Octavius-Framework/octavius-postgresql/blob/master/docs/client/plans.md), and every failure names its step on the exception's `path`            |
| `PgChannelListener`                                                                                                                                       | [LISTEN / NOTIFY](https://github.com/Octavius-Framework/octavius-postgresql/blob/master/docs/driver/listen-notify.md)                                                                  |
| CALL with IN/OUT/INOUT                                                                                                                                    | [Functions and Procedures](https://github.com/Octavius-Framework/octavius-postgresql/blob/master/docs/driver/functions-procedures.md)                                                  |
| bulk `unnest`                                                                                                                                             | [Bulk Writes](https://github.com/Octavius-Framework/octavius-postgresql/blob/master/docs/driver/bulk-writes.md)                                                                        |
| `DataResult`, `quoteAsPgIdentifier`, `PgTyped`/`withPgType`, `QualifiedName`, `CaseConvention`                                                            | the same names                                                                                                                                                                         |
| `flyway-integration`                                                                                                                                      | [`migrations`](https://github.com/Octavius-Framework/octavius-postgresql/blob/master/docs/migrations/README.md), which is its own migrator rather than an adapter                      |
| `spring-integration`                                                                                                                                      | [`driver-spring-integration`](https://github.com/Octavius-Framework/octavius-postgresql/blob/master/docs/driver/spring-integration.md)                                                 |
| `api` and `core`, split by platform                                                                                                                       | `pg-model` for what is multiplatform, `driver` and `client` for the rest                                                                                                               |

### `dynamic_map` was a workaround, and the workaround is gone

An anonymous `record` did come back — over the **text protocol**, which is the whole of the problem. What
arrived was strings, the per-field OIDs having been dropped on the way, and a map with no target class has
nothing else to infer a type from. So `dynamic_map` carried the OID as *data*:

```sql
CREATE TYPE public.dynamic_map_entry AS (type_oid oid, key text, raw_value text);
```

One entry per key, each stating its own type, which is what let a `TypeHandler` decode a `timestamptz` or a
custom enum instead of handing back the text. And it is exactly why the type could never be stored: those OIDs
sit in the rows, and a user-defined type's OID is not the same one after a dump and restore.

Registering types on the connection was the other road, and technically open — but it would have had to be
wired into every physical connection a pool hands out, which with HikariCP in front is not something to look
forward to maintaining.

The new driver speaks the binary protocol, so a record's fields arrive with their OIDs in the row description
and there is nothing left to work around. A `ROW(...)` in the `SELECT` clause is read as a map with its types
intact — no type to install, no `~>` operator, and no warning to attach, an anonymous record having nowhere to
rot:

```kotlin
session.createNativeQuery(
    """
    SELECT ROW(
        'id', c.id,
        'tributes', ARRAY(SELECT ROW('amount', t.amount) FROM tributes t WHERE t.citizen_id = c.id)
    ) AS r
    FROM citizens c WHERE c.id = 1
    """
).fetchFieldStrict<Map<String, Any?>>()

// {id=1, tributes=[{amount=40}, {amount=15}]}
```

The 1:N aggregation `dynamic_map` was built for works the same way, and so does the part that mattered: a
`date` comes back a `LocalDate`, a `uuid` a `Uuid`, a `timestamptz` an `Instant`. See
[the raw forms](https://github.com/Octavius-Framework/octavius-postgresql/blob/master/docs/driver/composites-reflection.md#the-raw-forms-pgcomposite-and-pgrecord).

### `QueryOptions` became one mechanism instead of five fields

`.options { }` carried five different things. Four of them are one thing now — a converter registered on a
query, ahead of the session's and discarded with it:

| `QueryOptions` field          | Now                                                                   |
|:------------------------------|:----------------------------------------------------------------------|
| `typeHandlers`                | `registerResultConverter` / `registerParameterConverter` on the query |
| `json`                        | `dynamicTypes.resultConverter(json)`, registered the same way         |
| `customCompositeMappers`      | a converter narrowed on `sourceType.name`                             |
| `returnCompositeAsMap(name)`  | `compositesAsMaps(name)`                                              |
| `returnAllCompositesAsMaps()` | `compositesAsMaps()`                                                  |

The last two are [one function](https://github.com/Octavius-Framework/octavius-postgresql/blob/master/docs/driver/composites-reflection.md#reading-them-all-as-maps)
with an overload that takes several names at once. One difference worth knowing: it claims a value only where
the caller asked for `Any` or `Map`, so naming a class in the same query still answers with the class, and a
data class is untouched below its own surface. Here the option won over everything, including a declared
property type.

### `DataMapper` became a converter keyed on `Row`

`toListOf(params, mapper)` handed the library a function and got a `List<T>` back. The same shape is a
`ResultConverter` whose source class is `Row`: `fetchObjects<T>()` hands the whole row to the converters, so
one that claims `Row` is called once per row and is exactly where the mapper stood.

```kotlin
object CitizenFromRow : ResultConverter<Row, Citizen> {
    override val supportedSourceClass = Row::class

    override fun canConvert(sourceClass: KClass<*>, expectedType: KType, sourceType: PgType, context: DeserializationContext) =
        expectedType.classifier == Citizen::class

    override fun convert(source: Row, expectedType: KType, sourceType: PgType, context: DeserializationContext) =
        Citizen(source.get("name"), source.get("province"))
}

session.createNativeQuery("SELECT name, province FROM citizens")
    .registerResultConverter(CitizenFromRow)
    .fetchObjects<Citizen>()
```

Two things it gains on the way. `forEachObject` runs through the same converter, so the mapping streams rather
than only filling a list. And the registration can go on the session's `typeManager` instead of on the query —
then `fetchObjects<Citizen>()` maps that way everywhere, which is not something a per-call mapper could say.

The reflective mapping is still there for the ordinary case: a `data class` whose properties match the columns
needs no converter at all, which is what most `DataMapper`s were written to do by hand.

### `TypeHandler` split in two, and is not scanned

One interface doing both directions became two — `ResultConverter` and `ParameterConverter` — each deciding for
itself what it claims through `canConvert`, which is what lets one narrow on the PostgreSQL type as well as on
the Kotlin one. Register on the session's `typeManager` for the old `GlobalTypeHandler`, or on a single query
for the old per-query one.

The one thing that does not carry over: **`client-scanner` does not pick them up off the classpath.** A
`TypeHandler` here was resolved from a map keyed by OID and class — one per type, no overlap, no order to get
wrong — which made scanning them safe. There, converters are consulted newest-first and a later one wins, so
registration order is the only override mechanism there is, and a classpath scan has no defined order. They are
registered by hand, in one place, on purpose:
[What It Does Not Scan](https://github.com/Octavius-Framework/octavius-postgresql/blob/master/docs/client/scanner.md#what-it-does-not-scan).

### What is gone rather than moved

- **`?` escaping for JSONB operators.** Parameters are `@name` and reach the wire as `$1`, so a `?` in the SQL
  is never anything but a `?`.
- **`disableCoreTypeInitialization`.** Nothing is created behind your back to begin with — `dynamic_dto` goes
  in a migration, or in an explicit `install()`.
- **`showBanner`.** There is no startup the library owns: you hand it a `DataSource`, `install()` is a call you
  make, and the migrator is a library rather than a command. Nowhere left to print one. 🫡

### The one thing that came with it unchanged

*It's not an ORM. It's a **ROME**.* Written here, and far too good to leave behind.

## Philosophy (The Pax Romana of Data)

*Just as Augustus brought order to a republic torn apart by the chaos of unchecked power, Octavius brings order to the chaotic republic of database interactions. The Senate of abstraction is dissolved. SQL rules supreme.*

Octavius was built to bring order to the chaotic republic of database interactions. It rejects the unpredictable "magic" of traditional ORMs and returns the power to the rightful ruler: **SQL**.

| Principle                   | Description                                                       |
|-----------------------------|-------------------------------------------------------------------|
| **Query is Imperator**      | Your SQL query dictates the shape of data — not the framework.    |
| **Object is a Vessel**      | A `data class` is simply a type-safe container for query results. |
| **Explicitness over Magic** | No lazy-loading, no session management, no dirty checking.        |

## Features

- **Fluent Query Builders** — SELECT, INSERT, UPDATE, DELETE with named parameters, subqueries, and CTE support
- **Automatic Type Mapping** — PostgreSQL `COMPOSITE`, `ENUM`, `ARRAY` and **Custom Type Handlers** (Global & Per-Query) ↔ Kotlin types
- **Dynamic Type System** — Polymorphic storage & ad-hoc object mapping with `dynamic_dto` and `dynamic_map`
- **Transaction Plans** — Multi-step atomic operations with step dependencies
- **Dynamic Filters** — Safe, composable `WHERE` clauses with `QueryFragment`
- **Stored Procedures** — CALL with automatic IN/OUT/INOUT handling, composite & array expansion
- **LISTEN / NOTIFY** — Flow-based async notifications on a dedicated connection outside the pool

Every design choice in Octavius is intentional. The reasoning behind them is laid out in the [Design Philosophy](DESIGN_PHILOSOPHY.md).

## Quick Start

```kotlin
// Define your data class — it maps directly to query results
data class Legionnaire(val id: Int, val name: String, val rank: String)

// Query with named parameters
val legionnaires = dataAccess.select("id", "name", "rank")
    .from("legions")
    .where("enlisted_year > @year")
    .orderBy("name")
    .toListOf<Legionnaire>("year" to 24)
```

## Query Builders

```kotlin
// SELECT with pagination
val senators = dataAccess.select("id", "name", "province")
    .from("senate")
    .where("active = true")
    .orderBy("appointed_at DESC")
    .limit(10)
    .offset(20)
    .toListOf<Senator>()

// INSERT with RETURNING
val newId = dataAccess.insertInto("citizens")
    .value("name")
    .value("tribe")
    .returning("id")
    .toField<Int>(mapOf("name" to "Marcus Aurelius", "tribe" to "Cornelia"))

// UPDATE with expressions
dataAccess.update("legion_supplies")
    .setExpression("quantity", "quantity - 1")
    .where("id = @id")
    .execute("id" to supplyId)

// DELETE
dataAccess.deleteFrom("expired_mandates")
    .where("expires_at < NOW()")
    .execute()
```


## Type Mapping

Automatic conversion between PostgreSQL and Kotlin types.

### Standard Types

| PostgreSQL                | Kotlin          | Notes                                            |
|---------------------------|-----------------|--------------------------------------------------|
| `int2`, `smallserial`     | `Short`         |                                                  |
| `int4`, `serial`          | `Int`           |                                                  |
| `int8`, `bigserial`       | `Long`          |                                                  |
| `float4`                  | `Float`         |                                                  |
| `float8`                  | `Double`        |                                                  |
| `numeric`                 | `BigDecimal`    |                                                  |
| `text`, `varchar`, `char` | `String`        |                                                  |
| `bool`                    | `Boolean`       |                                                  |
| `uuid`                    | `Uuid`          | `kotlin.uuid.Uuid`                               |
| `bytea`                   | `ByteArray`     |                                                  |
| `json`, `jsonb`           | `JsonElement`   | `kotlinx.serialization.json`                     |
| `void`                    | `Unit`          | Return type of void functions (e.g. `pg_notify`) |
| `date`                    | `LocalDate`     | `kotlinx.datetime` <sup>*</sup>                  |
| `time`                    | `LocalTime`     | `kotlinx.datetime`                               |
| `timestamp`               | `LocalDateTime` | `kotlinx.datetime` <sup>*</sup>                  |
| `timestamptz`             | `Instant`       | `kotlin.time` <sup>*</sup>                       |
| `interval`                | `Duration`      | `kotlin.time` <sup>*</sup>                       |

<sup>*</sup> Supports PostgreSQL infinity values (`infinity`, `-infinity`). See [Type System](docs/type-system.md#infinity-values-for-datetime) for details.

Arrays of all standard types are supported and map to `List<T>`.

### Custom Types

```kotlin
// PostgreSQL COMPOSITE TYPE → Kotlin data class
@PgComposite
data class Province(val name: String, val capital: String, val governor: String)

// PostgreSQL ENUM → Kotlin enum
@PgEnum(schema = "cursus_honorum")
enum class Magistrature { Quaestor, Aedile, Praetor, Consul, Censor }

// Works seamlessly in queries
data class Senator(val id: Int, val rank: Magistrature, val homeProvince: Province)

val senators = dataAccess.select("id", "rank", "home_province")
    .from("senate")
    .toListOf<Senator>()  // Types converted automatically
```

### Custom Type Handlers

Extend the type system for any PostgreSQL type (e.g., `circle`, `ltree`) by implementing `GlobalTypeHandler<T>`. Handlers are automatically discovered via classpath scanning.

```kotlin
object PgCircleHandler : GlobalTypeHandler<PgCircle> {
    override val pgTypeName = "circle"
    override val kotlinClass = PgCircle::class
    override val fromPgString = { s: String -> /* parse <(x,y),r> */ }
    override val toPgString = { c: PgCircle -> "<(${c.x},${c.y}),${c.radius}>" }
}
```

### Per-Query Overrides
Need to change a mapping, bypass reflection, or return a composite as a `Map` for just one specific query? Use the `.options()` block without affecting global state:

```kotlin
val results = dataAccess.select("*").from("classified_reports")
    .options { 
        registerTypeHandler(LegacyDateHandler) 
        returnCompositeAsMap("metadata")
    }
    .toListOf<Report>()
```
*See [Type System: Per-Query Configuration](docs/type-system.md#per-query-configuration-via-options-) for full details.*

## Dynamic Type System (`dynamic_dto` & `dynamic_map`)

Octavius provides a powerful bridge between PostgreSQL and Kotlin's type system using the `dynamic_dto` (JSONB-based storage) and `dynamic_map` (ad-hoc projections) composite types.
They allow you to map complex, nested, or polymorphic data on the fly without creating strict database schema types for every nested object.
These types are automatically initialized in the **`public`** schema on startup.


### 1. Ad-hoc Object Mapping

Construct Kotlin objects directly in SQL using `jsonb_build_object` — no need to define PostgreSQL COMPOSITE types. Perfect for JOINs and projections where you want nested results without schema changes.

```kotlin
@DynamicallyMappable(typeName = "citizen_profile")
@Serializable
data class CitizenProfile(val tribe: String, val rights: List<String>)

data class CitizenWithProfile(val id: Int, val name: String, val profile: CitizenProfile)

// The database packages the nested object, Octavius unpacks it. Zero boilerplate.
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

> **Why use this?** Usually, to get a citizen with their profile in one query, you'd fetch flat columns (`citizen_id`, `citizen_name`, `profile_tribe`...) 
> and manually map them, create a database VIEW or COMPOSITE. With ad-hoc mapping, you construct the nested structure directly in SQL. 
> The database does the packaging, Octavius does the unpacking — zero boilerplate.

### 2. Polymorphic Queries (Row-level)
Store different entity types in a single table and query them safely as a list of Kotlin interfaces.

```kotlin
// 1. Define a sealed interface
sealed interface MonumentRecord

@DynamicallyMappable(typeName = "inscription")
@Serializable
data class Inscription(val text: String, val lang: String) : MonumentRecord

@DynamicallyMappable(typeName = "relief")
@Serializable
data class Relief(val subject: String) : MonumentRecord

// Database: CREATE TABLE monument_records (id INT, record dynamic_dto);

// 2. Fetch directly to a list of your interface
val records = dataAccess.select("record")
    .from("monument_records")
    .toColumn<MonumentRecord>()

// Returns: [Inscription(...), Relief(...), Inscription(...)]
```

## Functions and Procedures

Octavius stays true to its SQL-first philosophy. Invoke functions and procedures directly using native PostgreSQL syntax:

```kotlin
// Functions (SELECT * FROM func)
val result = dataAccess.select("*").from("calculate_tribute(@province, @year)")
    .toField<Int>("province" to "Britannia", "year" to 43)

// Procedures (CALL proc)
val result = dataAccess.rawQuery("CALL register_conscript(@legion_id, @new_rank)")
    .toSingleStrict(
        "legion_id" to 7,
        "new_rank" to null.withPgType("text")
    )
```

## Safe Dynamic Filters

Build complex `WHERE` clauses without SQL injection risks:

```kotlin
fun buildFilters(name: String?, minRank: Int?, province: Province?) = listOfNotNull(
    name?.let { "name ILIKE @name" withParam ("name" to "%$it%") },
    minRank?.let { "rank_order >= @minRank" withParam ("minRank" to it) },
    province?.let { "home_province = @province" withParam ("province" to it) }
).join(" AND ")

val filter = buildFilters(name = "Julius", minRank = 3, province = null)
val senators = dataAccess.select("*")
    .from("senate")
    .where(filter.sql)
    .toListOf<Senator>(filter.params)
```

## Transactions

Octavius supports two powerful interaction patterns for atomic operations.

### 1. Transaction Blocks (Imperative)
The simplest way to execute multiple operations. Transactions follow a fail-fast policy: they are automatically rolled back if the block returns `DataResult.Failure` or throws an exception.

```kotlin
val result = dataAccess.transaction {
    val citizenId = insertInto("citizens")
        .value("name")
        .returning("id")
        .toField<Int>("name" to "Marcus Aurelius")
        .getOrElse { return@transaction it }

    insertInto("citizen_profiles")
        .values(listOf("citizen_id", "bio"))
        .execute("citizen_id" to citizenId, "bio" to "Stoic philosopher")
        .getOrElse { return@transaction it }

    DataResult.Success(citizenId)
}
```

### 2. Transaction Plans (Declarative)
Execute multi-step operations with complex dependencies between steps. Results from previous steps can be referenced in subsequent steps without nested callbacks or manual state management.

```kotlin
val plan = TransactionPlan()

// Step 1: Record the edict, get handle to future ID
val edictIdHandle = plan.add(
    dataAccess.insertInto("edicts")
        .values(listOf("issuer_id", "total_tribute"))
        .returning("id")
        .asStep()
        .toField<Int>(mapOf("issuer_id" to consulId, "total_tribute" to tribute))
)

// Step 2: Assign levy items using the handle
for (item in levyItems) {
    val levyItem: Map<String, Any?> = mapOf(
        "edict_id" to edictIdHandle.field(),  // Reference future value
        "province_id" to item.provinceId,
        "amount" to item.amount
    )

    plan.add(
        dataAccess.insertInto("edict_items")
            .values(levyItem)
            .asStep()
            .execute(levyItem)
    )
}

// Execute all steps in single transaction
dataAccess.executeTransactionPlan(plan)
```

## LISTEN / NOTIFY

Subscribe to PostgreSQL channels and receive real-time notifications as a Kotlin `Flow`:

```kotlin
// Send a notification
dataAccess.notify("legion_dispatch", "legion_id:VII")

// Listen on a dedicated connection (outside the HikariCP pool)
dataAccess.createChannelListener().use { listener ->
    listener.listen("legion_dispatch", "senate_decrees")

    listener.notifications()
        .collect { notification ->
            when (notification.channel) {
                "legion_dispatch" -> handleDispatch(notification.payload)
                "senate_decrees"  -> handleDecree(notification.payload)
            }
        }
}
```

Each `PgChannelListener` holds its own dedicated JDBC connection, separate from the query pool. Notifications sent inside a transaction are only delivered after commit.

## Error Handling

Octavius distinguishes between **Database Execution Errors** (returned safely) and **Fatal Developer Errors** (thrown).

- **Queries Never Throw (On Runtime Errors):** If a query is syntactically correct and reaches the database, it returns a `DataResult.Failure(error)` instead of throwing. This forces explicit handling of expected database errors like constraint violations, lock timeouts, or missing records.
- **Fail-Fast for Bugs:** If an obvious developer mistake is made (e.g., invalid SQL syntax, a missing `WHERE` clause in a `DELETE`, or a Kotlin type mapping mismatch), Octavius **throws a standard exception** (`FatalDatabaseException`). It fails fast because these errors represent broken code that should be caught and fixed during development.
- **Rich Context:** Every exception includes a `QueryContext` that provides a clean visualization of the high-level SQL, the low-level JDBC query, and the exact parameters involved (great for logging!).

```kotlin
val result = dataAccess.insertInto("citizens")
    .value("name")
    .returning("id")
    .toField<Int>("name" to "Marcus Aurelius")

result
    .onSuccess { id -> println("New citizen ID: $id") }
    .onFailure { error ->
        when (error) {
            is ConstraintViolationException -> println("Conflict in: ${error.constraintName}")
            is DataOperationException -> println("Operation failed: ${error.messageEnum}")
            is TransactionException -> println("Transient error: ${error.errorType}")
            else -> println("Database error: $error")
        }
    }
```

See [Error Handling](docs/error-handling.md) for the full exception hierarchy and debugging tips.

## Configuration

### Using Properties File

Create a `database.properties` file in `src/main/resources`:

```properties
db.url=jdbc:postgresql://localhost:5432/roma
db.username=augustus
db.password=spqr
db.schemas=public,cursus_honorum
db.packagesToScan=com.roma.domain,com.roma.dto

# Custom HikariCP settings
db.hikari.maximumPoolSize=20
db.hikari.minimumIdle=5

# Optional settings
db.setSearchPath=true
db.dynamicDtoStrategy=AUTOMATIC_WHEN_UNAMBIGUOUS
db.disableCoreTypeInitialization=false
```

Load it in your application:

```kotlin
// From properties file
val dataAccess = OctaviusDatabase.fromConfig(
    DatabaseConfig.loadFromFile("database.properties")
)
```

### Direct Configuration

```kotlin
val dataAccess = OctaviusDatabase.fromConfig(
    DatabaseConfig(
        dbUrl = "jdbc:postgresql://localhost:5432/roma",
        dbUsername = "augustus",
        dbPassword = "spqr",
        dbSchemas = listOf("public"),
        packagesToScan = listOf("com.roma.domain"),
        hikariProperties = mapOf("maximumPoolSize" to "20")
    )
)

// From existing DataSource
val dataAccess = OctaviusDatabase.fromDataSource(existingDataSource, ...)
```

## Database Migrations

Octavius provides an optional integration with [Flyway](https://flywaydb.org/) for schema migrations via the `:flyway-integration` module.

```kotlin
val dataAccess = OctaviusDatabase.fromConfig(
    config = config,
    migrationRunner = FlywayMigrationRunner.create(
        schemas = config.dbSchemas,
        baselineVersion = "1"
    )
)
```

See [Flyway Migrations](docs/configuration.md#flyway-migrations) in the configuration guide for details.

## Documentation

For detailed guides and examples, see the [full documentation](docs/README.md):

- [Configuration](docs/configuration.md) - Initialization, DatabaseConfig, Flyway (optional), core types, Type Registry scanning, JSON Configuration, DynamicDto strategy
- [Multiplatform Support](docs/multiplatform.md) - Shared DTOs, Multiplatform BigDecimal, and JS serializers
- [Lifecycle & Shutdown](docs/lifecycle-and-shutdown.md) - Proper cleanup, .use {} block, common integration patterns
- [Query Builders](docs/query-builders.md) - SELECT (FOR UPDATE), INSERT (ON CONFLICT), UPDATE, DELETE, fragments, `.options()` and builder modes
- [Functions & Procedures](docs/functions-and-procedures.md) - CALL, SELECT, IN/OUT, PgTyped resolution
- [Executing Queries](docs/executing-queries.md) - Terminal methods, DataResult matrix, iterative execution
- [Parameter Handling](docs/parameter-handling.md) - Named parameters (@), JSONB operator escaping (?), collections & flattening, unnest and bulk operations, identifiers escaping
- [Data Mapping](docs/data-mapping.md) - toDataMap(), toDataObject(), @MapKey, nested structures
- [ORM-Like Patterns](docs/orm-patterns.md) - CRUD patterns, real-world examples
- [Transactions](docs/transactions.md) - Transaction blocks, TransactionPlan, StepHandle, passing data between steps , propagation, isolation, read-only, timeouts, errors and Concurrency & Thread Safety
- [Notifications](docs/notifications.md) - LISTEN/NOTIFY, PgChannelListener, Flow-based receiving
- [Error Handling](docs/error-handling.md) - Exception hierarchy, debugging
- [Type System](docs/type-system.md) - @PgEnum, @PgComposite, @DynamicallyMappable, dynamic_map projections, dynamic data insertion, Custom Type Handlers, GlobalTypeHandler, .options() per-query configs, standard type mappings

## Architecture

| Module               | Platform      | Description                                                                            |
|----------------------|---------------|----------------------------------------------------------------------------------------|
| `api`                | Multiplatform | **Common:** Annotations & DTOs (JVM/JS). **JVM-only:** Query & Transaction interfaces. |
| `core`               | JVM           | **Zero-dependency** core engine. Pure JDBC & HikariCP.                                 |
| `spring-integration` | JVM           | Optional integration for Spring Boot (`@Transactional` support).                       |
| `flyway-integration` | JVM           | Optional migration runner integration.                                                 |

---

*This library is legacy — superseded by [octavius-postgresql](https://github.com/Octavius-Framework/octavius-postgresql).*
