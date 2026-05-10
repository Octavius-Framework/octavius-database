package io.github.octaviusframework.db.api.exception

abstract class OctaviusException(
    message: String,
    cause: Throwable? = null,
    queryContext: QueryContext? = null,
) : RuntimeException(message, cause) {
    open val includeCauseInToString: Boolean = true

    private var _queryContext: QueryContext? = queryContext
    open val queryContext: QueryContext? get() = _queryContext

    /**
     * Enriches the exception with the transaction step index.
     */
    fun withStepIndex(index: Int): OctaviusException {
        _queryContext = _queryContext?.withTransactionStep(index) ?: QueryContext(
            sql = "N/A",
            parameters = emptyMap(),
            transactionStepIndex = index
        )
        return this
    }

    /**
     * Enriches the exception with a full query context.
     */
    fun withContext(context: QueryContext): OctaviusException {
        _queryContext = context
        return this
    }

    /**
     * Subclasses can provide additional technical details here.
     */
    open fun getDetailedMessage(): String? = null

    override fun toString(): String {
        val contextStr = queryContext?.toString() ?: ""
        val detailedMsg = getDetailedMessage()?.let { "DETAILS: $it\n" } ?: ""

        val causeSection = if (includeCauseInToString) {
            val nestedError = cause?.toString() ?: "No cause available"
            """
CAUSE:
------------------------------------------------------------
$nestedError
------------------------------------------------------------
"""
        } else ""

        return """
$contextStr

------------------------------------------------------------
ERROR: ${this::class.simpleName}
MESSAGE: $message
${detailedMsg}------------------------------------------------------------
$causeSection
"""
    }
}