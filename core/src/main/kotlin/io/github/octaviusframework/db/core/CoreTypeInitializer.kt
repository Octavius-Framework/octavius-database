package io.github.octaviusframework.db.core

import io.github.octaviusframework.db.api.exception.InitializationException
import io.github.octaviusframework.db.api.exception.InitializationExceptionMessage
import io.github.octaviusframework.db.api.exception.QueryContext
import io.github.octaviusframework.db.core.jdbc.JdbcTemplate
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Manages the initialization of core PostgreSQL types and functions required by the Octavius framework.
 *
 * This component runs immediately after connection establishment and before any migrations,
 * ensuring that the database infrastructure is ready for the framework to operate.
 *
 * ## The `dynamic_dto` Type
 *
 * The central piece of infrastructure is the `dynamic_dto` composite type.
 * It enables polymorphic data handling within PostgreSQL.
 *
 * **Structure:**
 * - `type_name` (text): A discriminator key (e.g., "profile_dto") mapped to a specific Kotlin class.
 * - `data_payload` (jsonb): The actual data serialized as JSON.
 *
 * **Purpose:**
 * Serves as a universal container for transmitting dynamic data structures where the type
 * is determined at runtime rather than by the database schema. It is ideal for:
 * - Aggregating different data types in a single column (polymorphic arrays).
 * - Ad-hoc object mapping in queries (e.g., using `JOIN LATERAL`).
 *
 * **Note:**
 * For static, well-defined data structures, a dedicated PostgreSQL `COMPOSITE TYPE` is preferred.
 *
 * ## The `dynamic_map` Type
 *
 * A specialized transport structure (`dynamic_map_entry[]`) used for returning ad-hoc, untyped maps
 * from PostgreSQL to Kotlin while **strictly preserving PostgreSQL type information (OIDs)**.
 * 
 * **Structure:**
 * - `type_oid` (oid): The exact PostgreSQL internal Object Identifier of the original value.
 * - `key` (text): The map key.
 * - `raw_value` (text): The text-format representation of the value.
 *
 * **Purpose:**
 * Because a raw `Map<String, Any?>` lacks a target Kotlin class for type inference, preserving the OID 
 * is necessary to allow Octavius's `TypeHandler` architecture to precisely decode each map value into 
 * its exact Kotlin equivalent (e.g. keeping custom `@PgEnum` instead of falling back to String).
 * 
 * **⚠️ DANGER:**
 * `dynamic_map` is intended purely for data transport (e.g., dynamic projections, pivot tables) and 
 * temporary persistence (e.g. `TEMP` tables). You should **never** store `dynamic_map` in persistent 
 * tables because OIDs for custom types are dynamically generated and **will change** across different 
 * environments or after a database dump/restore, leading to data corruption.
 */
internal object CoreTypeInitializer {

    private val logger = KotlinLogging.logger {}

    private const val CORE_INIT_SQL = """
        DO $$
        BEGIN
           -- DYNAMIC_DTO
            IF NOT EXISTS (
                SELECT 1 
                FROM pg_type t 
                JOIN pg_namespace n ON n.oid = t.typnamespace 
                WHERE t.typname = 'dynamic_dto' AND n.nspname = 'public'
            ) THEN
                CREATE TYPE public.dynamic_dto AS (
                    type_name    text,
                    data_payload jsonb
                );
            END IF;
            -- DYNAMIC_MAP_ENTRY
            IF NOT EXISTS (
                SELECT 1 
                FROM pg_type t 
                JOIN pg_namespace n ON n.oid = t.typnamespace 
                WHERE t.typname = 'dynamic_map_entry' AND n.nspname = 'public'
            ) THEN
                CREATE TYPE public.dynamic_map_entry AS (
                    type_oid  oid,
                    key       text,
                    raw_value text
                );
            END IF;
            -- DYNAMIC_MAP
            IF NOT EXISTS (
                SELECT 1 
                FROM pg_type t 
                JOIN pg_namespace n ON n.oid = t.typnamespace 
                WHERE t.typname = 'dynamic_map' AND n.nspname = 'public'
            ) THEN
                CREATE TYPE public.dynamic_map AS (
                    entries dynamic_map_entry[]
                );
            END IF;
        END$$;

        CREATE OR REPLACE FUNCTION public.dynamic_dto(p_type_name TEXT, p_data JSONB)
            RETURNS public.dynamic_dto AS
        $$
        BEGIN
            RETURN ROW (p_type_name, p_data)::public.dynamic_dto;
        END;
        $$ LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE;

        CREATE OR REPLACE FUNCTION public.to_dynamic_dto(p_type_name TEXT, p_value ANYELEMENT)
            RETURNS public.dynamic_dto AS
        $$
        BEGIN
            RETURN ROW (p_type_name, to_jsonb(p_value))::public.dynamic_dto;
        END;
        $$ LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE;

        CREATE OR REPLACE FUNCTION public.to_dynamic_dto(p_type_name TEXT, p_value TEXT)
            RETURNS public.dynamic_dto AS
        $$
        BEGIN
            RETURN ROW (p_type_name, to_jsonb(p_value))::public.dynamic_dto;
        END;
        $$ LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE;

        CREATE OR REPLACE FUNCTION public.unwrap_dto_payload(p_dto public.dynamic_dto)
            RETURNS JSONB AS
        $$
        BEGIN
            RETURN p_dto.data_payload;
        END;
        $$ LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE;

        CREATE OR REPLACE FUNCTION public.dynamic_map_entry(k text, v anyelement)
            RETURNS public.dynamic_map_entry AS 
        $$
        BEGIN
            IF k IS NULL THEN
                RAISE EXCEPTION 'dynamic_map_entry: Key cannot be null';
            END IF;
            
            RETURN (
                pg_typeof(v)::oid, 
                k, 
                CASE WHEN v IS NULL THEN NULL ELSE format('%s', v) END
            )::public.dynamic_map_entry;
        END;
        $$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE;

        DO $$
        BEGIN
            IF NOT EXISTS (
                SELECT 1 FROM pg_operator 
                WHERE oprname = '~>' AND oprnamespace = 'public'::regnamespace
            ) THEN
                CREATE OPERATOR public.~> (
                    LEFTARG = text,
                    RIGHTARG = anyelement,
                    PROCEDURE = public.dynamic_map_entry
                );
            END IF;
        END$$;

        CREATE OR REPLACE FUNCTION public.dynamic_map(VARIADIC items public.dynamic_map_entry[])
            RETURNS public.dynamic_map AS 
        $$
        BEGIN
            RETURN ROW(items)::public.dynamic_map;
        END;
        $$ LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE STRICT;
    """

    /**
     * Ensures all required core types and functions exist in the database.
     * Uses `IF NOT EXISTS` / `CREATE OR REPLACE` to be idempotent and safe to run on every startup.
     */
    fun ensureRequiredTypes(jdbcTemplate: JdbcTemplate) {
        logger.debug { "Ensuring core Octavius schema elements exist..." }
        try {
            jdbcTemplate.execute(CORE_INIT_SQL)
            logger.debug { "Core schema elements verified." }
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize Octavius core schema! dynamic_dto might be missing." }
            throw InitializationException(
                messageEnum = InitializationExceptionMessage.DB_QUERY_FAILED,
                details = "dynamic_dto",
                cause = e,
                queryContext = QueryContext("", mapOf(), CORE_INIT_SQL, listOf())
            )
        }
    }
}
