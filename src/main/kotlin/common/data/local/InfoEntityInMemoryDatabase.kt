package common.data.local

import common.uuid2.IUUID2
import common.uuid2.UUID2
import common.data.network.FakeURL
import domain.common.data.info.local.InfoEntity

/**
 * InfoEntityInMemoryDatabase is an implementation of the IInfoEntityDatabase interface for the InfoEntity class.
 *
 * This class is a stub for a real database implementation.
 *
 * In a real implementation, this class would use a database driver to connect to a database for CRUD operations.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

@Suppress("UNUSED_PARAMETER")
class InfoEntityInMemoryDatabase<TUUID2 : IUUID2, TEntityInfo : InfoEntity> (
    fakeUrl: FakeURL = FakeURL("memory://hash.map"),
    user: String = "admin",
    password: String = "password",
    private val database: MutableMap<UUID2<TUUID2>, TEntityInfo> = mutableMapOf()
) : IInfoEntityDatabase<TUUID2, TEntityInfo> {

    override suspend fun fetchEntityInfo(id: UUID2<TUUID2>): Result<TEntityInfo> {
        // Simulate the request
        val infoResult: TEntityInfo = database[id]
            ?: return Result.failure(Exception("DB: Failed to get entityInfo, id: $id"))
        return Result.success(infoResult)
    }

    override suspend fun updateEntityInfo(entityInfo: TEntityInfo): Result<TEntityInfo> {
        // Simulate the request
        try {
            @Suppress("UNCHECKED_CAST")
            database[entityInfo.id() as UUID2<TUUID2>] = entityInfo
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return Result.success(entityInfo)
    }

    override suspend fun addEntityInfo(entityInfo: TEntityInfo): Result<TEntityInfo> {
        // Simulate the request
        @Suppress("UNCHECKED_CAST")
        if (database.containsKey(entityInfo.id() as UUID2<TUUID2>)) {
            return Result.failure(Exception("DB: Entity already exists, did you mean update?, entityInfo: $entityInfo"))
        }
        @Suppress("UNCHECKED_CAST")
        database[entityInfo.id() as UUID2<TUUID2>] = entityInfo
        return Result.success(entityInfo)
    }

    override suspend fun upsertEntityInfo(entityInfo: TEntityInfo): Result<TEntityInfo> {
        @Suppress("UNCHECKED_CAST")
        return if (database.containsKey(entityInfo.id() as UUID2<TUUID2>)) {
            updateEntityInfo(entityInfo)
        } else {
            addEntityInfo(entityInfo)
        }
    }

    override suspend fun deleteEntityInfo(entityInfo: TEntityInfo): Result<Unit> {
        @Suppress("UNCHECKED_CAST")
        return if (database.remove(entityInfo.id() as UUID2<TUUID2>) == null) {
            Result.failure(Exception("DB: Failed to delete entityInfo, entityInfo: $entityInfo"))
        } else
            Result.success(Unit)
    }

    override suspend fun findAllUUID2ToEntityInfoMap(): Result<Map<UUID2<TUUID2>, TEntityInfo>> {
        val map: MutableMap<UUID2<TUUID2>, TEntityInfo> = mutableMapOf()
        for ((key, value) in database.entries) {
            map[UUID2(key)] = value
        }
        return Result.success(map)
    }

    override suspend fun deleteAllEntityInfos(): Result<Unit> {
        database.clear()
        return Result.success(Unit)
    }

    override suspend fun findEntityInfosByField(field: String, searchValue: String): Result<List<TEntityInfo>> {
        val entityInfoList: MutableList<TEntityInfo> = mutableListOf()

        for ((_, value) in database.entries) {
            val member = value::class.members.find { entityField ->
                entityField.name == field
            }
            if (member != null) {
                val result = member.call(value).toString() // call extracts the field value
                if (result.contains(searchValue, ignoreCase = true)) {
                    entityInfoList.add(value)
                }
            }
        }

        return Result.success(entityInfoList)
    }
}