package com.realityexpander.common.data.network

import com.realityexpander.common.data.local.JsonRedisDatabase
import com.redis.lettucemod.RedisModulesClient
import common.data.network.IAPI
import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.HasId
import domain.common.data.info.network.InfoDTO
import kotlinx.serialization.KSerializer
import okhttp3.internal.toImmutableList

/**
 * **InfoDTORedisApi**
 *
 * An implementation of a Redis database client that uses the IInfoEntityDatabase interface to persistently store InfoDTO.
 *
 * Entities are stored as JSON strings in a Redis database.
 * Data is persisted, so the database is not reset on each run.
 *
 * @param TDomain The type of UUID2<> to use for the database. ie: User -> UUID2<User>
 * @param TDTOInfo The type of InfoDTO to use for the entities in the database.
 * @param databaseName The filename of the database.
 * @param entityKSerializer The kotlinx json serializer to use for the database entities.
 * @param redisUrl The URL to use for the Redis client.
 * @param redisClient The Redis client to use for the database.
 */

class InfoDTORedisApi<TDomain : IUUID2, TDTOInfo : InfoDTO>(
    databaseName: String = DEFAULT_DATABASE_NAME,
    entityKSerializer: KSerializer<TDTOInfo>,
    private val redisUrl: String = "redis://localhost:6379",
    private val redisClient: RedisModulesClient = RedisModulesClient.create(redisUrl),
) : JsonRedisDatabase<TDomain, TDTOInfo>(  // -> <UUID2<Info>, InfoDTO>
        databaseName,
        entityKSerializer,
        redisUrl,
        redisClient
    ),
    IAPI<TDomain, TDTOInfo>
{
    override suspend fun fetchDTOInfo(id: UUID2<TDomain>): Result<TDTOInfo> {
        return try {
            val entityInfo = super.findEntityById(id)
                ?: return Result.failure(Exception("Entity not found, id: $id"))

            Result.success(entityInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun findAllUUID2ToDTOInfoMap(): Result<Map<UUID2<TDomain>, TDTOInfo>> {
        return try {
            val entityInfoMap =
                super.findAllEntities()
                    .toImmutableList()
                    .associateBy {
                        it.id()
                    }

            @Suppress("UNCHECKED_CAST")
            entityInfoMap as Map<UUID2<TDomain>, TDTOInfo>

            Result.success(entityInfoMap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAllDTOInfo(): Result<Unit> {
        return try {
            super.deleteDatabase()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteDTOInfo(dtoInfo: TDTOInfo): Result<Unit> {
        return try {
            @Suppress("UNCHECKED_CAST")
            if(super.findEntityById(dtoInfo.id() as UUID2<TDomain>) == null)
                return Result.failure(Exception("Entity not found, id: ${dtoInfo.id()}"))

            super.deleteEntity(dtoInfo)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun upsertDTOInfo(dtoInfo: TDTOInfo): Result<TDTOInfo> {
        @Suppress("UNCHECKED_CAST")
        dtoInfo as HasId<UUID2<TDomain>>

        if(super.findEntityById(dtoInfo.id()) == null)
            super.addEntity(dtoInfo)
        else
            super.updateEntity(dtoInfo)

        return Result.success(dtoInfo)
    }

    override suspend fun updateDTOInfo(dtoInfo: TDTOInfo): Result<TDTOInfo> {
        return try {
            super.updateEntity(dtoInfo)

            Result.success(dtoInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addDTOInfo(dtoInfo: TDTOInfo): Result<TDTOInfo> {
        super.addEntity(dtoInfo)

        return Result.success(dtoInfo)
    }

    override suspend fun findDTOInfosByField(field: String, searchValue: String): Result<List<TDTOInfo>> {
        return try {
            val entityInfoList = super.findEntitiesByField(field, searchValue)

            Result.success(entityInfoList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val DEFAULT_DATABASE_NAME = "jsonApi"
    }
}