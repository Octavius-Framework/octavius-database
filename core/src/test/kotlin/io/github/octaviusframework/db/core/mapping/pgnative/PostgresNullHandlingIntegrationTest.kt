package io.github.octaviusframework.db.core.mapping.pgnative

import io.github.octaviusframework.db.api.getOrThrow
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import io.github.octaviusframework.db.domain.test.pgtype.TestPerson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresNullHandlingIntegrationTest: AbstractIntegrationTest() {

    override val scriptName: String = "init-complex-test-db.sql"

    override val packagesToScan: List<String> = listOf("io.github.octaviusframework.db.domain.test.pgtype")

    @Test
    fun `should handle NULL values correctly in arrays and composites`() {
        // 1. Array with NULL (unquoted NULL literal)
        val arrayResult = dataAccess.rawQuery("SELECT ARRAY[1, NULL, 3]::integer[] as val")
            .toSingleStrict()
            .getOrThrow()
        
        assertThat(arrayResult["val"] as List<*>).containsExactly(1, null, 3)

        // 2. Array with quoted \"NULL\" (should be treated as string)
        val arrayQuotedResult = dataAccess.rawQuery("SELECT ARRAY['NULL', 'normal']::text[] as val")
            .toSingleStrict()
            .getOrThrow()
            
        assertThat(arrayQuotedResult["val"] as List<*>).containsExactly("NULL", "normal")

        // 3. Composite with NULL (empty field)
        val compositeResult = dataAccess.rawQuery("SELECT ROW('John', NULL, 'john@example.com', true, ARRAY['admin'])::test_person as val")
            .toSingleStrict()
            .getOrThrow()
            
        val person = compositeResult["val"] as TestPerson
        assertThat(person.name).isEqualTo("John")
        assertThat(person.age).isNull()
        assertThat(person.email).isEqualTo("john@example.com")

        // 4. Literal NULL string in composite (unquoted in text field)
        // A literal 'NULL' string in a text field is just NULL.
        val compositeLiteralResult = dataAccess.rawQuery("SELECT ROW('NULL', 30, 'null@example.com', true, ARRAY['user'])::test_person as val")
            .toSingleStrict()
            .getOrThrow()
            
        val personLiteral = compositeLiteralResult["val"] as TestPerson
        assertThat(personLiteral.name).isEqualTo("NULL")
        assertThat(personLiteral.age).isEqualTo(30)

        // 5. Nested NULLs: Composite in array with NULL fields
        val nestedResult = dataAccess.rawQuery("SELECT ARRAY[ROW('Alice', NULL, 'alice@example.com', true, ARRAY['dev']), ROW(NULL, NULL, NULL, NULL, NULL), NULL]::test_person[] as val")
            .toSingleStrict()
            .getOrThrow()
            
        @Suppress("UNCHECKED_CAST")
        val persons = nestedResult["val"] as List<TestPerson?>
        assertThat(persons).hasSize(3)
        assertThat(persons[0]!!.name).isEqualTo("Alice")
        assertThat(persons[0]!!.age).isNull()
        
        assertThat(persons[1]!!.name).isNull()
        assertThat(persons[1]!!.age).isNull()
        assertThat(persons[1]!!.email).isNull()
        assertThat(persons[1]!!.active).isNull()
        assertThat(persons[1]!!.roles).isNull()
        assertThat(persons[2]).isNull()
    }
}
