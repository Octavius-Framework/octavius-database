package io.github.octaviusframework.db.core.mapping.dynamic

import io.github.octaviusframework.db.api.getOrThrow
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class DynamicMapTest : AbstractIntegrationTest() {

    @Test
    fun `should read dynamic_record as map`() {
        val result = dataAccess.rawQuery("SELECT dynamic_map('age' ~> 25, 'name' ~> 'Kacper'::text, 'active' ~> true) as rec")
            .toSingleStrict()
            .getOrThrow()

        @Suppress("UNCHECKED_CAST")
        val rec = result["rec"] as Map<String, Any?>
        
        assertEquals(25, rec["age"])
        assertEquals("Kacper", rec["name"])
        assertEquals(true, rec["active"])
    }

    @Test
    fun `should handle null values in dynamic_record`() {
        val result = dataAccess.rawQuery("SELECT dynamic_map('val1' ~> NULL::int, 'val2' ~> 'text'::text) as rec")
            .toSingleStrict()
            .getOrThrow()

        @Suppress("UNCHECKED_CAST")
        val rec = result["rec"] as Map<String, Any?>

        assertEquals(null, rec["val1"])
        assertEquals("text", rec["val2"])
    }

    @Test
    fun `should read deeply nested dynamic_map with arrays`() {
        val sql = """
            SELECT dynamic_map(
                'department' ~> 'Engineering'::text,
                'metadata' ~> dynamic_map(
                    'budget' ~> 1500000.50::numeric,
                    'is_remote' ~> true
                ),
                'tags' ~> ARRAY['backend', 'kotlin', 'insanity']::text[],
                'employees' ~> ARRAY[
                    dynamic_map('id' ~> 1, 'name' ~> 'Kacper'::text),
                    dynamic_map('id' ~> 2, 'name' ~> 'Olaf'::text)
                ]::public.dynamic_map[]
            ) as rec
        """.trimIndent()

        val result = dataAccess.rawQuery(sql)
            .toSingleStrict()
            .getOrThrow()

        @Suppress("UNCHECKED_CAST")
        val rec = result["rec"] as Map<String, Any?>

        // 1. Zwykłe wartości
        assertEquals("Engineering", rec["department"])

        // 2. Zagnieżdżona Mapa (metadata)
        @Suppress("UNCHECKED_CAST")
        val metadata = rec["metadata"] as Map<String, Any?>
        assertEquals(BigDecimal("1500000.50"), metadata["budget"])
        assertEquals(true, metadata["is_remote"])

        // 3. Zwykła tablica (tags)
        @Suppress("UNCHECKED_CAST")
        val tags = rec["tags"] as List<String>
        assertEquals(listOf("backend", "kotlin", "insanity"), tags)

        // 4. TABLICA ZAGNIEŻDŻONYCH MAP (employees) - Święty Graal!
        @Suppress("UNCHECKED_CAST")
        val employees = rec["employees"] as List<Map<String, Any?>>

        assertEquals(2, employees.size)
        assertEquals(1, employees[0]["id"])
        assertEquals("Kacper", employees[0]["name"])
        assertEquals(2, employees[1]["id"])
        assertEquals("Olaf", employees[1]["name"])
    }

}
