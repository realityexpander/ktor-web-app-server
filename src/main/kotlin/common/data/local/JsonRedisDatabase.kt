package com.realityexpander.common.data.local

import com.realityexpander.jsonConfig
import com.realityexpander.ktorLogger
import com.redis.lettucemod.RedisModulesClient
import com.redis.lettucemod.api.StatefulRedisModulesConnection
import com.redis.lettucemod.api.reactive.RedisModulesReactiveCommands
import com.redis.lettucemod.api.sync.RedisModulesCommands
import com.redis.lettucemod.search.CreateOptions
import com.redis.lettucemod.search.Field
import com.redis.lettucemod.search.SearchOptions
import common.data.local.IJsonDatabase
import common.uuid2.IUUID2
import common.uuid2.UUID2
import common.uuid2.UUID2.Companion.fromUUID2StrToTypedUUID2
import common.uuid2.UUID2.Companion.toUUID2WithUUID2TypeOf
import domain.common.data.HasId
import domain.common.data.Model
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
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
 * - Performs database operations on a JSON redis client.
 * - Allows for searching text on fields.
 *
 * This implements a persistent database that uses a Redis client to store data in a JSON format.
 *
 * * **`TKey`** The type of the key used to identify the entities in the database.
 * * **`abstract class  Entity<TKey : Any>`** is a Marker interface for entities that will be
 *     stored in the database.
 *     It must implement the **`HasId<TKey>`** interface.
 *     It must be marked with the **`@Serializable`** annotation.

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
    redisClient: RedisModulesClient = RedisModulesClient.create(redisUrl),
): IJsonDatabase<TDomain, TEntity> {
    private val redisConnection: StatefulRedisModulesConnection<String, String> = redisClient.connect()
    private val redis = RedisCommands(redisConnection)

    init {
        redis.search.ftConfigSet("MINPREFIX", "1") // set the minimum searchable text length to one character (default is 2)
    }

    // Create the search index if it doesn't exist
    // fieldsToSearchIndex is an array of the fields to index on the entity
    fun initDatabase(fieldsToSearchIndex: Array<String>) {
        val mutex = Mutex()

        runBlocking {
            mutex.lock() // needed to prevent multiple threads from creating the index at the same time

            val isSearchIndexCreated = try {
                // check if index exists (throws exception if it doesn't exist)
                redis.sync.ftInfo(databaseRootName.searchIndexName())
                true
            } catch (e: Exception) {
                ktorLogger.warn("Index does not exist, creating index..., message=${e.message}, index=${databaseRootName.searchIndexName()}")
                false
            }

            if (!isSearchIndexCreated) {
                try {
                    // Set up the search indexes
                    @Suppress("ConvertToStringTemplate")
                    val fields = fieldsToSearchIndex.map { field ->
                        if(field=="id")
                            Field
                                .tag("$." + field) // Redis TAG type is not tokenized like TEXT fields
                                .`as`(field)
                                .withSuffixTrie()  // for improved search (allows partial word search)
                                .build()
                        else
                            Field
                                .text("$." + field)
                                .`as`(field)
                                .sortable()
                                .withSuffixTrie()  // for improved search (allows partial word search)
                                .build()
                    }.toTypedArray()

                    //        // make all fields text searchable
                    //        redisSearchCommand.ftAdd(
                    //            databaseRootName.searchIndexName(),
                    //            "$databaseRootName:${entity.id()}",
                    //            1.0,
                    //            *entityKSerializer.descriptor.elementNames.map { field ->
                    //                "$." + field to entityKSerializer.descriptor.getElementIndex(field).toString()
                    //            }.toTypedArray()
                    //        )

                    val result = redis.sync.ftCreate(
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

    override suspend fun findAllEntities(): List<TEntity> {
        var result = arrayOf<String>()
        do {
            var cursor = "0"
            val scanCursor = ScanCursor.of(cursor)
            val foundKeys = redis.sync.scan(
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

        return result.map { redis.sync.jsonGet(it, "$") }
            .map { jsonConfig.decodeFromString(ListSerializer(entityKSerializer), it)[0] }
            .toList()
    }

    override suspend fun findEntityById(id: UUID2<TDomain>): TEntity? {
        val result = redis.sync.jsonGet("$databaseRootName:$id", "$")
            ?: return null

        return jsonConfig.decodeFromString(ListSerializer(entityKSerializer), result)[0]
    }

    override suspend fun findEntitiesByField(field: String, rawSearchValue: String): List<TEntity> {
        val searchQuery =
            if(field=="id") { // search based on TAG, not TEXT.  TAGs are not tokenized like TEXT fields.
                """
                @$field:{*${rawSearchValue.escapeRedisSearchSpecialCharacters()}*}
                """.trimIndent()
            } else
                """
                @$field:*$rawSearchValue*
                """.trimIndent()

        val result = redis.sync.ftSearch(
            databaseRootName.searchIndexName(),
                searchQuery,
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

    override suspend fun addEntity(entity: TEntity): TEntity {
        @Suppress("UNCHECKED_CAST")
        entity as HasId<UUID2<TDomain>>

        redis.sync.jsonSet(
            "$databaseRootName:${entity.id()}",  // todo use :{id} instead of :id
            "$",
            jsonConfig.encodeToString(entityKSerializer, entity)
        )

        return entity;
    }

    override suspend fun updateEntity(entity: TEntity): TEntity? {
        @Suppress("UNCHECKED_CAST")
        entity as HasId<UUID2<TDomain>>

        val result =
            redis.sync.jsonGet("$databaseRootName:${entity.id()}", "$")
            ?: return null
        addEntity(entity)

        return entity;
    }

    override suspend fun upsertEntity(entity: TEntity): TEntity? {
        @Suppress("UNCHECKED_CAST")
        entity as HasId<UUID2<TDomain>>

        // simulate network upsert
        return if(findEntityById(entity.id()) != null) {
            updateEntity(entity)
        } else {
            addEntity(entity)
        }
    }

    override suspend fun deleteEntity(entity: TEntity) {
        redis.sync.del("$databaseRootName:${entity.id()}")
    }

    override suspend fun deleteEntityById(id: UUID2<TDomain>) {
        redis.sync.del("$databaseRootName:$id")
    }

    public override suspend fun deleteDatabase() {

        // Delete all the keys
        do {
            var cursor = "0"
            val scanCursor = ScanCursor.of(cursor)
            val deleteKeys = redis.sync.scan(
                scanCursor,
                    ScanArgs()
                        .match("$databaseRootName:*")
                        .limit(100),
                )

            deleteKeys?.keys?.forEach {
                redis.sync.del(it)
            }
            cursor = deleteKeys?.cursor ?: break
        } while (deleteKeys?.isFinished == false)

        // Delete the index
        redis.search.ftDropindex(databaseRootName.searchIndexName())
    }

    companion object {
        fun String.searchIndexName(): String {
            val databaseRootName = this
            return "${databaseRootName}_index"
        }

        fun String.escapeRedisSearchSpecialCharacters(): String {
            val escapeChars =
                """
            ,.<>{}[]"':;!@#$%^&*()-+=~"
            """.trimIndent()
            var result = this

            escapeChars.forEach {
                result = result.replace(it.toString(), "\\$it")
            }

            return result
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

    // Define the Redis Commands
    class RedisCommands(private val redisConnection: StatefulRedisModulesConnection<String, String>) {
        val sync: RedisModulesCommands<String, String> = redisConnection.sync()
        @OptIn(ExperimentalLettuceCoroutinesApi::class)
        val coroutine: RedisCoroutinesCommands<String, String> = redisConnection.coroutines()
        val reactive: RedisModulesReactiveCommands<String, String> = redisConnection.reactive()
        val search: RedisSearchCommands =
            RedisCommandFactory(redisConnection).getCommands(RedisSearchCommands::class.java)
    }
}
