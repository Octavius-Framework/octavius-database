package org.octavius.data.annotation

/**
 * Annotation used to specify a custom key for a property
 * during object to/from map conversion. (functions toDataMap and toDataObject)
 *
 * By default, the property name is used with snake_case <-> camelCase conversion. This annotation allows overriding it,
 * which is useful when map key names should not match property names
 * e.g., userId vs user
 *
 * @property name Key name that will be used in the map.
 *
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class MapKey(val name: String)