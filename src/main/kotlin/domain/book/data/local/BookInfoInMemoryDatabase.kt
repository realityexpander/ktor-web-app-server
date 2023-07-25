package domain.book.data.local

import com.realityexpander.domain.book.data.local.IBookInfoDatabase
import common.data.local.IDatabase
import common.data.local.InMemoryDatabase
import common.data.network.FakeURL
import common.uuid2.UUID2
import domain.book.Book

/**
 * BookInfoInMemoryDatabase
 *
 * Simulates an In-Memory local non-persisted database for EntityBookInfo.
 *
 * This class uses domain-specific language and wraps an implementation of the IDatabase interface
 * for EntityBookInfo.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

class BookInfoInMemoryDatabase constructor(
    val database: IDatabase<Book, EntityBookInfo> =
        InMemoryDatabase(
            FakeURL("memory://db.bookInfo.ram"),
            "user",
            "password"
        )
) : IBookInfoDatabase {
    override suspend fun fetchBookInfo(id: UUID2<Book>): Result<EntityBookInfo> {
        return database.fetchEntityInfo(id)
    }

    override suspend fun allBookInfos(): Result<Map<UUID2<Book>, EntityBookInfo>> {
        return database.findAllUUID2ToEntityInfoMap()
    }

    override suspend fun updateBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo> {
        return database.updateEntityInfo(bookInfo)
    }

    override suspend fun addBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo> {
        return database.addEntityInfo(bookInfo)
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