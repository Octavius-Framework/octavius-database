package org.octavius.database.type

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.octavius.data.type.QualifiedName

class QualifiedNameTest {

    @Test
    fun `test parsing simple name`() {
        val qn = QualifiedName.from("my_type")
        assertEquals("", qn.schema)
        assertEquals("my_type", qn.name)
        assertEquals(false, qn.isArray)
    }

    @Test
    fun `test parsing schema and name`() {
        val qn = QualifiedName.from("public.my_type")
        assertEquals("public", qn.schema)
        assertEquals("my_type", qn.name)
        assertEquals(false, qn.isArray)
    }

    @Test
    fun `test parsing array`() {
        val qn = QualifiedName.from("public.my_type[]")
        assertEquals("public", qn.schema)
        assertEquals("my_type", qn.name)
        assertEquals(true, qn.isArray)
    }

    @Test
    fun `test parsing quoted names with dots`() {
        val qn = QualifiedName.from("\"my.schema\".\"my.type\"")
        assertEquals("\"my.schema\"", qn.schema)
        assertEquals("\"my.type\"", qn.name)
        assertEquals("\"my.schema\".\"my.type\"", qn.toString())
    }

    @Test
    fun `test parsing mixed quoted and unquoted`() {
        val qn = QualifiedName.from("public.\"MyType\"")
        assertEquals("public", qn.schema)
        assertEquals("\"MyType\"", qn.name)
    }

    @Test
    fun `test parsing escaped quotes`() {
        val qn = QualifiedName.from("\"A\"\"B\".C")
        assertEquals("\"A\"\"B\"", qn.schema)
        assertEquals("C", qn.name)
    }

    @Test
    fun `test error on multiple dots`() {
        assertThrows(IllegalArgumentException::class.java) {
            QualifiedName.from("a.b.c")
        }
    }

    @Test
    fun `test multiple dots inside quotes are allowed`() {
        val qn = QualifiedName.from("\"a.b.c\".d")
        assertEquals("\"a.b.c\"", qn.schema)
        assertEquals("d", qn.name)
    }

    @Test
    fun `test automatic quoting of dots in fields`() {
        val qn = QualifiedName("", "my.type")
        assertEquals("\"my.type\"", qn.quote())
        
        val qn2 = QualifiedName("my.schema", "my.type")
        assertEquals("\"my.schema\".\"my.type\"", qn2.quote())
    }

    @Test
    fun `test quote function behavior`() {
        // Unquoted stays unquoted
        assertEquals("public.my_type", QualifiedName("public", "my_type").quote())
        
        // Quoted stays quoted
        assertEquals("\"Public\".\"MyType\"", QualifiedName("\"Public\"", "\"MyType\"").quote())
        
        // Mixed
        assertEquals("public.\"MyType\"", QualifiedName("public", "\"MyType\"").quote())
        
        // Arrays
        assertEquals("public.my_type[]", QualifiedName("public", "my_type", true).quote())
        assertEquals("\"Public\".\"MyType\"[]", QualifiedName("\"Public\"", "\"MyType\"", true).quote())
    }
}
