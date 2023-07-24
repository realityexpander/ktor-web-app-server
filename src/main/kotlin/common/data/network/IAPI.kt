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
    suspend fun fetchDtoInfo(id: UUID2<TUUID2>): Result<TDTOInfo>
    suspend fun findAllUUID2ToDtoInfoMap(): Result<Map<UUID2<TUUID2>, TDTOInfo>>
    suspend fun addDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo>
    suspend fun updateDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo>
    suspend fun upsertDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo>
    suspend fun deleteDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo>
    suspend fun deleteAllDtoInfo(): Result<Unit>
}

