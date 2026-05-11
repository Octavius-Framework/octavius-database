package io.github.octaviusframework.db.core.exception

import io.github.octaviusframework.db.api.DataResult
import io.github.octaviusframework.db.api.exception.BadStatementException
import io.github.octaviusframework.db.api.exception.BadStatementExceptionMessage
import io.github.octaviusframework.db.api.exception.DataOperationException
import io.github.octaviusframework.db.api.exception.DataOperationExceptionMessage
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

import org.junit.jupiter.api.Test

class StatementIntegrationTest: AbstractIntegrationTest() {

    @Test
    fun `should throw UNDEFINED_OBJECT for non-existent table`() {
        assertThatThrownBy {
            dataAccess.rawQuery("SELECT * FROM table_that_does_not_exist").execute()
        }.isInstanceOf(BadStatementException::class.java)
            .hasMessage(BadStatementExceptionMessage.UNDEFINED_OBJECT.name)
    }

    @Test
    fun `should throw UNDEFINED_OBJECT for non-existent column`() {
        assertThatThrownBy {
            dataAccess.rawQuery("SELECT non_existent_column FROM (SELECT 1 as id) dummy").execute()
        }.isInstanceOf(BadStatementException::class.java)
            .hasMessage(BadStatementExceptionMessage.UNDEFINED_OBJECT.name)
    }

    @Test
    fun `should throw SYNTAX_ERROR for malformed SQL`() {
        assertThatThrownBy {
            dataAccess.rawQuery("SELEC * FROM (SELECT 1) dummy").execute() // Typo in SELECT
        }.isInstanceOf(BadStatementException::class.java)
            .hasMessage(BadStatementExceptionMessage.SYNTAX_ERROR.name)
    }

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
    fun `should return DATA_EXCEPTION for value out of range`() {
        // big value into smallint
        val result = dataAccess.rawQuery("SELECT 99999::smallint").execute()
        
        assertThat(result).isInstanceOf(DataResult.Failure::class.java)
        val error = (result as DataResult.Failure).error as DataOperationException
        assertThat(error.messageEnum).isEqualTo(DataOperationExceptionMessage.INVALID_DATA_FORMAT)
    }
}
