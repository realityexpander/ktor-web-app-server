package com.realityexpander.common.data.local

import com.realityexpander.jsonConfig
import com.realityexpander.ktorLogger
import com.redis.lettucemod.RedisModulesClient
import com.redis.lettucemod.api.StatefulRedisModulesConnection
import com.redis.lettucemod.api.sync.RedisModulesCommands
import com.redis.lettucemod.search.CreateOptions
import com.redis.lettucemod.search.Field
import com.redis.lettucemod.search.SearchOptions
import common.uuid2.IUUID2
import common.uuid2.UUID2
import common.uuid2.UUID2.Companion.fromUUID2StrToTypedUUID2
import common.uuid2.UUID2.Companion.fromUUID2StrToUUID2
import common.uuid2.UUID2.Companion.toUUID2WithUUID2TypeOf
import domain.common.data.HasId
import domain.common.data.Model
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.api.coroutines
import io.lettuce.core.dynamic.Commands
import io.lettuce.core.dynamic.RedisCommandFactory
import io.lettuce.core.dynamic.annotation.Command
import io.lettuce.core.dynamic.annotation.CommandNaming
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/**
 * **JsonRedisDatabase**
 *
 * - Performs key-value database operations on a JSON redis client.
 *
 * This implements a persistent database that uses a Redis client to store data in a JSON format.
 *
 * * **`TKey`** The type of the key used to identify the entities in the database.
 * * **`abstract class  Entity<TKey : Any>`** is a Marker interface for entities that will be
 *     stored in the database.
 *     It must implement the **`HasId<TKey>`** interface.
 *     It must be marked with the **`@Serializable`** annotation.
 *
 * Note: It is possible to have multiple databases, but they must have different filenames.
 *
 * @param databaseRootName The name of the JSON root to use for the database.
 * @param entityKSerializer The Kotlin class serializer to use for the Entities in the database.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

abstract class JsonRedisDatabase<TDomain : IUUID2, TEntity : Model> (  // <User, UserInfo> -> in database:<UUID2<User>, UserInfo>
    private val databaseRootName: String = "jsonDB",
    private val entityKSerializer: KSerializer<TEntity>,
    private val redisUrl: String = "redis://localhost:6379",
    private val redisClient: RedisModulesClient = RedisModulesClient.create(redisUrl),
) {
    private val redisConnection: StatefulRedisModulesConnection<String, String> = redisClient.connect()
    private val redisSyncCommand: RedisModulesCommands<String, String> = redisConnection.sync()
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    private val redisCoroutineCommand = redisConnection.coroutines()
    private val redisReactiveCommand = redisConnection.reactive()

    private val redisSearchCommands: RedisSearchCommands =
        RedisCommandFactory(redisConnection).getCommands(RedisSearchCommands::class.java)

    init {
        redisSearchCommands.ftConfigSet("MINPREFIX", "1") // set the minimum searchable text length to 1 character (default is 2)
    }

    // Create the search index if it doesn't exist
    // fieldsToSearchIndex is an array of the fields to index on the entity
    fun initDatabase(fieldsToSearchIndex: Array<String>) {
        val mutex = Mutex()

        runBlocking {
            mutex.lock() // needed to prevent multiple threads from creating the index at the same time

            val isSearchIndexCreated = try {
                // check if index exists (throws exception if it doesn't exist)
                redisSyncCommand.ftInfo(databaseRootName.searchIndexName())
                true
            } catch (e: Exception) {
                ktorLogger.warn("Index does not exist, creating index..., message=${e.message}, index=${databaseRootName.searchIndexName()}")
                false
            }

            if (!isSearchIndexCreated) {
                try {
                    // Setup the search indexes
                    @Suppress("ConvertToStringTemplate")
                    val fields = fieldsToSearchIndex.map { field ->
                        Field
                            .text("$." + field)
                            .`as`(field)
                            .sortable()
                            .withSuffixTrie()  // for improved search (allows partial word search)
                            .build()
                    }.toTypedArray()

                    val result = redisSyncCommand.ftCreate(
                        databaseRootName.searchIndexName(),
                        CreateOptions.builder<String, String>()
                            .prefix("$databaseRootName:")
                            .on(CreateOptions.DataType.JSON)
                            .build(),
                        *fields
                    )

                    if (result != "OK") {
                        ktorLogger.error("Error creating index: $result")
                    }
                } catch (e: Exception) {
                    ktorLogger.warn("Error creating index: ${e.message}")
                }
            }
        }
        mutex.unlock()
    }

    suspend inline fun <reified TDomain : IUUID2> toDatabaseCopy(): Map<UUID2<TDomain>, TEntity> {
        return findAllEntities()
            .associateBy {
                it.id().toUUID2WithUUID2TypeOf(TDomain::class)
            }
    }

    suspend fun findAllEntities(): List<TEntity> {
        var result = arrayOf<String>()
        do {
            var cursor = "0"
            val scanCursor = ScanCursor.of(cursor)
            val foundKeys = redisSyncCommand.scan(
                scanCursor,
                ScanArgs()
                    .match("$databaseRootName:*")
                    .limit(100),
            )

            foundKeys?.keys?.forEach {
                result += it
            }
            cursor = foundKeys?.cursor ?: break
        } while (foundKeys?.isFinished == false)

        return result.map { redisSyncCommand.jsonGet(it, "$") }
            .map { jsonConfig.decodeFromString(ListSerializer(entityKSerializer), it)[0] }
            .toList()
    }

    suspend fun findEntityById(id: UUID2<TDomain>): TEntity? {
        val result = redisSyncCommand.jsonGet("$databaseRootName:$id", "$")
            ?: return null

        return jsonConfig.decodeFromString(ListSerializer(entityKSerializer), result)[0]
    }

    suspend fun findAllEntitiesByField(field: String, searchValue: String): List<TEntity> {
        val result = redisSyncCommand.ftSearch(
            databaseRootName.searchIndexName(),
                """
                    ${'$'}.$field=*$searchValue*
                """.trimIndent(),
                SearchOptions.builder<String, String>()
                .limit(0, 100)
                .withSortKeys(true)
                .build()
        )

        return result.map { document ->
            val id = document.id.substringAfter(":")
            val entity = findEntityById(id.fromUUID2StrToTypedUUID2())
            entity
        }.filterNotNull()
    }

    suspend fun addEntity(entity: TEntity): TEntity {
        @Suppress("UNCHECKED_CAST")
        entity as HasId<UUID2<TDomain>>

        redisSyncCommand.jsonSet(
            "$databaseRootName:${entity.id()}", "$",
            jsonConfig.encodeToString(entityKSerializer, entity)
        )

        return entity;
    }

    suspend fun updateEntity(entity: TEntity): TEntity? {
        @Suppress("UNCHECKED_CAST")
        entity as HasId<UUID2<TDomain>>

        val result =
            redisSyncCommand.jsonGet("$databaseRootName:${entity.id()}", "$")
            ?: return null
        addEntity(entity)

        return entity;
    }

    suspend fun upsertEntity(entity: TEntity): TEntity? {
        @Suppress("UNCHECKED_CAST")
        entity as HasId<UUID2<TDomain>>

        // simulate network upsert
        return if(findEntityById(entity.id()) != null) {
            updateEntity(entity)
        } else {
            addEntity(entity)
        }
    }

    suspend fun deleteEntity(entity: TEntity) {
        redisSyncCommand.del("$databaseRootName:${entity.id()}")
    }

    suspend fun deleteEntityById(id: UUID2<TDomain>) {
        redisSyncCommand.del("$databaseRootName:$id")
    }

    public suspend fun deleteDatabase() {

        // Delete all the keys
        do {
            var cursor = "0"
            val scanCursor = ScanCursor.of(cursor)
            val deleteKeys = redisSyncCommand.scan(
                scanCursor,
                    ScanArgs()
                        .match("$databaseRootName:*")
                        .limit(100),
                )

            deleteKeys?.keys?.forEach {
                redisSyncCommand.del(it)
            }
            cursor = deleteKeys?.cursor ?: break
        } while (deleteKeys?.isFinished == false)

        // Delete the index
        redisSearchCommands.ftDropindex(databaseRootName.searchIndexName())
    }

    companion object {
        fun String.searchIndexName(): String {
            val databaseRootName = this
            return "${databaseRootName}_index"
        }
    }

    // Define the Redis Search Commands
    // Yes, its odd that we have to define the commands this way, but it's how it works.
    interface RedisSearchCommands : Commands {
        @Command("FT.CREATE")
        @CommandNaming(strategy = CommandNaming.Strategy.DOT)
        fun ftCreate(index: String, vararg args: String): String

        @Command("FT.ADD")
        @CommandNaming(strategy = CommandNaming.Strategy.DOT)
        fun ftAdd(index: String, docId: String, score: Double, vararg fields: Any): String

        @Command("FT.CONFIG SET")
        @CommandNaming(strategy = CommandNaming.Strategy.DOT)
        fun ftConfigSet(key: String, value: String): String

        @Command("FT.CONFIG GET")
        @CommandNaming(strategy = CommandNaming.Strategy.DOT)
        fun ftConfigGet(key: String): String

        @Command("FT.DROPINDEX")
        @CommandNaming(strategy = CommandNaming.Strategy.DOT)
        fun ftDropindex(index: String): String
    }

}
