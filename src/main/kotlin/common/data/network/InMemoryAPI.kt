package common.data.network

import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.info.network.DTOInfo
import okhttp3.internal.toImmutableMap

/**
 * InMemoryAPI is an implementation of the IAPI interface for the DTOInfo.
 *
 * @param <TUUID2> The type of the UUID2
 * @param <TDTOInfo> The type of the DTOInfo
 * @since 0.11
*/

class InMemoryAPI<TUUID2 : IUUID2, TDTOInfo : DTOInfo> (
    private val fakeUrl: FakeURL,
    private val client: FakeHttpClient
) : IAPI<TUUID2, TDTOInfo> {
    // Simulate a database accessed via a network API
    private val database: MutableMap<UUID2<TUUID2>, TDTOInfo> = mutableMapOf()

    internal constructor() : this(
        FakeURL("http://localhost:8080"),
        FakeHttpClient()
    )

    override fun fetchDtoInfo(id: UUID2<TUUID2>): Result<TDTOInfo> {
        // Simulate the network request
        return if (!database.containsKey(id)) {
            Result.failure(Exception("API: DTOInfo not found, id=$id"))
        } else Result.success(database[id]
            ?: return Result.failure(Exception("API: DTOInfo null, id=$id"))
        )
    }

    override fun fetchDtoInfo(uuidStr: String): Result<TDTOInfo> {
        return try {
            val uuid: UUID2<TUUID2> = UUID2.fromUUIDString<TUUID2>(uuidStr) as UUID2<TUUID2>
            fetchDtoInfo(uuid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun updateDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo> {
        try {
            // Simulate Network
            database[dtoInfo.id() as UUID2<TUUID2>] = dtoInfo
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return Result.success(dtoInfo)
    }

    override fun addDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo> {
        // Simulate Network
        if (database.containsKey(dtoInfo.id() as UUID2<TUUID2>)) {
            return Result.failure(Exception("API: DtoInfo already exists, use UPDATE, id=" + dtoInfo.id()))
        }
        database[dtoInfo.id() as UUID2<TUUID2>] = dtoInfo
        return Result.success(dtoInfo)
    }

    override fun upsertDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo> {
        // Simulate Network
        return if (database.containsKey(dtoInfo.id() as UUID2<TUUID2>)) {
            updateDtoInfo(dtoInfo)
        } else {
            addDtoInfo(dtoInfo)
        }
    }

    override fun deleteDtoInfo(dtoInfo: TDTOInfo): Result<TDTOInfo> {
        // Simulate Network
        return if (database.remove(dtoInfo.id() as UUID2<TUUID2>) == null) {
            Result.failure(Exception("API: Failed to delete DtoInfo"))
        } else Result.success(dtoInfo)
    }

    override fun findAllUUID2ToDtoInfoMap(): Map<UUID2<TUUID2>, TDTOInfo> {
        val map: MutableMap<UUID2<TUUID2>, TDTOInfo> = mutableMapOf()

        // Simulate Network
        for ((key, value) in database.entries) {
            map[UUID2(key)] = value
        }

            return map.toImmutableMap()
//        return map.toMap()
    }
}