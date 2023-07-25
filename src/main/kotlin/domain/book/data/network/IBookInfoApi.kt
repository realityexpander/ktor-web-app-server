package domain.book.data.network

import common.uuid2.UUID2
import domain.book.Book

interface IBookInfoApi {
    suspend fun fetchBookInfo(id: UUID2<Book>): Result<DTOBookInfo>
    suspend fun allBookInfos(): Result<Map<UUID2<Book>, DTOBookInfo>>
    suspend fun addBookInfo(bookInfo: DTOBookInfo): Result<DTOBookInfo>
    suspend fun updateBookInfo(bookInfo: DTOBookInfo): Result<DTOBookInfo>
    suspend fun upsertBookInfo(bookInfo: DTOBookInfo): Result<DTOBookInfo>
    suspend fun deleteBookInfo(bookInfo: DTOBookInfo): Result<DTOBookInfo>
    suspend fun deleteDatabase(): Result<Unit>
}