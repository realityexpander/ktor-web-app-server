package domain.book.data.network

import common.data.network.IAPI
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

abstract class BookInfoApi(
    val api: IAPI<Book, BookInfoDTO>
) : IBookInfoApi {
    override suspend fun fetchBookInfo(id: UUID2<Book>): Result<BookInfoDTO> {
        return api.fetchDTOInfo(id)
    }

    override suspend fun allBookInfos(): Result<Map<UUID2<Book>, BookInfoDTO>> {
        return api.findAllUUID2ToDTOInfoMap()
    }

    override suspend fun addBookInfo(bookInfo: BookInfoDTO): Result<BookInfoDTO> {
        return api.addDTOInfo(bookInfo)
    }

    override suspend fun updateBookInfo(bookInfo: BookInfoDTO): Result<BookInfoDTO> {
        return api.updateDTOInfo(bookInfo)
    }

    override suspend fun upsertBookInfo(bookInfo: BookInfoDTO): Result<BookInfoDTO> {
        return api.upsertDTOInfo(bookInfo)
    }

    override suspend fun deleteBookInfo(bookInfo: BookInfoDTO): Result<Unit> {
        return api.deleteDTOInfo(bookInfo)
    }

    override suspend fun deleteDatabase(): Result<Unit> {
        return api.deleteAllDTOInfo()
    }
}