package com.realityexpander.common.data.local

import com.redis.lettucemod.RedisModulesClient
import common.data.local.IDatabase
import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.HasId
import domain.common.data.info.local.InfoEntity
import kotlinx.serialization.KSerializer
import okhttp3.internal.toImmutableList

/**
 * **InfoEntityRedisDatabase**
 *
 * An implementation of a Redis database client that uses the IDatabase interface to persistently store InfoEntity.
 *
 * Entities are stored as JSON strings in a Redis database.
 *
 * Implements a Database API that is backed by a Redis client.
 * Data is persisted, so the database is not reset on each run.
 *
 * @param TDomain The type of UUID2<> to use for the database. ie: User -> UUID2&lt;User&gt;
 * @param TEntityInfo The type of InfoEntity to use for the entities in the database.
 * @param databaseName The root json field name for the database.
 * @param entityKSerializer The kotlinx json serializer to use for the database entities.
 * @param redisUrl The URL to use for the Redis client.
 * @param redisClient The Redis client to use for the database.
 */

class InfoEntityRedisDatabase<TDomain : IUUID2, TEntityInfo : InfoEntity>(
    databaseName: String = DEFAULT_DATABASE_NAME,
    entityKSerializer: KSerializer<TEntityInfo>,
    private val redisUrl: String = "redis://localhost:6379",
    private val redisClient: RedisModulesClient = RedisModulesClient.create(redisUrl),
) : JsonRedisDatabase<TDomain, TEntityInfo>(  // -> <UUID2<User>, UserAuthEntity>
        databaseName,
        entityKSerializer,
        redisUrl,
        redisClient
    ),
    IDatabase<TDomain, TEntityInfo>
{
    override suspend fun fetchEntityInfo(id: UUID2<TDomain>): Result<TEntityInfo> {
        return try {
            val entityInfo = super.findEntityById(id)
                ?: return Result.failure(Exception("Entity not found, id: $id"))

            Result.success(entityInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun findAllUUID2ToEntityInfoMap(): Result<Map<UUID2<TDomain>, TEntityInfo>> {
        return try {
            val entityInfoMap =
                super.findAllEntities()
                    .toImmutableList()
                    .associateBy {
                        it.id()
                    }

            @Suppress("UNCHECKED_CAST")
            entityInfoMap as Map<UUID2<TDomain>, TEntityInfo>

            Result.success(entityInfoMap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAllEntityInfos(): Result<Unit> {
        return try {
            super.deleteDatabase()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteEntityInfo(entityInfo: TEntityInfo): Result<Unit> {
        return try {
            @Suppress("UNCHECKED_CAST")
            if(super.findEntityById(entityInfo.id() as UUID2<TDomain>) == null)
                return Result.failure(Exception("Entity not found, id: ${entityInfo.id()}"))

            super.deleteEntity(entityInfo)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun upsertEntityInfo(entityInfo: TEntityInfo): Result<TEntityInfo> {
        @Suppress("UNCHECKED_CAST")
        entityInfo as HasId<UUID2<TDomain>>

        if(super.findEntityById(entityInfo.id()) == null)
            super.addEntity(entityInfo)
        else
            super.updateEntity(entityInfo)

        return Result.success(entityInfo)
    }

    override suspend fun updateEntityInfo(entityInfo: TEntityInfo): Result<TEntityInfo> {
        return try {
            super.updateEntity(entityInfo)

            Result.success(entityInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addEntityInfo(entityInfo: TEntityInfo): Result<TEntityInfo> {
        super.addEntity(entityInfo)

        return Result.success(entityInfo)
    }

    override suspend fun findEntityInfosByField(field: String, searchValue: String): Result<List<TEntityInfo>> {
        return try {
            val entityInfoList = super.findAllEntitiesByField(field, searchValue)

            Result.success(entityInfoList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val DEFAULT_DATABASE_NAME = "jsonDatabase"
    }
}