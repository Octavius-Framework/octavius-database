package io.github.octaviusframework.db.core.exception.integration

import io.github.octaviusframework.db.api.exception.BadStatementException
import io.github.octaviusframework.db.api.exception.BadStatementExceptionMessage
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class BadStatementIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `should throw UNDEFINED_OBJECT for non-existent table`() {
        assertThatThrownBy {
            dataAccess.rawQuery("SELECT * FROM table_that_does_not_exist").execute()
        }.isInstanceOf(BadStatementException::class.java)
            .hasMessageContaining(BadStatementExceptionMessage.UNDEFINED_OBJECT.name)
    }

    @Test
    fun `should throw UNDEFINED_OBJECT for non-existent column`() {
        assertThatThrownBy {
            dataAccess.rawQuery("SELECT non_existent_column FROM (SELECT 1 as id) dummy").execute()
        }.isInstanceOf(BadStatementException::class.java)
            .hasMessageContaining(BadStatementExceptionMessage.UNDEFINED_OBJECT.name)
    }

    @Test
    fun `should throw SYNTAX_ERROR for malformed SQL`() {
        assertThatThrownBy {
            dataAccess.rawQuery("SELEC * FROM (SELECT 1) dummy").execute() // Typo in SELECT
        }.isInstanceOf(BadStatementException::class.java)
            .hasMessageContaining(BadStatementExceptionMessage.SYNTAX_ERROR.name)
    }
}
