package com.realityexpander.common.data.local

import common.data.local.IInfoEntityDatabase
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
 * **InfoEntityFileDatabase**
 *
 * An implementation of a JsonFileDatabase that uses the IInfoEntityDatabase interface to persistently store InfoEntity.
 *
 * Simulates a Database API that is backed by a json file.
 * Data is persisted, so the database is not reset on each run.
 *
 * @param TDomain The type of UUID2<> to use for the database. ie: User -> UUID2<User>
 * @param TEntityInfo The type of InfoEntity to use for the entities in the database.
 * @param databaseFilename The filename of the database.
 * @param entityKSerializer The kotlinx json serializer to use for the database entities.
 * @param fakeUrl The fake URL to use for the API
 * @param fakeClient The fake HTTP client to use for the API
 */

class InfoEntityFileDatabase<TDomain : IUUID2, TEntityInfo : InfoEntity>(
    databaseFilename: String = DEFAULT_FILE_DATABASE_FILENAME,
    entityKSerializer: KSerializer<TEntityInfo>,
    private val fakeUrl: FakeURL = FakeURL("fakeHttp://fakeDatabaseHost:44444"),
    private val fakeClient: FakeHttpClient = FakeHttpClient()
) : JsonFileDatabase<TDomain, TEntityInfo>(databaseFilename, entityKSerializer),  // -> <UUID2<User>, UserAuthEntity>
    IInfoEntityDatabase<TDomain, TEntityInfo>
{
    init {
        runBlocking {
            super.loadFileDatabase()
        }
    }

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

    override suspend fun findEntityInfosByField(field: String, searchValue: String): Result<List<TEntityInfo>> {
        return try {
            val entityInfoList = super.findEntitiesByField(field, searchValue)

            Result.success(entityInfoList)
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

    override suspend fun updateLookupTables() {
        // no-op
    }

    companion object {
        const val DEFAULT_FILE_DATABASE_FILENAME = "fileDatabase.json"
    }
}