package org.octavius.data.exception


enum class ConstraintViolationExceptionMessage {
    UNIQUE_CONSTRAINT_VIOLATION,
    FOREIGN_KEY_VIOLATION,
    NOT_NULL_VIOLATION,
    CHECK_CONSTRAINT_VIOLATION,
    DATA_INTEGRITY,
    UNKNOWN
}

/**
 * Errors during SQL execution in the database related to data integrity constraints.
 */
class ConstraintViolationException(
    val messageEnum: ConstraintViolationExceptionMessage,
    val tableName: String? = null,
    val columnName: String? = null,
    val constraintName: String? = null,
    queryContext: QueryContext?,
    cause: Throwable?
) : DatabaseException(messageEnum.name, cause, queryContext, includeCauseInToString = true) {
    override fun getDetailedMessage(): String {
        return buildString {
            append("\n")
            tableName?.let { appendLine("| Table: $it") }
            columnName?.let { appendLine("| Column: $it") }
            constraintName?.let { appendLine("| Constraint: $it") }
        }
    }
}