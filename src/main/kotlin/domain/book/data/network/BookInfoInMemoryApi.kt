package domain.book.data.network

import common.data.network.FakeHttpClient
import common.data.network.FakeURL
import common.data.network.InMemoryAPI
import common.uuid2.UUID2
import domain.book.Book

/**
 * BookInfoInMemoryApi
 *
 * Simulates an API using an in-memory database for the BookInfoDTO.
 *
 * Note: Use Domain-specific language to define the API methods.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12
 */

class BookInfoInMemoryApi(
    private val api: InMemoryAPI<Book, BookInfoDTO> = InMemoryAPI(
        FakeURL("memory://api.book.com"),
        FakeHttpClient()
    )
) : IBookInfoApi {

    override suspend fun fetchBookInfo(id: UUID2<Book>): Result<BookInfoDTO> {
        return api.fetchDtoInfo(id)
    }

    override suspend fun allBookInfos(): Result<Map<UUID2<Book>, BookInfoDTO>> {
        return api.findAllUUID2ToDtoInfoMap()
    }

    override suspend fun addBookInfo(bookInfo: BookInfoDTO): Result<BookInfoDTO> {
        return api.addDtoInfo(bookInfo)
    }

    override suspend fun updateBookInfo(bookInfo: BookInfoDTO): Result<BookInfoDTO> {
        return api.updateDtoInfo(bookInfo)
    }

    override suspend fun upsertBookInfo(bookInfo: BookInfoDTO): Result<BookInfoDTO> {
        return api.upsertDtoInfo(bookInfo)
    }

    override suspend fun deleteBookInfo(bookInfo: BookInfoDTO): Result<Unit> {
        return api.deleteDtoInfo(bookInfo)
    }

    override suspend fun deleteDatabase(): Result<Unit> {
        return api.deleteAllDtoInfo()
    }
}