package common.data.network

import common.uuid2.IUUID2
import common.uuid2.UUID2

/**
 * IAPI is an interface for the API class.
 *
 * API uses Model.DTOs to transfer data to/from the Domain from API.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

interface IAPI<TUUID2 : IUUID2, TDTOInfo> {
    suspend fun fetchDTOInfo(id: UUID2<TUUID2>): Result<TDTOInfo>
    suspend fun findAllUUID2ToDTOInfoMap(): Result<Map<UUID2<TUUID2>, TDTOInfo>>
    suspend fun addDTOInfo(dtoInfo: TDTOInfo): Result<TDTOInfo>
    suspend fun updateDTOInfo(dtoInfo: TDTOInfo): Result<TDTOInfo>
    suspend fun upsertDTOInfo(dtoInfo: TDTOInfo): Result<TDTOInfo>
    suspend fun deleteDTOInfo(dtoInfo: TDTOInfo): Result<Unit>
    suspend fun deleteAllDTOInfo(): Result<Unit>
    suspend fun findDTOInfosByField(field: String, searchValue: String): Result<List<TDTOInfo>>
}

