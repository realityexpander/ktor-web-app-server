package domain.book.data.network

import common.data.network.FakeURL
import common.data.network.FakeHttpClient
import common.data.network.InMemoryAPI
import common.uuid2.UUID2
import domain.book.Book

/**
 * BookInfoInMemoryApi
 *
 * Simulates an API using an in-memory database for the DTOBookInfo.
 *
 * Note: Use Domain-specific language to define the API methods.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12
 */

class BookInfoInMemoryApi(api: InMemoryAPI<Book, DTOBookInfo>) {
    private val api: InMemoryAPI<Book, DTOBookInfo>

    constructor() : this(
        InMemoryAPI(
            FakeURL("memory://api.book.com"),
            FakeHttpClient()
        )
    )

    init {
        this.api = api
    }

    suspend fun fetchBookInfo(id: String): Result<DTOBookInfo> {
        return api.fetchDtoInfo(id)
    }

    suspend fun fetchBookInfo(id: UUID2<Book>): Result<DTOBookInfo> {
        return api.fetchDtoInfo(id)
    }

    suspend fun addBookInfo(bookInfo: DTOBookInfo): Result<DTOBookInfo> {
        return api.addDtoInfo(bookInfo)
    }

    suspend fun updateBookInfo(bookInfo: DTOBookInfo): Result<DTOBookInfo> {
        return api.updateDtoInfo(bookInfo)
    }

    suspend fun upsertBookInfo(bookInfo: DTOBookInfo): Result<DTOBookInfo> {
        return api.upsertDtoInfo(bookInfo)
    }

    suspend fun deleteBookInfo(bookInfo: DTOBookInfo): Result<DTOBookInfo> {
        return api.deleteDtoInfo(bookInfo)
    }

    suspend fun allBookInfos(): Result<Map<UUID2<Book>, DTOBookInfo>> {
        return api.findAllUUID2ToDtoInfoMap()
    }
}
