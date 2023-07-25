package common.data.local

import com.realityexpander.common.data.local.JsonFileDatabase
import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.HasId
import domain.common.data.info.local.EntityInfo

/**
 * IDatabase is an interface for the Database class.
 *
 * This class should be wrapped and use domain-specific language for the EntityInfo class.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

interface IDatabase<TUUID2 : IUUID2, TEntity : EntityInfo> {
    suspend fun fetchEntityInfo(id: UUID2<TUUID2>): Result<TEntity>
    suspend fun findAllUUID2ToEntityInfoMap(): Result<Map<UUID2<TUUID2>, TEntity>>
    suspend fun updateEntityInfo(entityInfo: TEntity): Result<TEntity>
    suspend fun addEntityInfo(entityInfo: TEntity): Result<TEntity>
    suspend fun upsertEntityInfo(entityInfo: TEntity): Result<TEntity>
    suspend fun deleteEntityInfo(entityInfo: TEntity): Result<TEntity>
    suspend fun deleteAllEntityInfo(): Result<Unit>
}
