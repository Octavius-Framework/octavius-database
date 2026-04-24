package io.github.octaviusframework.db.domain.test.softEnum

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.octaviusframework.db.api.annotation.DynamicallyMappable

// Definicja Soft Enuma w kodzie testowym
@DynamicallyMappable(typeName = "feature_flag")
@Serializable
enum class FeatureFlag {
    @SerialName("dark_theme")
    DarkTheme,
    @SerialName("beta_access")
    BetaAccess,
    @SerialName("legacy_support")
    LegacySupport
}