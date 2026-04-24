package io.github.octaviusframework.db.domain.test.compositevsdynamic

import kotlinx.serialization.Serializable
import io.github.octaviusframework.db.api.annotation.DynamicallyMappable
import io.github.octaviusframework.db.api.annotation.PgComposite

@PgComposite(name = "statistics")
data class PgStats(val strength: Int, val agility: Int, val intelligence: Int)

@PgComposite(name = "game_character")
data class PgCharacter(val id: Int, val name: String, val stats: PgStats)


// 2. Klasy dla podejścia z dynamicznym mapowaniem (JSONB)
@Serializable
@DynamicallyMappable("dynamic_stats")
data class DynamicStats(val strength: Int, val agility: Int, val intelligence: Int)

@Serializable
@DynamicallyMappable("dynamic_character")
data class DynamicCharacter(val id: Int, val name: String, val stats: DynamicStats)