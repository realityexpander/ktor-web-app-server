package common.data.local

import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.Model

interface IJsonDatabase<TDomain : IUUID2, TEntity : Model> {
    suspend fun findAllEntities(): List<TEntity>
    suspend fun findEntityById(id: UUID2<TDomain>): TEntity?
    suspend fun findEntitiesByField(field: String, rawSearchValue: String): List<TEntity>
    suspend fun addEntity(entity: TEntity): TEntity
    suspend fun updateEntity(entity: TEntity): TEntity?
    suspend fun upsertEntity(entity: TEntity): TEntity?
    suspend fun deleteEntity(entity: TEntity)
    suspend fun deleteEntityById(id: UUID2<TDomain>)
    suspend fun deleteDatabase()
}