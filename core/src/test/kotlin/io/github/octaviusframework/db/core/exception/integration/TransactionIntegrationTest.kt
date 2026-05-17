package io.github.octaviusframework.db.core.exception.integration

import io.github.octaviusframework.db.api.DataResult
import io.github.octaviusframework.db.api.exception.TransactionException
import io.github.octaviusframework.db.api.exception.TransactionExceptionMessage
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

class TransactionIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `should return TransactionException on timeout`() {
        // GIVEN: Executing query that takes 2s with 1s timeout
        // PostgreSQL: SET statement_timeout = 1000;
        dataAccess.rawQuery("SET statement_timeout = 1000").execute()

        val result = dataAccess.rawQuery("SELECT pg_sleep(2)").execute()

        // THEN
        assertThat(result).isInstanceOf(DataResult.Failure::class.java)
        val failure = result as DataResult.Failure
        assertThat(failure.error).isInstanceOf(TransactionException::class.java)
        val concError = failure.error as TransactionException
        assertThat(concError.messageEnum).isEqualTo(TransactionExceptionMessage.TIMEOUT)

        // Cleanup
        dataAccess.rawQuery("SET statement_timeout = 0").execute()
    }

    @Test
    @Timeout(10)
    fun `should return TransactionException on deadlock`() = runBlocking {
        // GIVEN
        dataAccess.rawQuery("CREATE TABLE IF NOT EXISTS deadlock_test (id INT PRIMARY KEY, val TEXT)").execute()
        dataAccess.rawQuery("TRUNCATE deadlock_test").execute()
        dataAccess.rawQuery("INSERT INTO deadlock_test (id, val) VALUES (1, 'A'), (2, 'B')").execute()

        // TWO transactions locking rows in reverse order
        val deferred1 = async(Dispatchers.IO) {
            dataAccess.transaction {
                rawQuery("UPDATE deadlock_test SET val = 'T1' WHERE id = 1").execute()
                Thread.sleep(1000) // Give T2 time to lock row 2
                rawQuery("UPDATE deadlock_test SET val = 'T1' WHERE id = 2").execute()
            }
        }

        val deferred2 = async(Dispatchers.IO) {
            dataAccess.transaction {
                rawQuery("UPDATE deadlock_test SET val = 'T2' WHERE id = 2").execute()
                Thread.sleep(1000) // Give T1 time to lock row 1
                rawQuery("UPDATE deadlock_test SET val = 'T2' WHERE id = 1").execute()
            }
        }

        val res1 = deferred1.await()
        val res2 = deferred2.await()

        // One of them must fail with deadlock
        val anyDeadlock = (res1 is DataResult.Failure && (res1.error as? TransactionException)?.messageEnum == TransactionExceptionMessage.DEADLOCK) ||
                (res2 is DataResult.Failure && (res2.error as? TransactionException)?.messageEnum == TransactionExceptionMessage.DEADLOCK)

        assertThat(anyDeadlock).isTrue()
        Unit
    }
}
