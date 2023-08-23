package domain.book.data.network

import common.data.network.InfoDTOFileApi
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
    bookInfoFileApiDatabaseFilename: String = DEFAULT_BOOKINFO_FILE_API_DATABASE_FILENAME,
) : BookInfoApi(
    // Use a file Api to persist the book info
    api = InfoDTOFileApi<Book, BookInfoDTO>(
            bookInfoFileApiDatabaseFilename,
            BookInfoDTO.serializer()
        )
) {
    companion object {
        const val DEFAULT_BOOKINFO_FILE_API_DATABASE_FILENAME = "bookInfoFileApiDB.json"
    }
}

//class BookInfoFileApi1(
//    private val bookInfoFileApiDatabaseFilename: String = DEFAULT_BOOKINFO_FILE_API_DATABASE_FILENAME,
//
//    // Use a file Api to persist the book info
//    private val api: InfoDTOFileApi<Book, BookInfoDTO> =
//        InfoDTOFileApi(
//            bookInfoFileApiDatabaseFilename,
//            BookInfoDTO.serializer()
//        )
//) : IBookInfoApi {
//
//    override suspend fun fetchBookInfo(id: UUID2<Book>): Result<BookInfoDTO> {
//        return api.fetchDTOInfo(id)
//    }
//
//    override suspend fun allBookInfos(): Result<Map<UUID2<Book>, BookInfoDTO>> {
//        return api.findAllUUID2ToDTOInfoMap()
//    }
//
//    override suspend fun addBookInfo(bookInfo: BookInfoDTO): Result<BookInfoDTO> {
//        return api.addDTOInfo(bookInfo)
//    }
//
//    override suspend fun updateBookInfo(bookInfo: BookInfoDTO): Result<BookInfoDTO> {
//        return api.updateDTOInfo(bookInfo)
//    }
//
//    override suspend fun upsertBookInfo(bookInfo: BookInfoDTO): Result<BookInfoDTO> {
//        return api.upsertDTOInfo(bookInfo)
//    }
//
//    override suspend fun deleteBookInfo(bookInfo: BookInfoDTO): Result<Unit> {
//        return api.deleteDTOInfo(bookInfo)
//    }
//
//    override suspend fun deleteDatabase(): Result<Unit> {
//        return api.deleteAllDTOInfo()
//    }
//
//    companion object {
//        const val DEFAULT_BOOKINFO_FILE_API_DATABASE_FILENAME = "bookInfoFileApiDB.json"
//    }
//}