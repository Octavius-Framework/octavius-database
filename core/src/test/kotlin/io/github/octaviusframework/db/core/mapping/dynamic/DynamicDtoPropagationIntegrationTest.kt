package io.github.octaviusframework.db.core.mapping.dynamic

import io.github.octaviusframework.db.api.builder.execute
import io.github.octaviusframework.db.api.builder.toColumn
import io.github.octaviusframework.db.api.getOrThrow
import io.github.octaviusframework.db.api.type.withPgType
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import io.github.octaviusframework.db.core.config.DynamicDtoSerializationStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DynamicDtoPropagationIntegrationTest : AbstractIntegrationTest() {
    override val packagesToScan: List<String> = listOf("io.github.octaviusframework.db.core.mapping.dynamic")
    
    override val dynamicDtoStrategy = DynamicDtoSerializationStrategy.PREFER_DYNAMIC_DTO

    override val sqlToExecuteOnSetup: String = """
        CREATE TYPE prop_inner AS (value TEXT);
        CREATE TYPE prop_outer AS (payload dynamic_dto);
        CREATE TYPE prop_outer_all_comp AS (payload prop_inner);
        CREATE TABLE prop_test (id SERIAL PRIMARY KEY, data prop_outer);
        CREATE TABLE prop_test_comp (id SERIAL PRIMARY KEY, data prop_outer_all_comp);
    """.trimIndent()

    @Test
    fun `should allow nested DynamicDto even if outer is PgTyped`() {
        val outer = PropOuter(PropInner("hello"))
        
        // Before the fix, wrapping 'outer' in PgTyped would force PropInner to NOT be a DynamicDto.
        // But PropOuter in DB expects 'payload' to be 'dynamic_dto'!
        
        dataAccess.rawQuery("INSERT INTO prop_test (data) VALUES (@data)")
            .execute("data" to outer.withPgType("prop_outer"))
            
        val result = dataAccess.select("data").from("prop_test")
            .toColumn<PropOuter>()
            .getOrThrow()
            .first()
            
        assertThat(result.payload.value).isEqualTo("hello")
    }

    @Test
    fun `should skip nested DynamicDto if nested is ALSO explicitly PgTyped`() {
        // Here we want to force 'PropInner' to be a composite 'prop_inner'
        // even though it has @DynamicallyMappable annotation.
        // We do this by wrapping it in PgTyped.
        
        val outer = PropOuterComp(payload = PropInner("world").withPgType("prop_inner"))
        
        dataAccess.rawQuery("INSERT INTO prop_test_comp (data) VALUES (@data)")
            .execute("data" to outer) // Type will be resolved from PropOuterComp annotation

        val result = dataAccess.select("data").from("prop_test_comp")
            .toColumn<PropOuterComp>()
            .getOrThrow()
            .first()

        // result.payload will be mapped back to PropInner if it's found in the registry for prop_inner
        assertThat(result.payload).isInstanceOf(PropInner::class.java)
        assertThat((result.payload as PropInner).value).isEqualTo("world")
    }
}
