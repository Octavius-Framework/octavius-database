package io.github.octaviusframework.db.domain.test.softEnum

import io.github.octaviusframework.db.api.annotation.DynamicallyMappable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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