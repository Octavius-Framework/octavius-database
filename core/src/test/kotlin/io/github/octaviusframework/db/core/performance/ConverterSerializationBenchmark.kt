package io.github.octaviusframework.db.core.performance

import io.github.octaviusframework.db.core.mapping.utils.createFakeTypeRegistry
import io.github.octaviusframework.db.core.type.InternalQueryOptions
import io.github.octaviusframework.db.core.type.KotlinToPostgresConverter
import io.github.octaviusframework.db.core.type.registry.TypeRegistry
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureNanoTime

/**
 * Benchmark wydajności dla KotlinToPostgresConverter.
 *
 * Mierzy czas analizy zapytań SQL (prepocessor), podmieniania nazwanych parametrów
 * na pozycyjne (`?`) oraz serializacji wartości (w tym List i złożonych stringów)
 * na parametry zgodne z JDBC.
 *
 * Metodologia:
 * 1. Test generuje duże zapytania "Bulk Insert" zawierające po 4 parametry na "wiersz".
 * 2. Przeprowadzany jest "warm-up" JVM.
 * 3. Test powtarzany wielokrotnie dla każdej liczby wierszy, z uśrednieniem wyniku.
 * 4. Używa `createFakeTypeRegistry()` z zachowaniem izolacji.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Disabled
class ConverterSerializationBenchmark {

    // --- Konfiguracja Benchmarku ---
    private val ITERATIONS_PER_SIZE = 20

    // --- Zmienne przechowujące wyniki (czas w nanosekundach) ---
    private val serializationResults = ConcurrentHashMap<Int, MutableList<Long>>()

    // --- Komponenty testowane ---
    private lateinit var converter: KotlinToPostgresConverter
    private lateinit var typeRegistry: TypeRegistry
    private lateinit var options: InternalQueryOptions

    companion object {
        // Liczba wierszy w symulowanym zapytaniu Bulk Insert (każdy wiersz to kilka parametrów)
        @JvmStatic
        fun rowCountsProvider(): List<Int> = listOf(1, 10, 50, 100, 250, 500, 1000, 5000, 10000, 15000)
    }

    @BeforeAll
    fun setup() {
        println("--- ROZPOCZYNANIE KONFIGURACJI BENCHMARKU SERIALIZACJI (Kotlin -> Postgres) ---")
        typeRegistry = createFakeTypeRegistry()
        this.converter = KotlinToPostgresConverter(typeRegistry)
        this.options = InternalQueryOptions.empty(typeRegistry, Json)

        println("\n--- WARM-UP RUN (500 wierszy, wyniki ignorowane) ---")
        val (warmupSql, warmupParams) = buildTestQueryAndParams(500)
        repeat(10) {
            converter.toPositionalQuery(warmupSql, warmupParams, options)
        }
        println("--- WARM-UP COMPLETE ---")
    }

    @ParameterizedTest(name = "Uruchamianie benchmarku serializacji dla {0} wierszy...")
    @MethodSource("rowCountsProvider")
    @Order(1)
    fun runSerializationBenchmark(rowCount: Int) {
        println("\n--- POMIAR DLA $rowCount WIERSZY (x$ITERATIONS_PER_SIZE iteracji) ---")
        val (testSql, testParams) = buildTestQueryAndParams(rowCount)
        val timings = mutableListOf<Long>()

        repeat(ITERATIONS_PER_SIZE) {
            val time = measureNanoTime {
                converter.toPositionalQuery(testSql, testParams, options)
            }
            timings.add(time)
        }
        serializationResults[rowCount] = timings
    }

    @AfterAll
    fun printResults() {
        println("\n\n--- OSTATECZNE WYNIKI BENCHMARKU SERIALIZACJI ---")
        println("==================================================================================================")
        println("| Liczba wierszy | Liczba parametrów | Średni czas (ms) | Wiersze/sek (ops/sec) | Parametry/sek  |")
        println("|----------------|-------------------|------------------|-----------------------|----------------|")

        val sortedKeys = rowCountsProvider().sorted()
        for (key in sortedKeys) {
            val avgNanos = serializationResults[key]?.average() ?: -1.0
            val avgMillis = avgNanos / 1_000_000.0
            val paramCount = key * 4 // Ponieważ w buildTestQueryAndParams wstawiamy 4 parametry na wiersz

            // Przepustowość
            val opsPerSec = if (avgNanos > 0) (key / (avgNanos / 1_000_000_000.0)).toLong() else 0
            val paramsPerSec = if (avgNanos > 0) (paramCount / (avgNanos / 1_000_000_000.0)).toLong() else 0

            val keyStr = key.toString().padStart(14)
            val paramStr = paramCount.toString().padStart(17)
            val avgStr = String.format("%.3f ms", avgMillis).padStart(16)
            val opsStr = "$opsPerSec".padStart(21)
            val ppsStr = "$paramsPerSec".padStart(14)

            println("|$keyStr |$paramStr |$avgStr |$opsStr |$ppsStr |")
        }
        println("==================================================================================================")
        println("* Wiersze/sek = przetworzone 'zestawy' wartości na sekundę.")
        println("* Parametry/sek = łączna liczba rozwiązanych i skonwertowanych @parametrów na sekundę.")
    }

    /**
     * Buduje zapytanie typu Bulk Insert oraz odpowiadającą mu mapę parametrów.
     * Symuluje wstawianie wielu rekordów, zawierających m.in. stringi i listy
     * (aby obciążyć logikę konwertera).
     *
     * Zwraca Pair(zapytanie SQL, mapa parametrów).
     */
    private fun buildTestQueryAndParams(rowCount: Int): Pair<String, Map<String, Any?>> {
        val sqlBuilder = StringBuilder("INSERT INTO fake_project (id, name, tags, is_active) VALUES ")
        val params = mutableMapOf<String, Any?>()

        for (i in 1..rowCount) {
            // Dodajemy nazwane parametry dla danego wiersza
            sqlBuilder.append("(@id_$i, @name_$i, @tags_$i, @active_$i)")
            if (i < rowCount) {
                sqlBuilder.append(",\n")
            }

            // Populating the map with diverse data types
            params["id_$i"] = i
            params["name_$i"] = "Project Number $i with some \"special\" characters"
            // Używamy listy, co wymusi przejście przez logikę serializera (PgTextSerializer.serializeList)
            params["tags_$i"] = listOf("tagA_$i", "tagB_$i", "complex tag with , and \" $i")
            params["active_$i"] = (i % 2 == 0) // Boolean
        }

        // Dodajemy klauzulę z użyciem surowego znaku zapytania `?`,
        // aby upewnić się, że preprocesor prawidłowo ewaluuje escapeQuestionMarks()
        sqlBuilder.append("\nON CONFLICT (id) DO UPDATE SET tags = fake_project.tags || '{\"updated\": true}'::jsonb ")
        sqlBuilder.append("WHERE fake_project.tags ? 'critical';")

        return Pair(sqlBuilder.toString(), params)
    }
}
