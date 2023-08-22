package com.realityexpander.domain.book.data.local

import com.realityexpander.common.data.network.FileDatabase
import domain.book.data.local.BookInfoEntity

/**
 * BookInfoFileDatabase
 *
 * Simulates a persistent database using a local file database for the BookInfoEntity.
 *
 * Note: Use Domain-specific language to define the API methods.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12
 */

class BookInfoFileDatabase(
    bookInfoFileDatabaseFilename: String = DEFAULT_BOOKINFO_FILE_DATABASE_FILENAME,
): BookInfoDatabase(
    // Use a file database to persist the book info
    database =
        FileDatabase(
        bookInfoFileDatabaseFilename,
        BookInfoEntity.serializer()
    )
) {
    companion object {
        const val DEFAULT_BOOKINFO_FILE_DATABASE_FILENAME: String = "bookInfoFileDatabaseDB.json"
    }
}

//class BookInfoFileDatabase1(
//    bookInfoFileDatabaseFilename: String = DEFAULT_BOOKINFO_FILE_DATABASE_FILENAME,
//
//    // Use a file database to persist the book info
//    private val database: IDatabase<Book, BookInfoEntity> =
//        FileDatabase(
//            bookInfoFileDatabaseFilename,
//            BookInfoEntity.serializer()
//        )
//) : IBookInfoDatabase {
//
//    override suspend fun fetchBookInfo(id: UUID2<Book>): Result<BookInfoEntity> {
//        return database.fetchEntityInfo(id)
//    }
//
//    override suspend fun allBookInfos(): Result<Map<UUID2<Book>, BookInfoEntity>> {
//        return database.findAllUUID2ToEntityInfoMap()
//    }
//
//    override suspend fun addBookInfo(bookInfo: BookInfoEntity): Result<BookInfoEntity> {
//        return database.addEntityInfo(bookInfo)
//    }
//
//    override suspend fun updateBookInfo(bookInfo: BookInfoEntity): Result<BookInfoEntity> {
//        return database.updateEntityInfo(bookInfo)
//    }
//
//    override suspend fun upsertBookInfo(bookInfo: BookInfoEntity): Result<BookInfoEntity> {
//        return database.upsertEntityInfo(bookInfo)
//    }
//
//    override suspend fun deleteBookInfo(bookInfo: BookInfoEntity): Result<Unit> {
//        return database.deleteEntityInfo(bookInfo)
//    }
//
//    override suspend fun deleteDatabase(): Result<Unit> {
//        return database.deleteAllEntityInfos()
//    }
//
//    override suspend fun findBookInfosByField(field: String, searchTerm: String): Result<List<BookInfoEntity>> {
//        return database.findEntityInfosByField(field, searchTerm)
//    }
//
//    override suspend fun findBookInfosByTitle(searchTerm: String): Result<List<BookInfoEntity>> {
//        return database.findEntityInfosByField("title", searchTerm)
//    }
//
//    companion object {
//        const val DEFAULT_BOOKINFO_FILE_DATABASE_FILENAME: String = "bookInfoFileDatabaseDB.json"
//    }
//}
