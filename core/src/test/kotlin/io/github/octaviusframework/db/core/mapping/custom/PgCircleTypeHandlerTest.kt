package io.github.octaviusframework.db.core.mapping.custom

import io.github.octaviusframework.db.api.builder.toField
import io.github.octaviusframework.db.api.getOrThrow
import io.github.octaviusframework.db.api.type.GlobalTypeHandler
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.ResultSet
import kotlin.reflect.KClass

data class PgCircle(val x: Double, val y: Double, val r: Double)

object PgCircleHandler : GlobalTypeHandler<PgCircle> {
    override val pgTypeName: String = "circle"
    override val pgSchema: String = "pg_catalog"
    override val kotlinClass: KClass<PgCircle> = PgCircle::class
    override val fromResultSet: ((ResultSet, Int) -> PgCircle?)? = null

    override val fromPgString: (String) -> PgCircle = { s ->
        val cleaned = s.replace("<", "").replace(">", "").replace("(", "").replace(")", "")
        val parts = cleaned.split(",")
        PgCircle(
            parts[0].trim().toDouble(),
            parts[1].trim().toDouble(),
            parts[2].trim().toDouble()
        )
    }

    override val toPgString: (PgCircle) -> String = { v ->
        "((${v.x},${v.y}),${v.r})"
    }
}

class PgCircleTypeHandlerTest : AbstractIntegrationTest() {

    override val packagesToScan: List<String> = listOf("io.github.octaviusframework.db.core.mapping.custom")

    @Test
    fun `should handle circle type roundtrip`() {
        val circle = PgCircle(1.0, 2.0, 3.0)

        val result = dataAccess.rawQuery("SELECT @c as c")
            .toField<PgCircle>(mapOf("c" to circle))
            .getOrThrow()

        assertEquals(circle, result)
    }

    @Test
    fun `should handle circle array roundtrip`() {
        val circles = listOf(
            PgCircle(1.0, 2.0, 3.0),
            PgCircle(4.0, 5.0, 6.5)
        )

        val resultList = dataAccess.rawQuery("SELECT @circles as c")
            .toField<List<PgCircle>>(mapOf("circles" to circles))
            .getOrThrow()

        assertEquals(circles, resultList)
    }
}
