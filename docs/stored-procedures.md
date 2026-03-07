# Stored Procedures

Octavius Database supports calling PostgreSQL stored procedures (`CREATE PROCEDURE`) with full type handling for IN, OUT, and INOUT parameters.

## Table of Contents

- [Basic Usage](#basic-usage)
- [Parameter Modes](#parameter-modes)
- [Complex Parameters](#complex-parameters)
- [Advanced Execution Modes](#advanced-execution-modes)
- [How It Works](#how-it-works)
- [Functions vs Procedures](#functions-vs-procedures)
- [OUT Type Overrides](#out-type-overrides)
- [Limitations](#limitations)

---

## Basic Usage

Call a stored procedure using `dataAccess.call()`:

```kotlin
// Procedure: CREATE PROCEDURE add_numbers(IN a int4, IN b int4, OUT result int4)
val result = dataAccess.call("add_numbers")
    .executeCall("a" to 17, "b" to 25)

result.getOrThrow() // { "result" to 42 }
```

The `executeCall()` method accepts parameters as `vararg Pair` or `Map`:

```kotlin
// vararg
dataAccess.call("my_proc").executeCall("x" to 1, "y" to 2)

// Map
dataAccess.call("my_proc").executeCall(mapOf("x" to 1, "y" to 2))
```

The return type of `executeCall()` is always `DataResult<Map<String, Any?>>`, where the map contains OUT and INOUT parameter values keyed by their PostgreSQL parameter names.

---

## Parameter Modes

### IN — Input Only

Standard input parameters. Pass them in the `executeCall()` call:

```kotlin
// CREATE PROCEDURE void_proc(p_text text)
dataAccess.call("void_proc").executeCall("p_text" to "hello")
```

Procedures with no OUT parameters return an empty map.

### OUT — Output Only

Not passed by the caller. Values are returned in the result map:

```kotlin
// CREATE PROCEDURE split_text(IN input text, OUT first_half text, OUT second_half text, OUT total_len int4)
val result = dataAccess.call("split_text")
    .executeCall("input" to "abcdef")
    .getOrThrow()

result["first_half"]  // "abc"
result["second_half"] // "def"
result["total_len"]   // 6
```

### INOUT — Input and Output

Passed as input AND returned as output. The value you pass is the initial value; the procedure may modify it:

```kotlin
// CREATE PROCEDURE increment(INOUT counter int4, IN step int4)
val result = dataAccess.call("increment")
    .executeCall("counter" to 10, "step" to 3)
    .getOrThrow()

result["counter"] // 13
```

---

## Complex Parameters

All parameter types supported by the [Type System](type-system.md) work with procedures — composites, arrays, enums, and combinations thereof. Octavius automatically handles the serialization of these types into the `CALL` statement.

---

## Advanced Execution Modes

`CallQueryBuilder` extends the base `QueryBuilder` interface, meaning it supports all standard execution modes available for other queries and have all standard terminal methods.
(See [Executing Queries](executing-queries.md))

---

## How It Works

### Procedure Metadata

Octavius scans `pg_proc` at startup to discover procedure signatures (parameter names, types, modes). This metadata is stored in `TypeRegistry` and used to build the `CALL` statement automatically.

### Execution Strategy

`DatabaseCallQueryBuilder` builds the CALL SQL from procedure metadata and leverages the standard query pipeline.

The CALL statement is constructed as follows:

| Parameter Mode | SQL Fragment                     | Binding                                                                      |
|----------------|----------------------------------|------------------------------------------------------------------------------|
| **IN**         | Named placeholder (`:paramName`) | Expanded by standard pipeline (composites → `?::type`, arrays → `?::type[]`) |
| **OUT**        | `NULL::typeName` literal         | No bind — PostgreSQL requires the slot but ignores the value                 |
| **INOUT**      | Named placeholder (`:paramName`) | Same expansion as IN                                                         |

When calling `executeCall()`:
- **No OUT params:** Internal delegation to `execute()` (terminal modification method) → returns empty map.
- **Has OUT params:** Internal delegation to `toSingleStrict()` → returns OUT/INOUT values as `Map<String, Any?>`.

---

## Functions vs Procedures

PostgreSQL has two distinct callable types. Octavius handles them differently:

|                        | Procedure (`CREATE PROCEDURE`)      | Function (`CREATE FUNCTION`)                                |
|------------------------|-------------------------------------|-------------------------------------------------------------|
| **SQL syntax**         | `CALL proc(...)`                    | `SELECT * FROM func(...)`                                   |
| **Octavius API**       | `dataAccess.call("proc")`           | `dataAccess.rawQuery("SELECT * FROM func(:params)")`        |
| **OUT params in call** | Require placeholder (`NULL::type`)  | Not passed at all — they define the return columns          |
| **INOUT params**       | Passed as input, returned as output | Passed as input, returned as output                         |
| **Return value**       | `DataResult<Map<String, Any?>>`     | Use standard terminal methods (`toField`, `toListOf`, etc.) |
| **Void return**        | Empty map                           | `toField<Unit>()`                                           |

### Calling Functions

Functions are called through the standard query builders, not through `call()`:

```kotlin
// Function: CREATE FUNCTION add(IN a int, IN b int, OUT result int)
// OUT params are NOT passed — they become return columns
val result = dataAccess.rawQuery("SELECT * FROM add(:a, :b)")
    .toField<Int>("a" to 1, "b" to 2)

// Function with multiple OUT params
// CREATE FUNCTION split(IN input text, OUT left text, OUT right text)
val parts = dataAccess.rawQuery("SELECT * FROM split(:input)")
    .toSingle("input" to "abcdef")
    .getOrThrow()

parts!!["left"]  // "abc"
parts["right"]   // "def"

// Void function
dataAccess.rawQuery("SELECT pg_notify(:channel, :payload)")
    .toField<Unit>("channel" to "orders", "payload" to "new_order")
```

---

## OUT Type Overrides

Some procedures use PostgreSQL pseudo-types (`anyarray`, `anyelement`, etc.) for OUT parameters. Since PostgreSQL rejects casts to pseudo-types, the default `NULL::anyarray` would fail. Use `outTypes()` to specify the concrete type:

```kotlin
// CREATE PROCEDURE extract_elements(IN input anyarray, OUT first anyelement, OUT last anyelement)
val result = dataAccess.call("extract_elements")
    .outTypes("first" to "int4", "last" to "int4")
    .executeCall("input" to listOf(10, 20, 30))
    .getOrThrow()

result["first"] // 10
result["last"]  // 30
```

`outTypes()` accepts `vararg Pair` or `Map`. Only the overridden OUT parameters are affected — other OUT parameters keep their types from procedure metadata.

---

## Limitations

### Overloaded Procedures

PostgreSQL allows multiple procedures with the same name but different parameter signatures. Octavius **detects overloaded procedures at startup** and excludes them from `dataAccess.call()` with a warning log:

```
Overloaded procedures detected: [my_proc] — these are excluded from dataAccess.call() and can only be invoked via rawQuery()
```

To call an overloaded procedure, use `rawQuery()` instead (see below).

### Calling Procedures via rawQuery()

Any procedure — including overloaded ones — can be called through `rawQuery()`. You write the full `CALL` statement yourself, including `NULL::type` placeholders for OUT parameters:

```kotlin
// Void procedure (no OUT params) — use execute()
dataAccess.rawQuery("CALL void_proc(:p_text)")
    .execute("p_text" to "hello")

// Procedure with OUT params — use toSingle()
val result = dataAccess.rawQuery("CALL add_numbers(:a, :b, NULL::int4)")
    .toSingle("a" to 17, "b" to 25)
    .getOrThrow()

result!!["result"] // 42
```

**Best terminal methods for CALL statements:**

| Has OUT params? | Terminal method    | Return type                     |
|-----------------|--------------------|---------------------------------|
| No              | `execute()`        | `DataResult<Int>` (always 0)    |
| Yes             | `toSingleStrict()` | `DataResult<Map<String, Any?>>` |

---

## See Also

- [Query Builders](query-builders.md) - SELECT, INSERT, UPDATE, DELETE
- [Executing Queries](executing-queries.md) - Terminal methods, DataResult
- [Type System](type-system.md) - @PgEnum, @PgComposite, type mappings
- [Transactions](transactions.md) - Wrapping procedure calls in transactions
