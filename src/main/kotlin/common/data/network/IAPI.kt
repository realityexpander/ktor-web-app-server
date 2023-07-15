package common.data.network

import common.uuid2.IUUID2
import common.uuid2.UUID2

/**
 * IAPI is an interface for the API class.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */
// API uses Model.DTOs
interface IAPI<TUUID2 : IUUID2, TDTOInfo> {
    fun fetchDtoInfo(id: UUID2<TUUID2>): Result<TDTOInfo>
    fun fetchDtoInfo(uuidStr: String): Result<TDTOInfo>
    fun addDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo>
    fun updateDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo>
    fun upsertDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo>
    fun deleteDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo>
    fun findAllUUID2ToDtoInfoMap(): Map<UUID2<TUUID2>, TDTOInfo>
}
