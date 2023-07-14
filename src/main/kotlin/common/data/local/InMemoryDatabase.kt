package common.data.local

import common.uuid2.IUUID2
import common.uuid2.UUID2
import common.data.network.FakeURL
import domain.common.data.info.local.EntityInfo

/**
 * InMemoryDatabase is an implementation of the IDatabase interface for the EntityInfo class.
 *
 * This class is a stub for a real database implementation.
 *
 * In a real implementation, this class would use a database driver to connect to a database for CRUD operations.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */


class InMemoryDatabase <TEntity : EntityInfo, TUUID2 : IUUID2> (  // todo reverse the type parameters to <TEntity : EntityInfo, TUUID2 : IUUID2>
    fakeUrl: FakeURL,
    user: String,
    password: String
) : IDatabase<TUUID2, TEntity> {

    private val fakeUrl: FakeURL
    private val user: String
    private val password: String

    // Simulate a local database
    private val database: MutableMap<UUID2<TUUID2>, TEntity> = mutableMapOf()

    init {
        this.fakeUrl = fakeUrl
        this.user = user
        this.password = password
    }

    internal constructor() : this(FakeURL("memory://hash.map"), "admin", "password")

    override fun fetchEntityInfo(id: UUID2<TUUID2>): Result<TEntity> {
        // Simulate the request
        val infoResult: TEntity = database.get(id)
            ?: return Result.failure(Exception("DB: Failed to get entityInfo, id: $id"))
        return Result.success(infoResult)
    }

    override fun fetchEntityInfo(id: String): Result<TEntity> {
        return try {
            val uuid: UUID2<TUUID2> = UUID2.fromUUIDString<TUUID2>(id)
            fetchEntityInfo(uuid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun updateEntityInfo(entityInfo: TEntity): Result<TEntity> {
        // Simulate the request
        try {
            database[entityInfo.id() as UUID2<TUUID2>] = entityInfo
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return Result.success(entityInfo)
    }

    override fun addEntityInfo(entityInfo: TEntity): Result<TEntity> {
        // Simulate the request
        if (database.containsKey(entityInfo.id() as UUID2<TUUID2>)) {
            return Result.failure(Exception("DB: Entity already exists, did you mean update?, entityInfo: $entityInfo"))
        }
        database[entityInfo.id() as UUID2<TUUID2>] = entityInfo
        return Result.success(entityInfo)
    }

    override fun upsertEntityInfo(entityInfo: TEntity): Result<TEntity> {
        return if (database.containsKey(entityInfo.id() as UUID2<TUUID2>)) {
            updateEntityInfo(entityInfo)
        } else {
            addEntityInfo(entityInfo)
        }
    }

    override fun deleteEntityInfo(entityInfo: TEntity): Result<TEntity> {
        return if (database.remove(entityInfo.id() as UUID2<TUUID2>) == null) {
            Result.failure(Exception("DB: Failed to delete entityInfo, entityInfo: $entityInfo"))
        } else
            Result.success(entityInfo)
    }

    override fun findAllUUID2ToEntityInfoMap(): Map<UUID2<TUUID2>, TEntity> {
        val map: MutableMap<UUID2<TUUID2>, TEntity> = java.util.HashMap<UUID2<TUUID2>, TEntity>()
        for ((key, value) in database.entries) {
            map[UUID2(key)] = value
        }
        return map
    }
}