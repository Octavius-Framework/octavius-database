# Error Handling

*The Roman jurist distinguished carefully between errors of fact and errors of law, between fatal defects in a contract and minor irregularities that could be remedied. Octavius makes the same distinction: some errors are fatal and must halt execution immediately, while others are returned safely so the calling code may decide how to proceed.*

## Table of Contents

- [Exception Hierarchy](#exception-hierarchy)
- [Fatal Developer Errors (Thrown)](#fatal-developer-errors-thrown)
  - [BadStatementException](#badstatementexception)
  - [TypeMappingException](#typemappingexception)
  - [TypeRegistryException](#typeregistryexception)
  - [StepDependencyException](#stepdependencyexception)
  - [InitializationException](#initializationexception)
- [Database Execution Errors (Returned)](#database-execution-errors-returned)
  - [ConstraintViolationException](#constraintviolationexception)
  - [DataOperationException](#dataoperationexception)
  - [TransactionException](#transactionexception)
  - [ConnectionException](#connectionexception)
  - [UnknownDatabaseException](#unknowndatabaseexception)
- [Exception Enrichment](#exception-enrichment)
- [Logging and Debugging](#logging-and-debugging)

---

Octavius Database divides errors into two primary categories:

* **Database Execution (Returned):** These inherit from `DatabaseException`. If a query is syntactically correct and reaches the database, it **never throws**. Issues like constraint violations, transient connection losses, or timeouts are returned as `DataResult.Failure<DatabaseException>`. This forces explicit error handling for expected runtime conditions.
* **Fatal Developer Errors (Thrown):** These inherit from `FatalDatabaseException`. If an obvious mistake is made (like invalid SQL syntax, a missing `WHERE` clause in `DELETE`, or a type mapping mismatch), Octavius **throws a standard exception**. It fails fast because these errors typically require code changes to fix and should be caught during development.

> **Working with DataResult**: For `DataResult` usage patterns (`map`, `onSuccess`, `getOrElse`, etc.), see [Executing Queries](executing-queries.md#dataresult).

## Exception Hierarchy

All exceptions in Octavius inherit from the sealed base class `OctaviusException`. This hierarchy separates errors that are part of the normal flow (returned in `DataResult`) from those that indicate broken code or configuration (thrown immediately).

```
OctaviusException (sealed)
├── DatabaseException (sealed) [Returned in DataResult]
│   ├── ConstraintViolationException - Data integrity (unique, FK, check)
│   ├── DataOperationException       - EMPTY_RESULT, PERMISSION_DENIED, etc.
│   ├── TransactionException         - Deadlocks, timeouts, and serialization failures
│   ├── ConnectionException          - Infrastructure and connectivity issues
│   └── UnknownDatabaseException     - Fallback for unrecognized errors
│
└── FatalDatabaseException [Thrown immediately]
    ├── BadStatementException        - SQL syntax, missing clauses, param mismatches
    ├── TypeMappingException         - Kotlin <-> PostgreSQL mapping and conversion failures
    ├── TypeRegistryException        - Type registry and schema validation errors
    ├── StepDependencyException      - Invalid references in Transaction Plans
    └── InitializationException      - Startup and configuration failures
```

---

## Fatal Developer Errors (Thrown)

These exceptions indicate that something is fundamentally wrong with the query or the application setup. They bypass `DataResult` to ensure they are noticed immediately during development.

### BadStatementException

Thrown when a query is semantically or syntactically invalid. This covers both framework-level validation (e.g., missing `WHERE`) and PostgreSQL syntax errors (Class 42).

| Enum Value (`BadStatementExceptionMessage`) | Description                                                                       |
|---------------------------------------------|-----------------------------------------------------------------------------------|
| `MISSING_CLAUSE`                            | Mandatory clause (like `WHERE` in `DELETE`) is missing                            |
| `MISSING_PARAMETERS`                        | Named parameters defined in SQL are missing in the builder                        |
| `DUPLICATE_PARAMETERS`                      | Same parameter name used multiple times with conflicting definitions              |
| `INVALID_TRANSACTION_STATE`                 | Operation attempted in a state not allowed (e.g., write in read-only transaction) |
| `INVALID_STATEMENT_STATE`                   | Query builder is in an inconsistent state for the requested operation             |
| `SYNTAX_ERROR`                              | SQL syntax error. The statement is malformed (SQLSTATE Class 42)                  |
| `UNDEFINED_OBJECT`                          | Table, column, or function does not exist (SQLSTATE Class 42)                     |
| `DUPLICATE_OBJECT`                          | Database object already exists (SQLSTATE Class 42)                                |
| `AMBIGUOUS_OBJECT`                          | Ambiguous reference to a database object (SQLSTATE Class 42)                      |
| `DATA_TYPE_ERROR`                           | Data type mismatch or invalid coercion (SQLSTATE Class 42)                        |
| `INVALID_DEFINITION`                        | Invalid object definition or schema mismatch                                      |
| `UNSUPPORTED_FEATURE`                       | The requested feature is not supported by the current database provider           |

```kotlin
// This throws BadStatementException immediately
dataAccess.deleteFrom("citizens")
    .execute() // Fails: missing .where()
```

### TypeMappingException

Thrown when data cannot be mapped between PostgreSQL and Kotlin objects. This usually indicates a mismatch between your SQL result set and your Kotlin data models.

| Enum Value (`TypeMappingExceptionMessage`) | Description                                                                                                    |
|--------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `VALUE_CONVERSION_FAILED`                  | Cannot convert value to target type                                                                            |
| `ENUM_CONVERSION_FAILED`                   | Database enum string does not match any entry in the Kotlin enum class                                         |
| `OBJECT_MAPPING_FAILED`                    | Critical failure during instantiation of a data class or object mapping                                        |
| `MISSING_REQUIRED_PROPERTY`                | A required (non-null) property in a data class was missing from the query result                               |
| `UNEXPECTED_NULL_VALUE`                    | A non-nullable Kotlin field received a NULL value from the database                                            |
| `TOO_MANY_ROWS`                            | Query returned multiple rows but only a single row was expected                                                |
| `UNSUPPORTED_COMPONENT_TYPE_IN_ARRAY`      | Native JDBC arrays do not support complex types; use `List<T>` instead                                         |
| `INVALID_DYNAMIC_DTO_FORMAT`               | The internal format of a `dynamic_dto` column is invalid or corrupted                                          |
| `INCOMPATIBLE_COLLECTION_ELEMENT_TYPE`     | An element within a collection has an incorrect type                                                           |
| `INCOMPATIBLE_TYPE`                        | General type mismatch during mapping                                                                           |
| `JSON_DESERIALIZATION_FAILED`              | Failed to deserialize JSON for dynamic type                                                                    |
| `JSON_SERIALIZATION_FAILED`                | Failed to serialize object to JSON format (ensure `@Serializable` is present)                                  |
| `COMPOSITE_MAPPER_FAILED`                  | Custom `PgCompositeMapper` implementation threw an exception                                                   |
| `UNSUPPORTED_GENERIC_TYPE_IN_DATA_CLASS`   | Data classes with generic type parameters are not supported for automatic mapping                              |

### TypeRegistryException

Thrown when there is a mismatch or missing definition in the internal type registry. The registry maintains mappings for PostgreSQL enums and composite types.


| Enum Value (`TypeRegistryExceptionMessage`) | Description                                                                        |
|---------------------------------------------|------------------------------------------------------------------------------------|
| `WRONG_FIELD_NUMBER_IN_COMPOSITE`           | Composite type in DB has a different number of fields than defined in the registry |
| `PG_TYPE_NOT_FOUND`                         | Runtime lookup failed. PostgreSQL type was not found in the loaded registry        |
| `KOTLIN_CLASS_NOT_MAPPED`                   | Class lacks `@PgEnum` or `@PgComposite` annotation or was not scanned              |
| `DYNAMIC_TYPE_NOT_FOUND`                    | No registered `@DynamicallyMappable` class found for the given key                 |
| `TYPE_DEFINITION_MISSING_IN_DB`             | Code has `@PgType` definition, but the database is missing `CREATE TYPE`           |
| `DUPLICATE_PG_TYPE_DEFINITION`              | Duplicate or collision between `@PgEnum` and `@PgComposite` names                  |
| `DUPLICATE_DYNAMIC_TYPE_DEFINITION`         | Conflict between two `@DynamicallyMappable` names (keys)                           |

### StepDependencyException

Thrown when a `TransactionPlan` contains invalid step references or data dependencies.

| Enum Value (`StepDependencyExceptionMessage`) | Description                                                    |
|-----------------------------------------------|----------------------------------------------------------------|
| `DEPENDENCY_ON_FUTURE_STEP`                   | Step attempts to use a result from a future or current step    |
| `UNKNOWN_STEP_HANDLE`                         | Found a handle that doesn't exist in the plan                  |
| `ROW_INDEX_OUT_OF_BOUNDS`                     | Referenced row index is outside the bounds of the step result  |
| `RESULT_NOT_LIST`                             | Expected the result of a step to be a List, but it was not     |
| `RESULT_NOT_MAP_LIST`                         | Expected result to be a List of Maps, but its elements are not |
| `INVALID_ROW_ACCESS_ON_NON_LIST`              | Attempted to use `row(n)` on a non-list result                 |
| `COLUMN_NOT_FOUND`                            | The requested column name was not found in the step result     |
| `SCALAR_NOT_FOUND`                            | Step result does not contain a scalar value (from `toField`)   |
| `TRANSFORMATION_FAILED`                       | Custom `.map { }` transformation for a step result failed      |

### InitializationException

Thrown during the startup of `OctaviusDatabase` when the system fails to validate its configuration or connect to the database.

| Enum Value (`InitializationExceptionMessage`) | Description                                                              |
|-----------------------------------------------|--------------------------------------------------------------------------|
| `INITIALIZATION_FAILED`                       | General fatal error during system startup                                |
| `INITIALIZATION_CONNECTION_FAILED`            | Failed to establish database connection or initialize connection pool    |
| `CLASSPATH_SCAN_FAILED`                       | Failed to scan classpath for annotations                                 |
| `DB_QUERY_FAILED`                             | Failed to fetch metadata from database (e.g., table or type definitions) |
| `MIGRATION_FAILED`                            | Database migration failed (e.g., via Flyway integration)                 |

---

## Database Execution Errors (Returned)

These errors represent runtime conditions that your application should handle gracefully via `DataResult`.

### ConstraintViolationException

Returned when an operation violates data integrity rules in the database (SQLSTATE Class 23).

| Enum Value (`ConstraintViolationExceptionMessage`) | Description                                           |
|----------------------------------------------------|-------------------------------------------------------|
| `UNIQUE_CONSTRAINT_VIOLATION`                      | Duplicate value for a unique constraint               |
| `FOREIGN_KEY_VIOLATION`                            | Referenced record does not exist                      |
| `NOT_NULL_VIOLATION`                               | Null value for a non-nullable column                  |
| `CHECK_CONSTRAINT_VIOLATION`                       | Value violates a business rule (CHECK)                |
| `DATA_INTEGRITY`                                   | General data integrity or exclusion violation         |
| `DEFERRED_CONSTRAINT_VIOLATION`                    | Violation of a deferred constraint at transaction end |

### DataOperationException

Returned for operations that are syntactically correct but fail due to runtime data conditions or permissions.

| Enum Value (`DataOperationExceptionMessage`) | Description                                                         |
|----------------------------------------------|---------------------------------------------------------------------|
| `EMPTY_RESULT`                               | No rows found when at least one was required by the terminal method |
| `PERMISSION_DENIED`                          | Access denied for the current database user                         |
| `INVALID_DATA_FORMAT`                        | Input value is incompatible with the database type                  |

### TransactionException

Returned during transaction-related conflicts or lifecycle failures. These errors are typically transient and may succeed if the operation is retried.

| Enum Value (`TransactionExceptionMessage`) | Description                                                          |
|--------------------------------------------|----------------------------------------------------------------------|
| `TIMEOUT`                                  | Transaction or statement timeout exceeded                            |
| `DEADLOCK`                                 | A deadlock was detected and this transaction rolled back             |
| `SERIALIZATION_FAILURE`                    | Concurrent updates prevented transaction serialization               |
| `TRANSACTION_ROLLBACK`                     | The transaction was rolled back by the database for internal reasons |

### ConnectionException

Returned when the library cannot establish or maintain a connection with the database during query execution.

| Description                                                                                                 |
|-------------------------------------------------------------------------------------------------------------|
| Infrastructure and connectivity issues that occur while the application is already running.                 |
| This includes pool exhaustion, network timeouts, or sudden database server shutdowns.                       |
| For connection failures during **initialization**, see [InitializationException](#initializationexception). |

### UnknownDatabaseException

A generic fallback exception used for database errors that do not fit into specific categories or for unrecognized SQLSTATE codes.

---

## Exception Enrichment

Every `OctaviusException` contains a `QueryContext` when it is available. This object stores:
- **High-level SQL**: The template SQL with `@param` placeholders.
- **Parameters**: The actual values provided to the query.
- **Low-level SQL**: The real SQL sent to JDBC (with `?`).
- **Step Index**: When inside a `TransactionPlan`, the index of the step that failed.

The `QueryContext` is automatically included in the exception's `toString()` output.

---

## Logging and Debugging

### Just Use toString()

All Octavius exceptions have a standardized `toString()` override that prints the full context (via `QueryContext`), error details, and the underlying cause chain.

```kotlin
result.onFailure { error ->
    logger.error(error.toString()) // Prints the full context automatically
}
```

### Typical Output Format

Octavius uses simple line separators to clearly separate the execution context from the error details.

```text
================================================================================
DATABASE EXECUTION CONTEXT
================================================================================
HIGH-LEVEL SQL:
INSERT INTO citizens (name, tribe) VALUES (@name, @tribe)
--------------------------------------------------------------------------------
PARAMETERS:
name = Marcus Tullius
tribe = Cornelia
--------------------------------------------------------------------------------
DATABASE-LEVEL SQL (SENT TO DB):
INSERT INTO citizens (name, tribe) VALUES (?, ?)
--------------------------------------------------------------------------------
DATABASE-LEVEL PARAMETERS:
Marcus Tullius
Cornelia
================================================================================

------------------------------------------------------------
ERROR: ConstraintViolationException
MESSAGE: UNIQUE_CONSTRAINT_VIOLATION
DETAILS: 
message: Unique constraint violation in table 'citizens'.
table: citizens
column: name
constraint: citizens_name_tribe_key
------------------------------------------------------------
CAUSE: 
------------------------------------------------------------
org.postgresql.util.PSQLException: ERROR: duplicate key value ...
------------------------------------------------------------
```

---

## See Also

- [Executing Queries](executing-queries.md) - DataResult patterns and usage
- [Transactions](transactions.md) - Transaction execution and rollback logic
- [Type System](type-system.md) - How custom types are mapped and registered
