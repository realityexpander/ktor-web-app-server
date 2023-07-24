package domain.book.data

import common.uuid2.UUID2
import domain.book.Book
import domain.common.data.repo.IRepo

/**
 * IBookInfoRepo is an interface for the BookInfoRepo class.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

interface IBookInfoRepo : IRepo {
    suspend fun fetchBookInfo(id: UUID2<Book>): Result<BookInfo>
    suspend fun addBookInfo(bookInfo: BookInfo): Result<BookInfo>
    suspend fun updateBookInfo(bookInfo: BookInfo): Result<BookInfo>
    suspend fun upsertBookInfo(bookInfo: BookInfo): Result<BookInfo>
    suspend fun deleteDatabase(): Result<Unit>
}