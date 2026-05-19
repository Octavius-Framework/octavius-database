package io.github.octaviusframework.db.core.exception.integration

import io.github.octaviusframework.db.api.DataResult
import io.github.octaviusframework.db.api.builder.toField
import io.github.octaviusframework.db.api.exception.DataOperationException
import io.github.octaviusframework.db.api.exception.DataOperationExceptionMessage
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DataOperationIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `should return INVALID_DATA_FORMAT for division by zero`() {
        val result = dataAccess.rawQuery("SELECT 1/0").execute()
        
        assertThat(result).isInstanceOf(DataResult.Failure::class.java)
        val error = (result as DataResult.Failure).error as DataOperationException
        assertThat(error.messageEnum).isEqualTo(DataOperationExceptionMessage.INVALID_DATA_FORMAT)
    }

    @Test
    fun `should return INVALID_DATA_FORMAT for invalid integer format`() {
        val result = dataAccess.rawQuery("SELECT 'abc'::integer").execute()
        
        assertThat(result).isInstanceOf(DataResult.Failure::class.java)
        val error = (result as DataResult.Failure).error as DataOperationException
        assertThat(error.messageEnum).isEqualTo(DataOperationExceptionMessage.INVALID_DATA_FORMAT)
    }

    @Test
    fun `should return INVALID_DATA_FORMAT for value out of range`() {
        // big value into smallint
        val result = dataAccess.rawQuery("SELECT 99999::smallint").execute()
        
        assertThat(result).isInstanceOf(DataResult.Failure::class.java)
        val error = (result as DataResult.Failure).error as DataOperationException
        assertThat(error.messageEnum).isEqualTo(DataOperationExceptionMessage.INVALID_DATA_FORMAT)
    }

    @Test
    fun `should return EMPTY_RESULT for empty non nullable value`() {
        val result = dataAccess.rawQuery("SELECT 1 WHERE 1=0").toField<Int>()

        assertThat(result).isInstanceOf(DataResult.Failure::class.java)
        val error = (result as DataResult.Failure).error as DataOperationException
        assertThat(error.messageEnum).isEqualTo(DataOperationExceptionMessage.EMPTY_RESULT)
    }
}
