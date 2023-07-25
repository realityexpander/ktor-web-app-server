package com.realityexpander.domain.book.data.local

import com.realityexpander.common.data.network.FileDatabase
import com.realityexpander.domain.book.data.local.IBookInfoDatabase.Companion.DEFAULT_BOOKINFO_FILE_DATABASE_FILENAME
import common.data.local.IDatabase
import common.uuid2.UUID2
import domain.book.Book
import domain.book.data.local.EntityBookInfo

/**
 * BookInfoFileDatabase
 *
 * Simulates a persistent database using a local file database for the EntityBookInfo.
 *
 * Note: Use Domain-specific language to define the API methods.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12
 */

class BookInfoFileDatabase(
    bookInfoFileDatabaseFilename: String = DEFAULT_BOOKINFO_FILE_DATABASE_FILENAME,

    // Use a file database to persist the book info
    private val database: IDatabase<Book, EntityBookInfo> =
        FileDatabase(
            bookInfoFileDatabaseFilename,
            EntityBookInfo.serializer()
        )
) : IBookInfoDatabase {

        override suspend fun fetchBookInfo(id: UUID2<Book>): Result<EntityBookInfo> {
            return database.fetchEntityInfo(id)
        }

        override suspend fun allBookInfos(): Result<Map<UUID2<Book>, EntityBookInfo>> {
            return database.findAllUUID2ToEntityInfoMap()
        }

        override suspend fun addBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo> {
            return database.addEntityInfo(bookInfo)
        }

        override suspend fun updateBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo> {
            return database.updateEntityInfo(bookInfo)
        }

        override suspend fun upsertBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo> {
            return database.upsertEntityInfo(bookInfo)
        }

        override suspend fun deleteBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo> {
            return database.deleteEntityInfo(bookInfo)
        }

        override suspend fun deleteDatabase(): Result<Unit> {
            return database.deleteAllEntityInfo()
        }
}
