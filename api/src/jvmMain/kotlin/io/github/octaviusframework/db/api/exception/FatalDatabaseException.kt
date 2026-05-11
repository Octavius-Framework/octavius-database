package io.github.octaviusframework.db.api.exception

/**
 * Base class for non-recoverable database errors that are thrown immediately instead of being wrapped in [io.github.octaviusframework.db.api.DataResult].
 *
 * These exceptions typically represent developer errors (e.g., SQL syntax errors, type mapping mismatches)
 * or critical system failures that require code changes or operator intervention.
 */
abstract class FatalDatabaseException(
    message: String, queryContext: QueryContext?,
    cause: Throwable?
) : OctaviusException(message, cause, queryContext)
