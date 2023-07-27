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
 * Simulates an In-Memory local non-persisted database for BookInfoEntity.
 *
 * This class uses domain-specific language and wraps an implementation of the IDatabase interface
 * for BookInfoEntity.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

class BookInfoInMemoryDatabase constructor(
    val database: IDatabase<Book, BookInfoEntity> =
        InMemoryDatabase(
            FakeURL("memory://db.bookInfo.ram"),
            "user",
            "password"
        )
) : IBookInfoDatabase {
    override suspend fun fetchBookInfo(id: UUID2<Book>): Result<BookInfoEntity> {
        return database.fetchEntityInfo(id)
    }

    override suspend fun allBookInfos(): Result<Map<UUID2<Book>, BookInfoEntity>> {
        return database.findAllUUID2ToEntityInfoMap()
    }

    override suspend fun updateBookInfo(bookInfo: BookInfoEntity): Result<BookInfoEntity> {
        return database.updateEntityInfo(bookInfo)
    }

    override suspend fun addBookInfo(bookInfo: BookInfoEntity): Result<BookInfoEntity> {
        return database.addEntityInfo(bookInfo)
    }

    override suspend fun upsertBookInfo(bookInfo: BookInfoEntity): Result<BookInfoEntity> {
        return database.upsertEntityInfo(bookInfo)
    }

    override suspend fun deleteBookInfo(bookInfo: BookInfoEntity): Result<Unit> {
        return database.deleteEntityInfo(bookInfo)
    }

    override suspend fun deleteDatabase(): Result<Unit> {
        return database.deleteAllEntityInfo()
    }
}