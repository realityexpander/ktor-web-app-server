package domain.book.data.network

import common.uuid2.UUID2
import domain.book.Book

interface IBookInfoApi {
    suspend fun fetchBookInfo(id: UUID2<Book>): Result<BookInfoDTO>
    suspend fun allBookInfos(): Result<Map<UUID2<Book>, BookInfoDTO>>
    suspend fun addBookInfo(bookInfo: BookInfoDTO): Result<BookInfoDTO>
    suspend fun updateBookInfo(bookInfo: BookInfoDTO): Result<BookInfoDTO>
    suspend fun upsertBookInfo(bookInfo: BookInfoDTO): Result<BookInfoDTO>
    suspend fun deleteBookInfo(bookInfo: BookInfoDTO): Result<Unit>
    suspend fun deleteDatabase(): Result<Unit>
}