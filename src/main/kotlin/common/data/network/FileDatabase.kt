package com.realityexpander.common.data.network

import com.realityexpander.common.data.local.JsonFileDatabase
import common.data.local.IDatabase
import common.data.network.FakeHttpClient
import common.data.network.FakeURL
import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.HasId
import domain.common.data.info.local.InfoEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import okhttp3.internal.toImmutableList

/**
 * **FileDatabase**
 *
 * An implementation of a JsonFileDatabase that uses the IDatabase interface to persistently store InfoEntity.
 *
 * Simulates a Database API that is backed by a json file.
 * Data is persisted, so the database is not reset on each run.
 *
 * @param TDomain The type of UUID2<> to use for the database. ie: User -> UUID2<User>
 * @param TEntityInfo The type of InfoEntity to use for the entities in the database.
 * @param apiDatabaseFilename The filename of the database.
 * @param entityKSerializer The kotlinx json serializer to use for the database entities.
 * @param fakeUrl The fake URL to use for the API
 * @param fakeClient The fake HTTP client to use for the API
 */

class FileDatabase<TDomain : IUUID2, TEntityInfo : InfoEntity>(
    apiDatabaseFilename: String = DEFAULT_FILE_DATABASE_FILENAME,
    entityKSerializer: KSerializer<TEntityInfo>,
    private val fakeUrl: FakeURL = FakeURL("fakeHttp://fakeDatabaseHost:44444"),
    private val fakeClient: FakeHttpClient = FakeHttpClient()
) : JsonFileDatabase<TDomain, TEntityInfo>(apiDatabaseFilename, entityKSerializer),  // -> <UUID2<User>, UserEntity>
    IDatabase<TDomain, TEntityInfo>
{
    init {
        runBlocking {
            super.loadFileDatabase()
        }
    }

    override suspend fun fetchEntityInfo(id: UUID2<TDomain>): Result<TEntityInfo> {
        return try {
            val entityInfo = super.findEntityById(id)
                ?: return Result.failure(Exception("Entity not found"))

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

    override suspend fun deleteAllEntityInfo(): Result<Unit> {
        return try {
            super.deleteDatabaseFile()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteEntityInfo(entityInfo: TEntityInfo): Result<TEntityInfo> {
        return try {
            super.deleteEntity(entityInfo)

            Result.success(entityInfo)
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

    override suspend fun updateLookupTables() {
        // no-op
    }

    companion object {
        const val DEFAULT_FILE_DATABASE_FILENAME = "fileDatabase.json"
    }
}