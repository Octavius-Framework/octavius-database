package io.github.octaviusframework.db.core.mapping.dynamic

import io.github.octaviusframework.db.api.annotation.DynamicallyMappable
import io.github.octaviusframework.db.api.annotation.PgComposite
import kotlinx.serialization.Serializable

@Serializable
@DynamicallyMappable("prop_inner_dto")
@PgComposite("prop_inner")
data class PropInner(val value: String)

@PgComposite("prop_outer")
data class PropOuter(val payload: PropInner)

@PgComposite("prop_outer_all_comp")
data class PropOuterComp(val payload: Any?)
