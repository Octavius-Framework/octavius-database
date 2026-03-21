# Lifecycle & Shutdown

*Every campaign must end. When Caesar returned from Gaul, he did not leave his legions camped on the banks of the Rubicon indefinitely — he issued the order to disband, the soldiers were paid and released, and the land stopped feeding idle men. A `DataAccess` instance is no different: when your application's work is done, the connection pool should be formally dismissed.*

The `DataAccess` interface implements `AutoCloseable`. Properly closing the instance is essential to gracefully shut down the underlying database connection pool, release TCP connections, and terminate background threads.

### Why is it important?
When you initialize Octavius using `fromConfig()`, it creates an internal HikariCP connection pool. If you don't close it when your application stops, you may experience database connection leaks, `unexpected EOF` errors on the PostgreSQL side, or prevent the JVM from shutting down cleanly.

### Standard Usage

For short-lived scripts, background jobs, or CLI applications, take advantage of Kotlin's standard `.use {}` block:

```kotlin
OctaviusDatabase.fromConfig(config).use { dataAccess ->
    // Issue your commands...
    val cohorts = dataAccess.select("*").from("legions").toListOf<Legion>()
} // The internal HikariCP pool is automatically closed here
```

### Common Integration Patterns

In long-running applications or services, you should tie the `close()` method to your application's lifecycle manager or dependency injection container.

**Ktor (Server):**
If you are using `ktor-server`, tie the closure to the application's stop event via the environment monitor:

```kotlin
val dataAccess = OctaviusDatabase.fromConfig(config)

environment.monitor.subscribe(ApplicationStopped) {
    dataAccess.close()
}
```

**Spring Boot:**
Spring automatically detects `AutoCloseable` beans. If you expose `DataAccess` as a `@Bean`, no extra code is required for shutdown:

```kotlin
@Bean
fun dataAccess(): DataAccess {
    return OctaviusDatabase.fromConfig(
        DatabaseConfig.loadFromFile("database.properties")
    )
}
```

**Koin (Dependency Injection):**
For manual lifecycle management in Koin, use the `onClose` hook in your module definition. This is especially useful in CLI tools or desktop apps where you manually call `stopKoin()`:

```kotlin
val appModule = module {
    single<DataAccess> { 
        OctaviusDatabase.fromConfig(get()) 
    } onClose { it?.close() }
}
```

### Behavior with Existing DataSource

When initializing via `fromDataSource()`, calling `dataAccess.close()` will invoke the optional `onClose` lambda if you provided one. If no lambda was provided, Octavius assumes the lifecycle of the `DataSource` is managed externally by your framework (e.g., Spring Boot managing its own Hikari pool), and `close()` behaves as a no-op for the connection pool.
