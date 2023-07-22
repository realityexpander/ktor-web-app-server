package domain.book.data.local

import common.data.local.IDatabase
import common.data.local.InMemoryDatabase
import common.data.network.FakeURL
import common.uuid2.UUID2
import domain.book.Book

/**
 * BookInfoInMemoryDatabase
 *
 * Simulates an In-Memory local database for BookInfo.
 *
 * This class uses domain-specific language and wraps an implementation of the IDatabase interface
 * for EntityBookInfo.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

class BookInfoInMemoryDatabase constructor(
    val database: IDatabase<Book, EntityBookInfo> = InMemoryDatabase(
            FakeURL("memory://db.book.com"),
            "user",
            "password"
        )
) {
    suspend fun fetchBookInfo(id: UUID2<Book>): Result<EntityBookInfo> {
        return database.fetchEntityInfo(id)
    }

    suspend fun updateBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo> {
        return database.updateEntityInfo(bookInfo)
    }

    suspend fun addBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo> {
        return database.addEntityInfo(bookInfo)
    }

    suspend fun upsertBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo> {
        return database.upsertEntityInfo(bookInfo)
    }

    suspend fun deleteBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo> {
        return database.deleteEntityInfo(bookInfo)
    }

    suspend fun allBookInfos(): Map<UUID2<Book>, EntityBookInfo> {
        return database.findAllUUID2ToEntityInfoMap()
    }
}