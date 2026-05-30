package io.github.octaviusframework.db.core.mapping.dynamic

import io.github.octaviusframework.db.api.getOrThrow
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DynamicMapWriteTest : AbstractIntegrationTest() {

    @Test
    fun `should write and read simple dynamic_map`() {
        val myMap = mapOf(
            "name" to "Kacper",
            "age" to 25,
            "active" to true
        )

        // Write to DB
        dataAccess.rawQuery("CREATE TEMP TABLE test_map (data public.dynamic_map)")
            .execute()
            .getOrThrow()

        dataAccess.rawQuery("INSERT INTO test_map (data) VALUES (@map)")
            .execute(mapOf("map" to myMap))
            .getOrThrow()

        // Read back
        val result = dataAccess.rawQuery("SELECT data FROM test_map")
            .toSingleStrict()
            .getOrThrow()

        @Suppress("UNCHECKED_CAST")
        val readMap = result["data"] as Map<String, Any?>

        assertEquals("Kacper", readMap["name"])
        assertEquals(25, readMap["age"])
        assertEquals(true, readMap["active"])
    }

    @Test
    fun `should handle nulls and nested maps in roundtrip`() {
        val nestedMap = mapOf(
            "id" to 1,
            "details" to mapOf(
                "city" to "Warsaw",
                "zip" to null
            ),
            "tags" to listOf("a", "b")
        )

        val result = dataAccess.rawQuery("SELECT @map as res")
            .toSingleStrict(mapOf("map" to nestedMap))
            .getOrThrow()

        @Suppress("UNCHECKED_CAST")
        val readMap = result["res"] as Map<String, Any?>

        assertEquals(1, readMap["id"])
        
        @Suppress("UNCHECKED_CAST")
        val details = readMap["details"] as Map<String, Any?>
        assertEquals("Warsaw", details["city"])
        assertEquals(null, details["zip"])

        @Suppress("UNCHECKED_CAST")
        val tags = readMap["tags"] as List<String>
        assertEquals(listOf("a", "b"), tags)
    }

    @Test
    fun `should handle deeply nested structures with arrays of maps`() {
        val complex = mapOf(
            "users" to listOf(
                mapOf("id" to 1, "meta" to mapOf("role" to "admin")),
                mapOf("id" to 2, "meta" to mapOf("role" to "user"))
            )
        )

        val result = dataAccess.rawQuery("SELECT @complex as res")
            .toSingleStrict(mapOf("complex" to complex))
            .getOrThrow()

        @Suppress("UNCHECKED_CAST")
        val readMap = result["res"] as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val users = readMap["users"] as List<Map<String, Any?>>
        
        assertEquals(2, users.size)
        assertEquals(1, users[0]["id"])
        assertEquals("admin", (users[0]["meta"] as Map<String, Any?>)["role"])
        assertEquals(2, users[1]["id"])
        assertEquals("user", (users[1]["meta"] as Map<String, Any?>)["role"])
    }
}
