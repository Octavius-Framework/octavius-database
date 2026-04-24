package io.github.octaviusframework.db.core.mapping.pgnative

import io.github.octaviusframework.db.api.getOrThrow
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import io.github.octaviusframework.db.domain.test.pgtype.TestPerson
import io.github.octaviusframework.db.domain.test.pgtype.TestPriority
import io.github.octaviusframework.db.domain.test.pgtype.TestProject
import io.github.octaviusframework.db.domain.test.pgtype.TestStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealPostgresDataTest: AbstractIntegrationTest() {

    override val scriptName: String = "init-complex-test-db.sql"

    override val packagesToScan: List<String> = listOf("io.github.octaviusframework.db.domain.test.pgtype")

    @Test
    fun `should convert a row with complex types into a map of correct Kotlin objects`() {
        // Given: dane są już w bazie dzięki setupowi

        // When: Używamy frameworka do pobrania danych
        val result = dataAccess.rawQuery("SELECT * FROM complex_test_data WHERE id = 1")
            .toSingleStrict()
            .getOrThrow()

        // Then: Sprawdzamy każdy przekonwertowany obiekt
        assertThat(result["simple_text"]).isEqualTo("Test \"quoted\" text with special chars: ąćęłńóśźż")
        assertThat(result["simple_bool"]).isEqualTo(true)
        assertThat(result["single_status"]).isEqualTo(TestStatus.Active)

        // Sprawdzamy tablicę enumów
        assertThat(result["status_array"] as List<*>).containsExactly(
            TestStatus.Active,
            TestStatus.Pending,
            TestStatus.NotStarted
        )

        // Sprawdzamy pojedynczy kompozyt
        val person = result["single_person"] as TestPerson
        assertThat(person.name).isEqualTo("John \"The Developer\" Doe")
        assertThat(person.age).isEqualTo(30)
        assertThat(person.roles).containsExactly("admin", "developer", "team-lead")

        // Sprawdzamy "mega" kompozyt - projekt
        val project = result["project_data"] as TestProject
        assertThat(project.name).isEqualTo("Complex \"Enterprise\" Project")
        assertThat(project.status).isEqualTo(TestStatus.Active)
        assertThat(project.teamMembers).hasSize(4)
        assertThat(project.teamMembers[0].name).isEqualTo("Project Manager")
        assertThat(project.teamMembers[3].name).isEqualTo(null)

        val firstTask = project.tasks[0]
        assertThat(firstTask.title).isEqualTo("Setup \"Development\" Environment")
        assertThat(firstTask.priority).isEqualTo(TestPriority.High)
        assertThat(firstTask.assignee.name).isEqualTo("DevOps Guy")
        assertThat(firstTask.metadata.tags).containsExactly("setup", "infrastructure", "priority")

        // Sprawdzamy tablicę projektów (zagnieżdżenie do potęgi)
        @Suppress("UNCHECKED_CAST")
        val projectArray = result["project_array"] as List<TestProject>
        assertThat(projectArray).hasSize(2)
        assertThat(projectArray[0].name).isEqualTo("Small \"Maintenance\" Project")
        assertThat(projectArray[1].tasks[0].assignee.name).isEqualTo("AI Specialist")
    }
}
