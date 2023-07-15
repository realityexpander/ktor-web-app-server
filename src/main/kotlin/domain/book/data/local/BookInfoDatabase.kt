package domain.book.data.local

import common.data.local.IDatabase
import common.data.local.InMemoryDatabase
import common.data.network.FakeURL
import common.uuid2.UUID2
import domain.book.Book

/**
 * BookInfoDatabase
 *
 * This class uses domain specific language and wraps an implementation of the IDatabase interface
 * for EntityBookInfo.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */

class BookInfoDatabase constructor(
    val database: IDatabase<Book, EntityBookInfo> = InMemoryDatabase(
            FakeURL("memory://db.book.com"),
            "user",
            "password"
        )
) {
    fun fetchBookInfo(id: UUID2<Book>): Result<EntityBookInfo> {
        return database.fetchEntityInfo(id)
    }

    fun updateBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo> {
        return database.updateEntityInfo(bookInfo)
    }

    fun addBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo> {
        return database.addEntityInfo(bookInfo)
    }

    fun upsertBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo> {
        return database.upsertEntityInfo(bookInfo)
    }

    fun deleteBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo> {
        return database.deleteEntityInfo(bookInfo)
    }

    val allBookInfos: Map<UUID2<Book>, EntityBookInfo>
        get() = database.findAllUUID2ToEntityInfoMap()
}