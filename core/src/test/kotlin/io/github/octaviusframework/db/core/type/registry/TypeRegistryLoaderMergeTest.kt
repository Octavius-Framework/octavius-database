package io.github.octaviusframework.db.core.type.registry

import io.github.octaviusframework.db.api.exception.TypeRegistryException
import io.github.octaviusframework.db.api.type.TypeHandler
import io.github.octaviusframework.db.core.jdbc.JdbcTemplate
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.KClass

class TypeRegistryLoaderMergeTest {

    private val jdbcTemplate = mockk<JdbcTemplate>()
    private val loader = TypeRegistryLoader(jdbcTemplate, emptyList(), emptyList()) { Json }
    private val searchPath = listOf("public")
    
    // OID for 'jsonb' is 3802 in PG
    private val jsonbOid = 3802
    private val nameToSchemaOid = mapOf(
        "jsonb" to mapOf("pg_catalog" to jsonbOid),
        "my_type" to mapOf("public" to 10001),
        "other_type" to mapOf("public" to 10002)
    )

    class TestType
    
    private fun createHandler(
        pgName: String, 
        ktClass: KClass<*>, 
        isDefault: Boolean = false,
        schema: String = ""
    ) = object : TypeHandler<Any> {
        override val pgTypeName = pgName
        override val pgSchema = schema
        override val kotlinClass = ktClass as KClass<Any>
        override val isDefaultForKotlinType = isDefault
        override val fromPgString: (String) -> Any = { it }
        override val toPgString: (Any) -> String = { it.toString() }
    }

    @Test
    fun `Custom(true) should override Standard handler for class`() {
        val customHandler = createHandler("jsonb", String::class, isDefault = true)
        
        val (_, handlersByClass) = loader.mergeHandlers(listOf(customHandler), searchPath, nameToSchemaOid)
        
        val handler = handlersByClass[String::class]
        assertNotNull(handler)
        assertTrue(handler!!.isDefaultForKotlinType)
        assertEquals("jsonb", handler.pgTypeName)
    }

    @Test
    fun `Custom(false) should NOT override Standard handler for class`() {
        // Standard handler for String is 'text' (OID 25)
        val customHandler = createHandler("jsonb", String::class, isDefault = false)
        
        val (_, handlersByClass) = loader.mergeHandlers(listOf(customHandler), searchPath, nameToSchemaOid)
        
        val handler = handlersByClass[String::class]
        assertNotNull(handler)
        // Should still be the standard handler (which has isDefault = true)
        assertTrue(handler!!.isDefaultForKotlinType)
        assertEquals("text", handler.pgTypeName)
    }

    @Test
    fun `Custom(true) should override Custom(false) handler for class`() {
        val customFalse = createHandler("my_type", TestType::class, isDefault = false)
        val customTrue = createHandler("other_type", TestType::class, isDefault = true)
        
        // Order in list shouldn't matter if we have different default flags
        val (_, handlersByClass) = loader.mergeHandlers(listOf(customFalse, customTrue), searchPath, nameToSchemaOid)
        
        val handler = handlersByClass[TestType::class]
        assertEquals("other_type", handler?.pgTypeName)
        assertTrue(handler?.isDefaultForKotlinType == true)
    }

    @Test
    fun `Custom(false) should NOT override Custom(true) handler for class`() {
        val customTrue = createHandler("other_type", TestType::class, isDefault = true)
        val customFalse = createHandler("my_type", TestType::class, isDefault = false)
        
        val (_, handlersByClass) = loader.mergeHandlers(listOf(customTrue, customFalse), searchPath, nameToSchemaOid, { Json })
        
        val handler = handlersByClass[TestType::class]
        assertEquals("other_type", handler?.pgTypeName)
    }

    @Test
    fun `Multiple Custom(true) for same class should throw exception`() {
        val custom1 = createHandler("my_type", TestType::class, isDefault = true)
        val custom2 = createHandler("other_type", TestType::class, isDefault = true)
        
        assertThrows<TypeRegistryException> {
            loader.mergeHandlers(listOf(custom1, custom2), searchPath, nameToSchemaOid, { Json })
        }
    }

    @Test
    fun `OID conflict between custom handlers should throw exception`() {
        val custom1 = createHandler("my_type", String::class)
        val custom2 = createHandler("my_type", Int::class)
        
        assertThrows<TypeRegistryException> {
            loader.mergeHandlers(listOf(custom1, custom2), searchPath, nameToSchemaOid) { Json }
        }
    }

    @Test
    fun `Custom handler should be allowed to override Standard OID mapping`() {
        // Overriding 'jsonb' OID with custom handler
        val custom = createHandler("jsonb", TestType::class)
        
        val (handlersByOid, _) = loader.mergeHandlers(listOf(custom), searchPath, nameToSchemaOid) { Json }

        assertEquals(custom, handlersByOid[jsonbOid])
    }
}
