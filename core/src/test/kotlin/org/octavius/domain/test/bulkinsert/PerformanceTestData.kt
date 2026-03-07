package org.octavius.domain.test.bulkinsert

import org.octavius.data.annotation.PgComposite

// Prosta data class do przechowywania naszych danych testowych
@PgComposite(name = "performance_test")
data class PerformanceTestData(val val1: Int, val val2: String)