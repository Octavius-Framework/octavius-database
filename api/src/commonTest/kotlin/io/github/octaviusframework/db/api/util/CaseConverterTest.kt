package io.github.octaviusframework.db.api.util

import kotlin.test.Test
import kotlin.test.assertEquals

class CaseConverterTest {

    @Test
    fun testSnakeToCamel() {
        assertEquals("userId", "user_id".toCamelCase())
        assertEquals("userAddressId", "user_address_id".toCamelCase())
    }

    @Test
    fun testCamelToSnake() {
        assertEquals("user_id", "userId".toSnakeCase())
        assertEquals("user_address_id", "userAddressId".toSnakeCase())
    }

    @Test
    fun testPascalToSnake() {
        assertEquals("user_id", "UserId".toSnakeCase())
        assertEquals("user_address_id", "UserAddressId".toSnakeCase())
    }

    @Test
    fun testAcronymsInCamelCase() {
        // Current implementation will probably fail these or produce "xmlparser"
        assertEquals("xml_parser", "XMLParser".toSnakeCase())
        assertEquals("http_client", "HTTPClient".toSnakeCase())
        assertEquals("my_http_client", "MyHTTPClient".toSnakeCase())
    }

    @Test
    fun testNumbers() {
        assertEquals("user1_id", "user1Id".toSnakeCase())
        assertEquals("v1_api", "V1Api".toSnakeCase())
    }

    @Test
    fun testMultipleUnderscores() {
        assertEquals("userId", "user__id".toCamelCase())
    }
}
