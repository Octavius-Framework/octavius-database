package io.github.octaviusframework.db.core.builder

import io.github.octaviusframework.db.api.annotation.PgCompositeMapper
import io.github.octaviusframework.db.api.builder.toColumn
import io.github.octaviusframework.db.api.builder.toSingle
import io.github.octaviusframework.db.api.getOrThrow
import io.github.octaviusframework.db.api.type.TypeHandler
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import io.github.octaviusframework.db.domain.test.pgtype.TestPerson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.ResultSet
import kotlin.reflect.KClass

class QueryOptionsTest : AbstractIntegrationTest() {

    override val packagesToScan: List<String> = listOf("io.github.octaviusframework.db.domain.test.pgtype")
    override val scriptName: String = "init-complex-test-db.sql"

    @Test
    fun `should override type handler using QueryOptions`() {
        // Given
        val customIntHandler = object : TypeHandler<Int> {
            override val pgTypeName: String = "int4"
            override val kotlinClass: KClass<Int> = Int::class
            override val fromPgString: (String) -> Int = { it.toInt() + 100 }
            override val toPgString: (Int) -> String = { (it - 100).toString() }
            override val fromResultSet: (ResultSet, Int) -> Int = { rs, i -> rs.getInt(i) + 100 }
        }

        // When
        val result = dataAccess.rawQuery("SELECT 42 as val")
            .options { registerTypeHandler(customIntHandler) }
            .toSingle(emptyMap())
            .getOrThrow()

        // Then
        assertEquals(142, result!!["val"])
    }

    @Test
    fun `should override type handler for writing using QueryOptions`() {
        // Given
        val customIntHandler = object : TypeHandler<Int> {
            override val pgTypeName: String = "int4"
            override val kotlinClass: KClass<Int> = Int::class
            override val isDefaultForKotlinType: Boolean = true
            override val fromPgString: (String) -> Int = { it.toInt() }
            override val toPgString: (Int) -> String = { (it + 100).toString() }
            override val toJdbc: ((Int) -> Any) = { it + 100 }
            override val fromResultSet: (ResultSet, Int) -> Int = { rs, i -> rs.getInt(i) }
        }

        // When
        val result = dataAccess.rawQuery("SELECT @val as val")
            .options { registerTypeHandler(customIntHandler) }
            .toSingle("val" to 42)
            .getOrThrow()

        // Then
        // The value 42 should be serialized to 142 during writing
        assertEquals(142, result!!["val"])
    }

    @Test
    fun `should return composite as Map using QueryOptions`() {
        // When
        val result = dataAccess.rawQuery("SELECT ROW('Alice Smith', 25, 'alice@example.com', true, ARRAY['admin'])::test_person as person")
            .options { returnCompositeAsMap("test_person", "public") }
            .toSingle()
            .getOrThrow()

        // Then
        val person = result!!["person"]
        assertTrue(person is Map<*, *>)
        val personMap = person as Map<String, Any?>
        assertEquals("Alice Smith", personMap["name"])
        assertEquals(25, personMap["age"])
    }

    @Test
    fun `should return all composites as Maps using QueryOptions`() {
        // When
        val result = dataAccess.rawQuery("SELECT ROW('Alice Smith', 25, 'alice@example.com', true, ARRAY['admin'])::test_person as person")
            .options { returnAllCompositesAsMaps() }
            .toSingle(emptyMap())
            .getOrThrow()

        // Then
        val person = result!!["person"]
        assertTrue(person is Map<*, *>)
    }

    @Test
    fun `should use custom composite mapper using QueryOptions`() {
        // Given
        val customMapper = object : PgCompositeMapper<TestPerson> {
            override fun toDataObject(map: Map<String, Any?>): TestPerson {
                return TestPerson(
                    name = (map["name"] as String) + " (Custom)",
                    age = (map["age"] as Int) + 10,
                    email = map["email"] as String,
                    active = map["active"] as Boolean,
                    roles = map["roles"] as List<String>
                )
            }
            override fun toDataMap(obj: TestPerson): Map<String, Any?> = emptyMap()
        }

        // When
        val result = dataAccess.rawQuery("SELECT ROW('Alice Smith', 25, 'alice@example.com', true, ARRAY['admin'])::test_person as person")
            .options { registerCompositeMapper("test_person", "public", customMapper) }
            .toColumn<TestPerson>(emptyMap())
            .getOrThrow()

        // Then
        val person = result.first()
        assertEquals("Alice Smith (Custom)", person.name)
        assertEquals(35, person.age)
    }
}
