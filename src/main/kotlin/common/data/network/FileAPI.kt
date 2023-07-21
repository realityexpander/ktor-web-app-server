package common.data.network

import com.realityexpander.common.data.local.FileDatabase
import common.uuid2.IUUID2
import common.uuid2.UUID2
import kotlinx.serialization.KSerializer
import okhttp3.internal.toImmutableList

class FileAPI<TUUID2 : IUUID2, TDTOInfo : FileDatabase.HasId<UUID2<TUUID2>>>(
    apiDBFilename: String = DEFAULT_API_DB_FILENAME,
    serializer: KSerializer<TDTOInfo>
) : FileDatabase<UUID2<TUUID2>, TDTOInfo>(apiDBFilename, serializer),
    IAPI<TUUID2, TDTOInfo>
{
    override suspend fun fetchDtoInfo(id: UUID2<TUUID2>): Result<TDTOInfo> {
        return try {
            val dtoInfo = super.findEntityById(id)
                ?: return Result.failure(Exception("Entity not found"))

            Result.success(dtoInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchDtoInfo(uuidStr: String): Result<TDTOInfo> {
        return try {
            val uuid: UUID2<TUUID2> = UUID2.fromUUIDString(uuidStr)
            fetchDtoInfo(uuid)
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