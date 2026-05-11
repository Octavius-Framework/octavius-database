package io.github.octaviusframework.db.api.exception

enum class InitializationExceptionMessage {
    // --- Loading / Infrastructure errors ---
    INITIALIZATION_FAILED,       // General fatal error
    INITIALIZATION_CONNECTION_FAILED,           // Connection pool / JDBC connection issue
    CLASSPATH_SCAN_FAILED,       // ClassGraph issue
    DB_QUERY_FAILED,             // JDBC/SQL issue
    MIGRATION_FAILED,            // Flyway issue
}

/**
 * Exception thrown during the initialization phase of Octavius Database.
 *
 * This exception indicates a fatal configuration or infrastructure error
 * that prevents the database system from starting (e.g., connection pool failure,
 * missing database types, or migration errors).
 */
class InitializationException(
    val messageEnum: InitializationExceptionMessage,
    val details: String? = null,
    cause: Throwable? = null,
    queryContext: QueryContext? = null
) : FatalDatabaseException(messageEnum.name, queryContext, cause) {
    override fun getDetailedMessage(): String = generateDeveloperMessage(messageEnum, details)
}

private fun generateDeveloperMessage(messageEnum: InitializationExceptionMessage, details: String?): String {
    val suffix = details?.let { ": $it" } ?: ""
    return when (messageEnum) {
        InitializationExceptionMessage.INITIALIZATION_FAILED -> "Critical error: Failed to initialize database system$suffix"
        InitializationExceptionMessage.INITIALIZATION_CONNECTION_FAILED -> "Failed to establish database connection or initialize connection pool$suffix"
        InitializationExceptionMessage.CLASSPATH_SCAN_FAILED -> "Failed to scan classpath for annotations$suffix"
        InitializationExceptionMessage.DB_QUERY_FAILED -> "Failed to fetch metadata from database$suffix"
        InitializationExceptionMessage.MIGRATION_FAILED -> "Database migration failed. Check your SQL migration files$suffix"
    }
}
