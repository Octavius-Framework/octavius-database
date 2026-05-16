package io.github.octaviusframework.db.core.mapping.custom

import io.github.octaviusframework.db.api.builder.toField
import io.github.octaviusframework.db.api.getOrThrow
import io.github.octaviusframework.db.api.type.TypeHandler
import io.github.octaviusframework.db.core.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@JvmInline
value class LTree(val path: String)

object LTreeHandler : TypeHandler<LTree> {
    override val pgTypeName = "ltree"
    override val kotlinClass = LTree::class
    override val isDefaultForKotlinType = true
    override val fromPgString = { s: String -> LTree(s) }
    override val toPgString = { t: LTree -> t.path }
}

class LTreeTypeHandlerTest : AbstractIntegrationTest() {

    override val scriptName: String = "init-ltree-test.sql"

    @Test
    fun `should handle ltree type roundtrip using query options`() {
        val path = LTree("Top.Science.Astronomy")

        val result = dataAccess.rawQuery("SELECT @p as p")
            .options { registerTypeHandler(LTreeHandler) }
            .toField<LTree>(mapOf("p" to path))
            .getOrThrow()

        assertEquals(path, result)
    }

    @Test
    fun `should handle ltree array roundtrip using query options`() {
        val paths = listOf(
            LTree("Top.Science.Astronomy"),
            LTree("Top.Science.Physics")
        )

        val resultList = dataAccess.rawQuery("SELECT @paths as p")
            .options { registerTypeHandler(LTreeHandler) }
            .toField<List<LTree>>(mapOf("paths" to paths))
            .getOrThrow()

        assertEquals(paths, resultList)
    }
}
