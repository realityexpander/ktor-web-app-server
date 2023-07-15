package domain.book.data

import common.uuid2.UUID2
import domain.book.Book
import domain.common.data.repo.IRepo

/**
 * IBookInfoRepo is an interface for the BookInfoRepo class.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */
interface IBookInfoRepo : IRepo {
    fun fetchBookInfo(id: UUID2<Book>): Result<BookInfo>
    fun addBookInfo(bookInfo: BookInfo): Result<BookInfo>
    fun updateBookInfo(bookInfo: BookInfo): Result<BookInfo>
    fun upsertBookInfo(bookInfo: BookInfo): Result<BookInfo>
}