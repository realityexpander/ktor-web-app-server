package common.data.network

import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.info.network.InfoDTO
import okhttp3.internal.toImmutableMap

/**
 * InMemoryAPI is an implementation of the IAPI interface for the InfoDTO.
 *
 * Simulates a REST API that is backed by a in-memory database.
 * No data is persisted, so the database is reset on each run.
 *
 * Uses type-safe UUID2 for the id.
 *
 * @param <TUUID2> The type of the UUID2
 * @param <TDTOInfo> The type of the InfoDTO
 * @param fakeUrl The fake URL to use for the API
 * @param client The fake HTTP client to use for the API
 * @since 0.12 Kotlin conversion
*/

class InMemoryAPI<TUUID2 : IUUID2, TDTOInfo : InfoDTO> (
    private val fakeUrl: FakeURL = FakeURL("fakeHttp://fakeHost:22222"),
    private val client: FakeHttpClient = FakeHttpClient()
) : IAPI<TUUID2, TDTOInfo> {

    // Simulate a database accessed via a network API
    private val remoteDatabase: MutableMap<UUID2<TUUID2>, TDTOInfo> = mutableMapOf()

    override suspend fun fetchDtoInfo(id: UUID2<TUUID2>): Result<TDTOInfo> {
        // Simulate the network request
        return if (!remoteDatabase.containsKey(id)) {
            Result.failure(Exception("API: InfoDTO not found, id=$id"))
        } else Result.success(remoteDatabase[id]
            ?:
            return Result.failure(Exception("API: InfoDTO null, id=$id"))
        )
    }

    override suspend fun updateDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo> {
        try {
            // Simulate Network
            @Suppress("UNCHECKED_CAST")
            remoteDatabase[dtoInfo.id() as UUID2<TUUID2>] = dtoInfo
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return Result.success(dtoInfo)
    }

    override suspend fun addDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo> {
        // Simulate Network
        @Suppress("UNCHECKED_CAST")
        if (remoteDatabase.containsKey(dtoInfo.id() as UUID2<TUUID2>)) {
            return Result.failure(Exception("API: DtoInfo already exists, use UPDATE, id=" + dtoInfo.id()))
        }

        @Suppress("UNCHECKED_CAST")
        remoteDatabase[dtoInfo.id() as UUID2<TUUID2>] = dtoInfo
        return Result.success(dtoInfo)
    }

    override suspend fun upsertDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo> {
        // Simulate Network
        @Suppress("UNCHECKED_CAST")
        return if (remoteDatabase.containsKey(dtoInfo.id() as UUID2<TUUID2>)) {
            updateDtoInfo(dtoInfo)
        } else {
            addDtoInfo(dtoInfo)
        }
    }

    override suspend fun deleteDtoInfo(dtoInfo: TDTOInfo): Result<Unit> {
        // Simulate Network
        @Suppress("UNCHECKED_CAST")
        return if (remoteDatabase.remove(dtoInfo.id() as UUID2<TUUID2>) == null) {
            Result.failure(Exception("API: Failed to delete DtoInfo"))
        } else
            Result.success(Unit)
    }

    override suspend fun findAllUUID2ToDtoInfoMap(): Result<Map<UUID2<TUUID2>, TDTOInfo>> {
        val map: MutableMap<UUID2<TUUID2>, TDTOInfo> = mutableMapOf()
        
        // Simulate Network
        for ((key, value) in remoteDatabase.entries) {
            map[UUID2(key)] = value
        }

        return Result.success(map.toImmutableMap())
    }

    override suspend fun deleteAllDtoInfo(): Result<Unit> {
        // Simulate Network
        remoteDatabase.clear()
        return Result.success(Unit)
    }
}