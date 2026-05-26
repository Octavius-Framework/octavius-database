package io.github.octaviusframework.db.core.mapping.standard

import io.github.octaviusframework.db.api.builder.toSingleOf
import io.github.octaviusframework.db.api.getOrThrow
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import io.github.octaviusframework.db.api.exception.TypeMappingException
import io.github.octaviusframework.db.api.exception.TypeMappingExceptionMessage

data class GenericData<T>(val id: Int, val payload: T)

class GenericDataClassMappingTest : AbstractIntegrationTest() {
    override val sqlToExecuteOnSetup: String = """
        CREATE TABLE generic_test_table (
            id INT PRIMARY KEY,
            payload TEXT
        );
        INSERT INTO generic_test_table (id, payload) VALUES (1, 'hello');
    """.trimIndent()

    @Test
    fun `mapping generic data class should fail with TypeMappingException due to type erasure`() {
        val thrown = org.assertj.core.api.Assertions.catchThrowable {
            dataAccess.select("id", "payload")
                .from("generic_test_table")
                .where("id = 1")
                .toSingleOf<GenericData<String>>()
                .getOrThrow()
        }

        org.assertj.core.api.Assertions.assertThat(thrown)
            .isInstanceOf(TypeMappingException::class.java)
        
        val exception = thrown as TypeMappingException
        org.assertj.core.api.Assertions.assertThat(exception.messageEnum)
            .isEqualTo(TypeMappingExceptionMessage.UNSUPPORTED_GENERIC_TYPE_IN_DATA_CLASS)
    }
}
