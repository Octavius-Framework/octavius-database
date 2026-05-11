package io.github.octaviusframework.db.api.exception
/**
 * Messages for [ConstraintViolationException] representing specific database integrity rules.
 *
 * Corresponds to PostgreSQL Class 23 (Integrity Constraint Violation).
 */
enum class ConstraintViolationExceptionMessage {
    /** A duplicate value was provided for a unique column or index (PostgreSQL 23505). */
    UNIQUE_CONSTRAINT_VIOLATION,

    /** A value was provided that does not exist in the referenced table (PostgreSQL 23503). */
    FOREIGN_KEY_VIOLATION,

    /** A null value was provided for a non-nullable column (PostgreSQL 23502). */
    NOT_NULL_VIOLATION,

    /** A value was provided that fails a CHECK constraint (PostgreSQL 23514). */
    CHECK_CONSTRAINT_VIOLATION,

    /** General data integrity error, such as exclusion constraint violations (PostgreSQL 23P01). */
    DATA_INTEGRITY,
    /** A constraint violation that occurred at the end of a transaction for a deferred constraint. */
    DEFERRED_CONSTRAINT_VIOLATION
}

/**
 * Exception thrown when a database operation violates data integrity constraints.
 *
 * This is a common exception for handling domain rules enforced by the database
 * (e.g., "Email already exists" or "Referenced user not found").
 *
 * It provides metadata like [tableName], [columnName], and [constraintName] if provided by the database driver.
 */
class ConstraintViolationException(
    val messageEnum: ConstraintViolationExceptionMessage,
    val tableName: String? = null,
    val columnName: String? = null,
    val constraintName: String? = null,
    queryContext: QueryContext?,
    cause: Throwable?
) : DatabaseException(
    message = messageEnum.name,
    cause = cause,
    queryContext = queryContext
) {
    override fun getDetailedMessage(): String {
        return buildString {
            append("\n")
            appendLine("message: ${generateDeveloperMessage(messageEnum, tableName, columnName, constraintName)}")
            tableName?.let { appendLine("table: $it") }
            columnName?.let { appendLine("column: $it") }
            constraintName?.let { appendLine("constraint: $it") }
        }
    }
}

private fun generateDeveloperMessage(
    messageEnum: ConstraintViolationExceptionMessage,
    tableName: String?,
    columnName: String?,
    constraintName: String?
): String {
    val tableInfo = if (tableName != null) " in table '$tableName'" else ""
    val columnInfo = if (columnName != null) " on column '$columnName'" else ""
    val constraintInfo = if (constraintName != null) " (Constraint: '$constraintName')" else ""

    return when (messageEnum) {
        ConstraintViolationExceptionMessage.UNIQUE_CONSTRAINT_VIOLATION ->
            "Unique constraint violation$tableInfo$columnInfo. A duplicate value was provided for a unique field$constraintInfo."

        ConstraintViolationExceptionMessage.FOREIGN_KEY_VIOLATION ->
            "Foreign key violation$tableInfo$columnInfo. The referenced record does not exist$constraintInfo."

        ConstraintViolationExceptionMessage.NOT_NULL_VIOLATION ->
            "Not null violation$tableInfo$columnInfo. A null value was provided for a non-nullable field$constraintInfo."

        ConstraintViolationExceptionMessage.CHECK_CONSTRAINT_VIOLATION ->
            "Check constraint violation$tableInfo. The value does not satisfy the business rule$constraintInfo."

        ConstraintViolationExceptionMessage.DATA_INTEGRITY ->
            "Data integrity violation$tableInfo. The operation would leave the database in an inconsistent state$constraintInfo."

        ConstraintViolationExceptionMessage.DEFERRED_CONSTRAINT_VIOLATION -> "Deferred constraint violation$tableInfo$columnInfo"
    }
}
