package io.github.octaviusframework.db.core.exception.unit

import io.github.octaviusframework.db.api.DataResult
import io.github.octaviusframework.db.api.builder.QueryBuilder
import io.github.octaviusframework.db.api.exception.StepDependencyException
import io.github.octaviusframework.db.api.exception.StepDependencyExceptionMessage
import io.github.octaviusframework.db.api.transaction.*
import io.github.octaviusframework.db.core.builder.AbstractQueryBuilder
import io.github.octaviusframework.db.core.jdbc.JdbcTransactionProvider
import io.github.octaviusframework.db.core.jdbc.TransactionStatus
import io.github.octaviusframework.db.core.transaction.TransactionPlanExecutor
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StepDependencyUnitExceptionTest {

    private val mockProvider = mockk<JdbcTransactionProvider>()
    private lateinit var executor: TransactionPlanExecutor

    @BeforeEach
    fun setup() {
        executor = TransactionPlanExecutor(mockProvider)
    }

    @Test
    fun `should throw StepDependencyException when dependency references a non-existent column`() {
        // GIVEN
        val plan = TransactionPlan()
        
        val mockBuilder1 = mockk<AbstractQueryBuilder<*>>()
        every { mockBuilder1.toSql() } returns "SELECT 1 as id"
        
        val mockBuilder2 = mockk<AbstractQueryBuilder<*>>()
        every { mockBuilder2.toSql() } returns "SELECT 1"

        // Step 1 returns a map (row)
        val logic1: (QueryBuilder<*>, Map<String, Any?>) -> DataResult<Map<String, Any?>> = { _, _ ->
            DataResult.Success(mapOf("id" to 1))
        }
        val step1 = TransactionStep<Map<String, Any?>>(mockBuilder1, logic1, emptyMap())
        val handle1 = plan.add(step1)
        
        // Step 2 depends on a non-existent column in Step 1
        val logic2: (QueryBuilder<*>, Map<String, Any?>) -> DataResult<Int> = { _, _ ->
            DataResult.Success(1)
        }
        val fieldRef = TransactionValue.FromStep.Field<Any?>(handle1, "non_existent")
        val step2 = TransactionStep<Int>(mockBuilder2, logic2, mapOf("id" to fieldRef))
        plan.add(step2)

        every { mockProvider.execute<Map<StepHandle<*>, Any?>>(any(), any(), any(), any(), any()) } answers {
            val action = lastArg<(TransactionStatus) -> Map<StepHandle<*>, Any?>>()
            action(mockk())
        }

        // WHEN & THEN
        assertThatThrownBy {
            executor.execute(plan, TransactionPropagation.REQUIRED, IsolationLevel.READ_COMMITTED, false, null)
        }.isInstanceOf(StepDependencyException::class.java)
            .hasMessageContaining(StepDependencyExceptionMessage.COLUMN_NOT_FOUND.name)
    }
}
