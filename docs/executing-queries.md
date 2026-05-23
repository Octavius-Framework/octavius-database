# Executing Queries

*A legatus does not merely issue commands — he awaits dispatches confirming their execution, and must know how to respond when a messenger returns with ill tidings. Handle every result. Acknowledge every failure. The Republic depends on it.*

Once you've built a query using the [Query Builders](query-builders.md), you need to execute it and handle the results. This guide covers terminal methods, the `DataResult` pattern, and iterative execution.

## Table of Contents

- [Terminal Methods](#terminal-methods)
- [DataResult](#dataresult)
- [Working with DataResult](#working-with-dataresult)
- [Iterative Execution](#iterative-execution)
- [Asynchronous Execution (Coroutines)](#asynchronous-execution-coroutines)

---

## Terminal Methods

All query builders share common terminal methods that execute the query and return results.

### Returning Methods (`TerminalReturningMethods`)

| Method                          | Returns                               | Description                               |
|---------------------------------|---------------------------------------|-------------------------------------------|
| `toList(params)`                | `DataResult<List<Map<String, Any?>>>` | All rows as list of maps                  |
| `toSingle(params)`              | `DataResult<Map<String, Any?>?>`      | Single row as map (or null)               |
| `toSingleStrict(params)`        | `DataResult<Map<String, Any?>>`       | Single row as map (Failure if no rows)    |
| `toListOf<T>(params)`           | `DataResult<List<T>>`                 | All rows mapped to data class             |
| `toListOf<T>(params, mapper)`   | `DataResult<List<T>>`                 | All rows mapped via custom `DataMapper`   |
| `toSingleOf<T>(params)`         | `DataResult<T>`                       | Single row mapped to data class           |
| `toSingleOf<T>(params, mapper)` | `DataResult<T>`                       | Single row mapped via custom `DataMapper` |
| `toField<T>(params)`            | `DataResult<T>`                       | Single value from first column/row        |
| `toFieldStrict<T>(params)`      | `DataResult<T>`                       | Single value, always Failure if no rows   |
| `toColumn<T>(params)`           | `DataResult<List<T>>`                 | All values from first column              |
| `toSql()`                       | `String`                              | Generated SQL (no execution)              |

> **Single-row guard**: All single-row methods (`toSingle`, `toSingleStrict`, `toSingleOf`, `toField`, `toFieldStrict`) return `Failure(TOO_MANY_ROWS)` if the query returns more than one row. Use `toList`/`toColumn` for multi-row results, or add `LIMIT 1` to your query.

### Modification Methods (`TerminalModificationMethods`)

| Method            | Returns           | Description        |
|-------------------|-------------------|--------------------|
| `execute(params)` | `DataResult<Int>` | Affected row count |

### Parameter Passing

Parameters can be passed as `Map` or `vararg Pair`:

```kotlin
// Using Map
dataAccess.select("*").from("citizens")
    .where("id = @id")
    .toSingleOf<Citizen>(mapOf("id" to 123))

// Using vararg (more concise)
dataAccess.select("*").from("citizens")
    .where("id = @id")
    .toSingleOf<Citizen>("id" to 123)
```

### Custom DataMappers

While `toListOf` and `toSingleOf` use reflection by default (which is cached and optimized), you might want to map rows manually for maximum performance or custom logic. For this, you can pass a `DataMapper` to the terminal methods.

```kotlin
val citizens = dataAccess.select("*")
    .from("citizens")
    .toListOf { map ->
        Citizen(
            citizenId = map["citizen_id"] as Int,
            firstName = map["first_name"] as String,
            enrolledAt = map["enrolled_at"] as Instant
        )
    }
```

Data mappers bypass reflection entirely, making them the fastest way to construct objects from query results.

---

## DataResult

All database operations return `DataResult<T>` - a sealed class that forces explicit handling of both success and failure cases.

```kotlin
sealed class DataResult<out T> {
    data class Success<out T>(val value: T) : DataResult<T>()
    data class Failure(val error: DatabaseException) : DataResult<Nothing>()
}
```

### Why DataResult?

- **Explicit error handling** - Compiler forces you to handle failures
- **No surprise exceptions** - Database errors don't crash your app unexpectedly
- **Chainable operations** - Functional-style transformations and callbacks
- **Type safety** - Errors are always `DatabaseException` subtypes

---

## Working with DataResult

### Extension Functions

| Function        | Description                                      |
|-----------------|--------------------------------------------------|
| `map { }`       | Transform success value, leave failure unchanged |
| `onSuccess { }` | Execute action on success, return same result    |
| `onFailure { }` | Execute action on failure, return same result    |
| `getOrElse { }` | Get value or compute default from error          |
| `getOrThrow()`  | Get value or throw exception                     |

### Basic Usage

```kotlin
val result: DataResult<List<Citizen>> = dataAccess.select("*")
    .from("citizens")
    .toListOf<Citizen>()

// Pattern 1: Callbacks
result
    .onSuccess { citizens -> println("Found ${citizens.size} citizens") }
    .onFailure { error -> println("Census failed: ${error.message}") }

// Pattern 2: Transform
val names: DataResult<List<String>> = result.map { citizens ->
    citizens.map { it.name }
}

// Pattern 3: Get with default
val citizens: List<Citizen> = result.getOrElse { emptyList() }

// Pattern 4: Get or throw (careful!)
val citizens: List<Citizen> = result.getOrThrow()  // Throws on failure
```

### Null Handling via Type Parameter

Nullability is determined by the type parameter you pass:

```kotlin
// Non-nullable — Failure if citizen not found (0 rows)
val citizen: DataResult<Citizen> = dataAccess.select("*")
    .from("citizens")
    .where("id = @id")
    .toSingleOf<Citizen>("id" to citizenId)

// Nullable — Success(null) if citizen not found
val maybeCitizen: DataResult<Citizen?> = dataAccess.select("*")
    .from("citizens")
    .where("id = @id")
    .toSingleOf<Citizen?>("id" to citizenId)

// Common pattern: non-nullable + getOrThrow
val citizen: Citizen = dataAccess.select("*")
    .from("citizens")
    .where("id = @id")
    .toSingleOf<Citizen>("id" to citizenId)
    .getOrThrow()  // Guaranteed non-null

// For untyped map results, use toSingleStrict
val row: DataResult<Map<String, Any?>> = dataAccess.select("*")
    .from("citizens")
    .where("id = @id")
    .toSingleStrict("id" to citizenId)  // Failure if no rows
```

### Terminal Method Behavior Matrix

| Method               | 0 rows                                                  | non-null value   | null value, non-null `T`        | null value, nullable `T?` | >1 rows                |
|----------------------|---------------------------------------------------------|------------------|---------------------------------|---------------------------|------------------------|
| `toField<T>()`       | `T` → `Failure(EMPTY_RESULT)`<br>`T?` → `Success(null)` | `Success(value)` | `throws UNEXPECTED_NULL_VALUE`  | `Success(null)`           | `throws TOO_MANY_ROWS` |
| `toFieldStrict<T>()` | always `Failure(EMPTY_RESULT)`                          | `Success(value)` | `throws UNEXPECTED_NULL_VALUE`  | `Success(null)`           | `throws TOO_MANY_ROWS` |
| `toSingleOf<T>()`    | `T` → `Failure(EMPTY_RESULT)`<br>`T?` → `Success(null)` | `Success(obj)`   | —                               | —                         | `throws TOO_MANY_ROWS` |
| `toSingle()`         | `Success(null)`                                         | `Success(map)`   | —                               | —                         | `throws TOO_MANY_ROWS` |
| `toSingleStrict()`   | `Failure(EMPTY_RESULT)`                                 | `Success(map)`   | —                               | —                         | `throws TOO_MANY_ROWS` |
| `toColumn<T>()`      | `Success([])`                                           | `Success([...])` | `throws UNEXPECTED_NULL_VALUE`* | `Success([..., null])`    | `Success([...])`       |
| `toListOf<T>()`      | `Success([])`                                           | `Success([...])` | —                               | —                         | `Success([...])`       |
| `toList()`           | `Success([])`                                           | `Success([...])` | —                               | —                         | `Success([...])`       |

*`toColumn<T>()` checks **every** element — a single null in any row fails the entire call by throwing `UNEXPECTED_NULL_VALUE`.

**Exception Reference:**
- `EMPTY_RESULT` is part of `DataOperationException` and is returned in DataResult.Failure.
- `UNEXPECTED_NULL_VALUE` and `TOO_MANY_ROWS` are part of `TypeMappingException` and are thrown.

Key patterns:
- **Regular** (`toField`, `toSingleOf`, `toSingle`) — empty result follows nullability of `T`
- **Strict** (`toFieldStrict`, `toSingleStrict`) — empty result is always `Failure`, regardless of `T`
- **Multi-row** (`toList`, `toListOf`, `toColumn`) — empty result is always `Success(emptyList())`
- **Single-row guard** — all single-row methods fail with `TOO_MANY_ROWS` if query returns >1 row

### In Transactions

```kotlin
val result = dataAccess.transaction {
    // Option 1: Early return on error
    val citizen = select("*")
        .from("citizens")
        .where("id = @id")
        .toSingleOf<Citizen>("id" to citizenId)
        .getOrElse { error ->
            return@transaction DataResult.Failure(error)
        }

    // Option 2: Check and handle
    val insertResult = insertInto("senate_audit")
        .values(auditData)
        .execute(auditData)

    if (insertResult is DataResult.Failure) {
        return@transaction insertResult
    }

    DataResult.Success(citizen)
}
```

### Chaining Multiple Operations

```kotlin
fun getCampaignWithLegions(campaignId: Int): DataResult<CampaignWithLegions> {
    val campaignResult = dataAccess.select("*")
        .from("campaigns")
        .where("id = @id")
        .toSingleOf<Campaign>("id" to campaignId)

    return campaignResult.map { campaign ->
        val legions = dataAccess.select("*")
            .from("campaign_legions")
            .where("campaign_id = @campaignId")
            .toListOf<Legion>("campaignId" to campaignId)
            .getOrElse { return it }

        CampaignWithLegions(campaign, legions)
    }
}
```

### Common Patterns

#### getOrThrow() with Global Exception Handler

When using Spring or other frameworks with global exception handling:

```kotlin
@Service
class CitizenService(private val dataAccess: DataAccess) {

    fun getCitizen(id: Int): Citizen {
        return dataAccess.select("*")
            .from("citizens")
            .where("id = @id")
            .toSingleOf<Citizen>("id" to id)
            .getOrThrow()  // Let global handler deal with errors
    }
}

// Spring global exception handler catches DatabaseException
@ControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(DatabaseException::class)
    fun handleDatabaseError(e: DatabaseException): ResponseEntity<*> {
        logger.error(e.toString())
        return ResponseEntity.status(500).body(ErrorResponse("Database error"))
    }
}
```

#### getOrElse() for Defaults

```kotlin
// Empty list on error
val citizens = dataAccess.select("*")
    .from("citizens")
    .toListOf<Citizen>()
    .getOrElse { emptyList() }

// Null on error
val citizen = dataAccess.select("*")
    .from("citizens")
    .where("id = @id")
    .toSingleOf<Citizen>("id" to citizenId)
    .getOrElse { null }
```

---
## Iterative Execution

Process large datasets without loading everything into memory.

> **Note:** Iterative execution must be called inside a `dataAccess.transaction { }` block. If you call `.iterate(fetchSize > 0)` outside of a transaction, a `BadStatementException` (`ITERATIVE_REQUIRES_TRANSACTION`) will be thrown to prevent accidental memory leaks.

If you explicitly want to bypass this protection and allow PostgreSQL to load the entire result into memory before iterative execution, you can use `.iterate(fetchSize = 0)`.

### Usage

Both `forEachRow` and `forEachRowOf` return `DataResult<Unit>` and accept an optional `params` parameter (defaults to `emptyMap()`).

```kotlin
dataAccess.transaction {
    val result = dataAccess.select("*")
        .from("census_records")
        .where("recorded_at > @since")
        .iterate(fetchSize = 500)
        .forEachRow("since" to startDate) { row: Map<String, Any?> ->
            processCensusRow(row)
        }

    result.onFailure { error ->
        logger.error("Census stream failed: ${error.message}")
    }
}

// With data class mapping (params defaults to emptyMap())
dataAccess.transaction {
    dataAccess.select("*")
        .from("province_audit_log")
        .iterate(fetchSize = 1000)
        .forEachRowOf<AuditEntry> { entry ->
            archiveProvinceRecord(entry)
        }
        .onFailure { error ->
            logger.error("Archive failed: ${error.message}")
        }
}
```

---

## Asynchronous Execution (Coroutines)

Octavius Database is built on top of JDBC, which is fundamentally synchronous and blocking. There is no `.async()` builder that magically makes the driver non-blocking. 

If you are using Kotlin Coroutines (e.g., in Ktor or Compose) and want to execute queries without blocking the main/UI thread, you should use standard Kotlin `withContext` to offload the work to the IO dispatcher.

### Usage in Application Code

The recommended approach is to wrap the blocking database call in `withContext(Dispatchers.IO)`:

```kotlin
viewModelScope.launch {
    // 1. On UI/Main thread
    _state.update { it.copy(isLoading = true) }
    
    // 2. Switch to IO thread for blocking database operation
    val result = withContext(Dispatchers.IO) {
        dataAccess.select("*")
            .from("citizens")
            .toSingleOf<Citizen>()
    }
    
    // 3. Kotlin automatically resumes on the UI/Main thread here!
    result.onSuccess { citizen -> 
        _state.update { it.copy(data = citizen, isLoading = false) }
    }.onFailure {
        showError(it)
    }
}
```

### Extension Functions for Cleaner Code

If you find yourself writing `withContext(Dispatchers.IO)` frequently in your application layer, you can create simple extension functions in your own project:

```kotlin
suspend inline fun <reified T : Any> QueryBuilder<*>.awaitSingleOf(
    vararg params: Pair<String, Any?>
): DataResult<T> = withContext(Dispatchers.IO) {
    this@awaitSingleOf.toSingleOf<T>(*params)
}

suspend inline fun <reified T : Any> QueryBuilder<*>.awaitListOf(
    vararg params: Pair<String, Any?>
): DataResult<List<T>> = withContext(Dispatchers.IO) {
    this@awaitListOf.toListOf<T>(*params)
}
```

Which allows you to write:
```kotlin
val result = dataAccess.select("*").from("citizens").awaitListOf<Citizen>()
```

> **Warning: Transactions and Coroutines**
> Because Octavius Database uses `ThreadLocal` for transaction management, you **must not** switch threads (`withContext`) *inside* a `transaction { }` block, or you will lose the transaction context. If you need a transaction, wrap the entire transaction block inside `withContext(Dispatchers.IO)`, not the other way around:
> 
> ```kotlin
> // CORRECT
> withContext(Dispatchers.IO) {
>     dataAccess.transaction {
>         // ...
>     }
> }
> ```

## See Also

- [Query Builders](query-builders.md) - How to build queries
- [Error Handling](error-handling.md) - Exception hierarchy and debugging
- [Data Mapping](data-mapping.md) - Converting between objects and maps
- [Transactions](transactions.md) - Transaction blocks and plans
