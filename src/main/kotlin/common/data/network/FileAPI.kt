package common.data.network

import com.realityexpander.common.data.local.FileDatabase
import common.uuid2.IUUID2
import common.uuid2.UUID2
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import okhttp3.internal.toImmutableList

/**
 * FileAPI
 *
 * A FileDatabase that implements the IAPI interface for DTOInfo.
 *
 * Simulates a REST API that is backed by a json file.
 * Data is persisted, so the database is not reset on each run.
 *
 * @param <TUUID2> The type of UUID2 to use for the database.
 * @param <TDTOInfo> The type of DTOInfo to use for the entities in the database.
 * @param apiDatabaseFilename The filename of the database.
 * @param serializer The kotlinx json serializer to use for the database entities.
 * @param fakeUrl The fake URL to use for the API
 * @param client The fake HTTP client to use for the API
 */

class FileAPI<TUUID2 : IUUID2, TDTOInfo : FileDatabase.HasId<UUID2<TUUID2>>>(
    apiDatabaseFilename: String = DEFAULT_API_DB_FILENAME,
    serializer: KSerializer<TDTOInfo>,
    private val fakeUrl: FakeURL = FakeURL("fakeHttp://fakeHost:22222"),
    private val client: FakeHttpClient = FakeHttpClient()
) : FileDatabase<UUID2<TUUID2>, TDTOInfo>(apiDatabaseFilename, serializer),
    IAPI<TUUID2, TDTOInfo>
{
    init {
        runBlocking {
            super.loadFileDatabase()
        }
    }

    override suspend fun fetchDtoInfo(id: UUID2<TUUID2>): Result<TDTOInfo> {
        return try {
            val dtoInfo = super.findEntityById(id)
                ?: return Result.failure(Exception("Entity not found"))

            Result.success(dtoInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun findAllUUID2ToDtoInfoMap(): Result<Map<UUID2<TUUID2>, TDTOInfo>> {
        return try {
            val dtoInfoMap =
                super.findAllEntities()
                    .toImmutableList()
                    .associateBy { it.id() }

            Result.success(dtoInfoMap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAllDtoInfo(): Result<Unit> {
        return try {
            super.deleteDatabaseFile()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo> {
        return try {
            super.deleteEntity(dtoInfo)

            Result.success(dtoInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun upsertDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo> {
        if(super.findEntityById(dtoInfo.id()) == null)
            super.addEntity(dtoInfo)
        else
            super.updateEntity(dtoInfo)

        return Result.success(dtoInfo)
    }

    override suspend fun updateDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo> {
        return try {
            super.updateEntity(dtoInfo)

            Result.success(dtoInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo> {
        super.addEntity(dtoInfo)

        return Result.success(dtoInfo)
    }

    override suspend fun updateLookupTables() {
        // no-op
    }

    companion object {
        const val DEFAULT_API_DB_FILENAME = "apiDB.json"
    }
}