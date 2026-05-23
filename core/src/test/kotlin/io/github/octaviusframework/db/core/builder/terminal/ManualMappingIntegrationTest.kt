package io.github.octaviusframework.db.core.builder.terminal

import io.github.octaviusframework.db.api.DataResult
import io.github.octaviusframework.db.api.builder.toSingleOf
import io.github.octaviusframework.db.api.transaction.TransactionPlan
import io.github.octaviusframework.db.api.transaction.TransactionPlanResult
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ManualMappingIntegrationTest : AbstractIntegrationTest() {

    override val sqlToExecuteOnSetup = """
        CREATE TABLE manual_map_test (id INT, name TEXT);
        INSERT INTO manual_map_test (id, name) VALUES (1, 'Caesar'), (2, 'Pompey'), (3, 'Crassus');
    """.trimIndent()

    data class Person(val id: Int, val name: String)

    @Test
    fun `toListOf with lambda should map results correctly`() {
        val result = dataAccess.select("*").from("manual_map_test").orderBy("id ASC")
            .toListOf { map ->
                Person(id = map["id"] as Int, name = map["name"] as String)
            }

        assertTrue(result is DataResult.Success)
        val people = (result as DataResult.Success).value
        assertEquals(3, people.size)
        assertEquals(Person(1, "Caesar"), people[0])
        assertEquals(Person(2, "Pompey"), people[1])
        assertEquals(Person(3, "Crassus"), people[2])
    }

    @Test
    fun `toSingleOf with lambda should map single result correctly`() {
        val result = dataAccess.select("*").from("manual_map_test").where("id = 1")
            .toSingleOf { map ->
                Person(id = map["id"] as Int, name = map["name"] as String)
            }

        assertTrue(result is DataResult.Success)
        val person = (result as DataResult.Success).value
        assertEquals(Person(1, "Caesar"), person)
    }

    @Test
    fun `forEachRowOf with lambda should process results correctly`() {
        val people = mutableListOf<Person>()
        
        dataAccess.transaction {
            select("*").from("manual_map_test").orderBy("id ASC")
                .iterate().forEachRowOf({ map ->
                    Person(id = map["id"] as Int, name = map["name"] as String)
                }) { person ->
                    people.add(person)
                }
        }

        assertEquals(3, people.size)
        assertEquals("Caesar", people[0].name)
        assertEquals("Pompey", people[1].name)
        assertEquals("Crassus", people[2].name)
    }

    @Test
    fun `asStep with lambda should work in TransactionPlan`() {
        val plan = TransactionPlan()
        val step = dataAccess.select("*").from("manual_map_test").where("id = 2")
            .asStep().toSingleOf { map ->
                Person(id = map["id"] as Int, name = map["name"] as String)
            }
        
        val handle = plan.add(step)
        val result = dataAccess.executeTransactionPlan(plan)

        assertTrue(result is DataResult.Success)
        val person = (result as DataResult.Success<TransactionPlanResult>).value.get(handle)
        assertEquals(Person(2, "Pompey"), person)
    }
}
