package io.github.octaviusframework.db.core.builder

import io.github.octaviusframework.db.api.DataResult
import io.github.octaviusframework.db.api.exception.BadStatementException
import io.github.octaviusframework.db.api.exception.BadStatementExceptionMessage
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IterativeTransactionTest : AbstractIntegrationTest() {

    override val sqlToExecuteOnSetup: String = """
        CREATE TABLE iterative_test (id INT PRIMARY KEY);
        INSERT INTO iterative_test VALUES (1), (2), (3);
    """.trimIndent()

    @Test
    fun `should throw BadStatementException when iterative is used outside transaction with fetchSize gt 0`() {
        // fetchSize defaults to 100
        val exception = assertThrows<BadStatementException> {
            dataAccess.select("*")
                .from("iterative_test")
                .iterate()
                .forEachRow { /* do nothing */ }
        }

        assertThat(exception.messageEnum).isEqualTo(BadStatementExceptionMessage.ITERATIVE_REQUIRES_TRANSACTION)
    }

    @Test
    fun `should allow iterative outside transaction when fetchSize is 0`() {
        val ids = mutableListOf<Int>()

        val result = dataAccess.select("id")
            .from("iterative_test")
            .iterate(fetchSize = 0)
            .forEachRow { row ->
                ids.add(row["id"] as Int)
            }

        assertThat(result).isInstanceOf(DataResult.Success::class.java)
        assertThat(ids).containsExactlyInAnyOrder(1, 2, 3)
    }

    @Test
    fun `should work with iterative and fetchSize gt 0 inside transaction`() {
        val ids = mutableListOf<Int>()

        val result = dataAccess.transaction {
            select("id")
                .from("iterative_test")
                .iterate(fetchSize = 1)
                .forEachRow { row ->
                    ids.add(row["id"] as Int)
                }
        }

        assertThat(result).isInstanceOf(DataResult.Success::class.java)
        assertThat(ids).containsExactlyInAnyOrder(1, 2, 3)
    }
}
