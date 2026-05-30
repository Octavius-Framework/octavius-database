package io.github.octaviusframework.db.core.mapping.dynamic

import io.github.octaviusframework.db.api.builder.toSingleStrict
import io.github.octaviusframework.db.api.getOrThrow
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import io.github.octaviusframework.db.domain.test.dynamic.DynamicProfile
import io.github.octaviusframework.db.domain.test.pgtype.TestMetadata
import io.github.octaviusframework.db.domain.test.pgtype.TestStatus
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CrazyDynamicMapIntegrationTest : AbstractIntegrationTest() {

    override val packagesToScan: List<String> = listOf(
        "io.github.octaviusframework.db.domain.test.dynamic",
        "io.github.octaviusframework.db.domain.test.pgtype"
    )

    override val sqlToExecuteOnSetup: String = """
        CREATE TYPE test_status AS ENUM ('active', 'inactive', 'pending', 'not_started');
        CREATE TYPE test_priority AS ENUM ('low', 'medium', 'high', 'critical');
        CREATE TYPE test_category AS ENUM ('bug_fix', 'feature', 'enhancement', 'documentation');
        
        CREATE TYPE test_metadata AS (
            created_at TIMESTAMP,
            updated_at TIMESTAMP,
            version INT,
            tags TEXT[]
        );
        CREATE TYPE test_person AS (
            name TEXT,
            age INT,
            email TEXT,
            active BOOLEAN,
            roles TEXT[]
        );
        CREATE TYPE test_task AS (
            id INT,
            title TEXT,
            description TEXT,
            status test_status,
            priority test_priority,
            category test_category,
            assignee test_person,
            metadata test_metadata,
            subtasks TEXT[],
            estimated_hours NUMERIC
        );
        CREATE TYPE test_project AS (
            name TEXT,
            description TEXT,
            status test_status,
            team_members test_person[],
            tasks test_task[],
            metadata test_metadata,
            budget NUMERIC
        );
    """.trimIndent()

    @Test
    fun `should read absolutely insane dynamic_map with composites, enums, and nested maps`() {
        val sql = """
            SELECT dynamic_map(
                'simple_string' ~> 'insanity'::text,
                'enum_val' ~> 'active'::test_status,
                'comp_val' ~> ROW('2024-01-01 10:00:00'::timestamp, '2024-01-02 10:00:00'::timestamp, 1, ARRAY['tag1', 'tag2'])::test_metadata,
                'nested' ~> dynamic_map(
                    'enum_array' ~> ARRAY['active'::test_status, 'pending'::test_status]::test_status[],
                    'dynamic_obj' ~> dynamic_dto(
                        'profile_dto',
                        jsonb_build_object(
                            'role', 'admin',
                            'permissions', ARRAY['read', 'write', 'execute'],
                            'lastLogin', '2024-01-01T10:00:00'
                        )
                    )
                ),
                'insane_array' ~> ARRAY[
                    dynamic_map('id' ~> 1, 'status' ~> 'pending'::test_status),
                    dynamic_map('id' ~> 2, 'status' ~> 'active'::test_status)
                ]::public.dynamic_map[]
            ) as rec
        """.trimIndent()

        val result = dataAccess.rawQuery(sql)
            .toSingleStrict()
            .getOrThrow()

        @Suppress("UNCHECKED_CAST")
        val rec = result["rec"] as Map<String, Any?>

        assertEquals("insanity", rec["simple_string"])
        assertEquals(TestStatus.Active, rec["enum_val"])

        val compVal = rec["comp_val"] as TestMetadata
        assertEquals(LocalDateTime.parse("2024-01-01T10:00:00"), compVal.createdAt)
        assertEquals(LocalDateTime.parse("2024-01-02T10:00:00"), compVal.updatedAt)
        assertEquals(1, compVal.version)
        assertEquals(listOf("tag1", "tag2"), compVal.tags)

        @Suppress("UNCHECKED_CAST")
        val nested = rec["nested"] as Map<String, Any?>
        
        @Suppress("UNCHECKED_CAST")
        val enumArray = nested["enum_array"] as List<TestStatus>
        assertEquals(listOf(TestStatus.Active, TestStatus.Pending), enumArray)

        val dynamicObj = nested["dynamic_obj"] as DynamicProfile
        assertEquals("admin", dynamicObj.role)
        assertEquals(listOf("read", "write", "execute"), dynamicObj.permissions)
        assertEquals("2024-01-01T10:00:00", dynamicObj.lastLogin)

        @Suppress("UNCHECKED_CAST")
        val insaneArray = rec["insane_array"] as List<Map<String, Any?>>
        assertEquals(2, insaneArray.size)
        assertEquals(1, insaneArray[0]["id"])
        assertEquals(TestStatus.Pending, insaneArray[0]["status"])
        assertEquals(2, insaneArray[1]["id"])
        assertEquals(TestStatus.Active, insaneArray[1]["status"])

        val dontdothis = dataAccess.rawQuery("""
            WITH please AS (
               SELECT (@no).entries AS tab
            ),
            dont AS (
                SELECT tab[3].* FROM please
            )
            SELECT (SELECT raw_value::test_metadata FROM dont) AS value, (SELECT key FROM dont) as key
        """.trimIndent()).toSingleStrict("no" to result["rec"]!!).getOrThrow()

        assertEquals((dontdothis["value"]!! as TestMetadata).tags.first(), "tag1")
    }
}
