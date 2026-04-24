package io.github.octaviusframework.db.domain.test.bulkinsert

import io.github.octaviusframework.db.api.annotation.PgComposite

// Prosta data class do przechowywania naszych danych testowych
@PgComposite(name = "performance_test")
data class PerformanceTestData(val val1: Int, val val2: String)