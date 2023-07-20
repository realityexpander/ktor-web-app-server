package common.data.local

import common.uuid2.IUUID2
import common.uuid2.UUID2
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
    fun fetchEntityInfo(id: UUID2<TUUID2>): Result<TEntity>
    fun fetchEntityInfo(id: String): Result<TEntity>
    fun updateEntityInfo(entityInfo: TEntity): Result<TEntity>
    fun addEntityInfo(entityInfo: TEntity): Result<TEntity>
    fun upsertEntityInfo(entityInfo: TEntity): Result<TEntity>
    fun deleteEntityInfo(entityInfo: TEntity): Result<TEntity>
    fun findAllUUID2ToEntityInfoMap(): Map<UUID2<TUUID2>, TEntity>
}
