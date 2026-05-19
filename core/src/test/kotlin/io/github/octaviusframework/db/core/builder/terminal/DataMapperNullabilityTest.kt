package io.github.octaviusframework.db.core.builder.terminal

import io.github.octaviusframework.db.api.DataResult
import io.github.octaviusframework.db.api.builder.toSingleOf
import io.github.octaviusframework.db.api.exception.DataOperationException
import io.github.octaviusframework.db.api.exception.DataOperationExceptionMessage
import io.github.octaviusframework.db.api.getOrThrow
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

data class SimpleUser(val id: Int, val name: String)

class DataMapperNullabilityTest : AbstractIntegrationTest() {
    override val scriptName: String = "init-simple-test-db.sql"

    @Test
    fun `toSingleOf with mapper should return null when no row is found and type is nullable`() {
        val result = dataAccess.select("id", "text_val")
            .from("simple_type_benchmark")
            .where("id = -1")
            .toSingleOf<SimpleUser?> { row ->
                SimpleUser(row["id"] as Int, row["text_val"] as String)
            }
            .getOrThrow()

        assertThat(result).isNull()
    }

    @Test
    fun `toSingleOf with mapper should return object when row is found and type is nullable`() {
        val result = dataAccess.select("id", "text_val")
            .from("simple_type_benchmark")
            .where("id = 1")
            .toSingleOf<SimpleUser?> { row ->
                SimpleUser(row["id"] as Int, row["text_val"] as String)
            }
            .getOrThrow()

        assertThat(result).isNotNull
        assertThat(result?.id).isEqualTo(1)
    }

    @Test
    fun `toSingleOf with mapper should fail when no row is found and type is non-nullable`() {
            val result = dataAccess.select("id", "text_val")
                .from("simple_type_benchmark")
                .where("id = -1")
                .toSingleOf<SimpleUser> { row ->
                    SimpleUser(row["id"] as Int, row["text_val"] as String)
                }
        assertThat(result).isInstanceOf(DataResult.Failure::class.java)
        val error = (result as DataResult.Failure).error as DataOperationException
        assertThat(error.messageEnum).isEqualTo(DataOperationExceptionMessage.EMPTY_RESULT)
    }
}
