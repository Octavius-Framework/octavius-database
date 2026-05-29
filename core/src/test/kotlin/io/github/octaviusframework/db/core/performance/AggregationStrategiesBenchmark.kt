package io.github.octaviusframework.db.core.performance

import com.zaxxer.hikari.HikariDataSource
import io.github.octaviusframework.db.api.DataAccess
import io.github.octaviusframework.db.api.builder.toColumn
import io.github.octaviusframework.db.api.getOrThrow
import io.github.octaviusframework.db.core.OctaviusDatabase
import io.github.octaviusframework.db.core.config.DatabaseConfig
import io.github.octaviusframework.db.core.jdbc.DefaultJdbcTransactionProvider
import io.github.octaviusframework.db.core.jdbc.JdbcTemplate
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AggregationStrategiesBenchmark {

    private val ITERATIONS = 5

    private val flatJoinResults = ConcurrentHashMap<Int, MutableList<Long>>()
    private val dynamicMapResults = ConcurrentHashMap<Int, MutableList<Long>>()

    private lateinit var dataSource: HikariDataSource
    private lateinit var dataAccess: DataAccess

    companion object {
        @JvmStatic
        fun userCountsProvider(): List<Int> = listOf(1000, 5000, 10000, 20000)
    }

    @BeforeAll
    fun setup() {
        println("--- INITIALIZING AGGREGATION BENCHMARK ---")

        val databaseConfig = DatabaseConfig.loadFromFile("test-database.properties")
        val hikariDataSource = HikariDataSource().apply {
            jdbcUrl = databaseConfig.dbUrl
            username = databaseConfig.dbUsername
            password = databaseConfig.dbPassword
            maximumPoolSize = 5
        }
        this.dataSource = hikariDataSource

        val jdbcTemplate = JdbcTemplate(DefaultJdbcTransactionProvider(hikariDataSource))
        jdbcTemplate.execute("""
            DROP TABLE IF EXISTS performance_orders CASCADE;
            DROP TABLE IF EXISTS performance_users CASCADE;
            
            CREATE TABLE performance_users (
                id SERIAL PRIMARY KEY,
                username TEXT NOT NULL,
                created_at TIMESTAMP NOT NULL
            );
            
            CREATE TABLE performance_orders (
                id SERIAL PRIMARY KEY,
                user_id INT NOT NULL REFERENCES performance_users(id),
                total NUMERIC NOT NULL,
                status TEXT NOT NULL
            );
        """.trimIndent())

        this.dataAccess = OctaviusDatabase.fromDataSource(
            dataSource,
            packagesToScan = emptyList(),
            dbSchemas = listOf("public")
        )

        val maxUsers = userCountsProvider().maxOrNull() ?: 20000
        val maxOrders = maxUsers * 10
        println("Generating $maxUsers users and $maxOrders orders (10 per user)...")
        
        jdbcTemplate.execute("""
            INSERT INTO performance_users (username, created_at)
            SELECT 'user_' || i, now() - (i || ' seconds')::interval
            FROM generate_series(1, $maxUsers) s(i);

            INSERT INTO performance_orders (user_id, total, status)
            SELECT ((i - 1) % $maxUsers) + 1, (i % 500) + 0.99, 'completed'
            FROM generate_series(1, $maxOrders) s(i);
        """.trimIndent())

        println("--- WARM-UP RUN (500 users) ---")
        runQueries(500)
        println("--- WARM-UP COMPLETE ---")
    }

    @ParameterizedTest(name = "Benchmark for {0} Users (and {0}0 Orders)")
    @MethodSource("userCountsProvider")
    @Order(1)
    fun runBenchmark(userCount: Int) {
        println("\n--- RUNNING BENCHMARK FOR $userCount USERS (${userCount}0 orders) (x$ITERATIONS) ---")

        flatJoinResults.putIfAbsent(userCount, mutableListOf())
        dynamicMapResults.putIfAbsent(userCount, mutableListOf())

        for (i in 1..ITERATIONS) {
            val timings = runQueries(userCount)
            flatJoinResults[userCount]!!.add(timings[0])
            dynamicMapResults[userCount]!!.add(timings[1])
        }
    }

    private fun runQueries(limitUsers: Int): LongArray {
        val timings = LongArray(2)

        // 1. FLAT JOIN + KOTLIN GROUPING
        val sqlFlat = """
            SELECT u.id as user_id, u.username, u.created_at, 
                   o.id as order_id, o.total, o.status
            FROM (SELECT * FROM performance_users LIMIT $limitUsers) u
            LEFT JOIN performance_orders o ON o.user_id = u.id
        """.trimIndent()
        
        timings[0] = measureTimeMillis {
            val rows = dataAccess.rawQuery(sqlFlat).toList().getOrThrow()
            
            // App-side aggregation
            val grouped = rows.groupBy { it["user_id"] }.map { (userId, userRows) ->
                val first = userRows.first()
                mapOf(
                    "user_id" to userId,
                    "username" to first["username"],
                    "created_at" to first["created_at"],
                    "orders" to userRows.mapNotNull { row ->
                        if (row["order_id"] != null) {
                            mapOf(
                                "order_id" to row["order_id"],
                                "total" to row["total"],
                                "status" to row["status"]
                            )
                        } else null
                    }
                )
            }
            if (grouped.isEmpty()) throw IllegalStateException("Empty result")
        }

        // 2. DB-SIDE DYNAMIC MAP AGGREGATION
        val sqlAgg = """
            SELECT dynamic_map(
                'user_id' ~> u.id,
                'username' ~> u.username,
                'created_at' ~> u.created_at,
                'orders' ~> array_agg(
                    dynamic_map('order_id' ~> o.id, 'total' ~> o.total, 'status' ~> o.status)
                ) FILTER (WHERE o.id IS NOT NULL)
            ) as rec
            FROM (SELECT * FROM performance_users LIMIT $limitUsers) u
            LEFT JOIN performance_orders o ON o.user_id = u.id
            GROUP BY u.id, u.username, u.created_at
        """.trimIndent()
        
        timings[1] = measureTimeMillis {
            val res = dataAccess.rawQuery(sqlAgg).toColumn<Map<String, Any?>>().getOrThrow()
            if (res.isEmpty()) throw IllegalStateException("Empty result")
        }

        return timings
    }

    @AfterAll
    fun printResults() {
        println("\n\n--- FINAL BENCHMARK RESULTS: Flat Join (App Grouping) vs DB Aggregation (Dynamic Map) ---")
        val header = "| Users (Total Rows) | Flat Join + App Grouping | DB Aggregation (dynamic_map) |"
        val separator = "—".repeat(header.length)
        println(separator)
        println(header)
        println(separator)

        userCountsProvider().sorted().forEach { key ->
            val avgFlat = flatJoinResults[key]?.average()?.toLong() ?: -1
            val avgAgg = dynamicMapResults[key]?.average()?.toLong() ?: -1
            
            val totalRows = key * 10
            val rowStr = "$key ($totalRows)".padEnd(18)

            println(
                "| $rowStr " +
                        "| ${"$avgFlat ms".padEnd(24)} " +
                        "| ${"$avgAgg ms".padEnd(28)} |"
            )
        }
        println(separator)
    }

    @AfterAll
    fun tearDown() {
        dataSource.close()
    }
}
