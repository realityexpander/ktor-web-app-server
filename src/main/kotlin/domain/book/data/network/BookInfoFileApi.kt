package domain.book.data.network

import common.data.network.FileApi
import common.uuid2.UUID2
import domain.book.Book

/**
 * BookInfoFileApi
 *
 * Simulates a persistent network API using a local json file database for the BookInfoDTO.
 *
 * Note: Use Domain-specific language to define the API methods.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12
 */

class BookInfoFileApi(
    val bookInfoFileApiDatabaseFilename: String = DEFAULT_BOOKINFO_FILE_API_DATABASE_FILENAME,

    // Use a file Api to persist the book info
    private val api: FileApi<Book, BookInfoDTO> =
        FileApi(
            bookInfoFileApiDatabaseFilename,
            BookInfoDTO.serializer()
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

    override suspend fun deleteBookInfo(bookInfo: BookInfoDTO): Result<BookInfoDTO> {
        return api.deleteDtoInfo(bookInfo)
    }

    override suspend fun deleteDatabase(): Result<Unit> {
        return api.deleteAllDtoInfo()
    }

    companion object {
        const val DEFAULT_BOOKINFO_FILE_API_DATABASE_FILENAME = "bookInfoFileApiDB.json"
    }
}