package common.data.network

import com.realityexpander.common.data.local.JsonFileDatabase
import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.HasId
import domain.common.data.info.network.InfoDTO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import okhttp3.internal.toImmutableList

/**
 * InfoDTOFileApi
 *
 * A JsonFileDatabase that implements the IAPI interface for InfoDTO.
 *
 * Simulates a REST API that is backed by a json file.
 * Data is persisted, so the database is not reset on each run.
 *
 * @param TDomain The type of UUID2 to use for the database. ie: User -> UUID2<User>
 * @param TDTOInfo The type of InfoDTO for the entities in the database.
 * @param databaseFilename The filename of the database.
 * @param serializer The kotlinx json serializer to use for the database entities.
 * @param fakeUrl The fake URL to use for the API
 * @param client The fake HTTP client to use for the API
 */

class InfoDTOFileApi<TDomain : IUUID2, TDTOInfo : InfoDTO>(
    databaseFilename: String = DEFAULT_FILE_API_DATABASE_FILENAME,
    serializer: KSerializer<TDTOInfo>,
    private val fakeUrl: FakeURL = FakeURL("fakeHttp://fakeApiHost:22222"),
    private val client: FakeHttpClient = FakeHttpClient()
) : JsonFileDatabase<TDomain, TDTOInfo>(databaseFilename, serializer),  // -> <UUID2<User>, UserAuthEntity>
    IAPI<TDomain, TDTOInfo>
{
    init {
        runBlocking {
            super.loadFileDatabase()
        }
    }

    override suspend fun fetchDTOInfo(id: UUID2<TDomain>): Result<TDTOInfo> {
        return try {
            val dtoInfo = super.findEntityById(id)
                ?: return Result.failure(Exception("Entity not found, id: $id"))

            Result.success(dtoInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun findAllUUID2ToDTOInfoMap(): Result<Map<UUID2<TDomain>, TDTOInfo>> {
        return try {
            val dtoInfoMap =
                super.findAllEntities()
                    .toImmutableList()
                    .associateBy { it.id() }

            @Suppress("UNCHECKED_CAST")
            dtoInfoMap as Map<UUID2<TDomain>, TDTOInfo>

            Result.success(dtoInfoMap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun findDTOInfosByField(field: String, searchValue: String): Result<List<TDTOInfo>> {
        return try {
            val entityInfoList = super.findEntitiesByField(field, searchValue)

            Result.success(entityInfoList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAllDTOInfo(): Result<Unit> {
        return try {
            if(super.findAllEntities().isEmpty())
                return Result.success(Unit)

            super.deleteDatabaseFile()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteDTOInfo(dtoInfo: TDTOInfo): Result<Unit> {
        return try {
            @Suppress("UNCHECKED_CAST")
            if(super.findEntityById(dtoInfo.id() as UUID2<TDomain>) == null)
                return Result.success(Unit)

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

    override suspend fun updateLookupTables() {
        // no-op
    }

    companion object {
        const val DEFAULT_FILE_API_DATABASE_FILENAME = "apiDB.json"
    }
}