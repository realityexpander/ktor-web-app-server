package domain.book.data

import com.realityexpander.domain.book.data.local.IBookInfoDatabase
import common.log.ILog
import common.log.Log
import common.uuid2.UUID2
import domain.book.Book
import domain.book.data.local.BookInfoEntity
import domain.book.data.network.BookInfoDTO
import domain.book.data.network.IBookInfoApi
import domain.common.data.repo.IRepo

/**
 * IBookInfoRepo is an interface for the BookInfoInMemoryRepo class.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

interface IBookInfoRepo : IRepo {
    val bookInfoApi: IBookInfoApi
    val bookInfoDatabase: IBookInfoDatabase

    suspend fun fetchBookInfo(id: UUID2<Book>): Result<BookInfo>
    suspend fun addBookInfo(bookInfo: BookInfo): Result<BookInfo>
    suspend fun updateBookInfo(bookInfo: BookInfo): Result<BookInfo>
    suspend fun upsertBookInfo(bookInfo: BookInfo): Result<BookInfo>
    suspend fun deleteBookInfo(bookInfo: BookInfo): Result<Unit>
    suspend fun deleteDatabase(): Result<Unit>

    /////////////////////////////////////////////////////
    // Debugging & Testing Methods                     //
    /////////////////////////////////////////////////////

    suspend fun upsertTestEntityBookInfoToDB(bookInfoEntity: BookInfoEntity): Result<BookInfo> {
        val upsertResult: Result<BookInfoEntity> = this.bookInfoDatabase.upsertBookInfo(bookInfoEntity)
        if (upsertResult.isFailure) {
            return Result.failure(upsertResult.exceptionOrNull() ?: Exception("upsertBookInfo DB Error"))
        }
        return Result.success(bookInfoEntity.toDomainInfoDeepCopy())
    }

    suspend fun upsertTestDTOBookInfoToApi(bookInfoDTO: BookInfoDTO): Result<BookInfo> {
        val upsertResult: Result<BookInfoDTO> = bookInfoApi.upsertBookInfo(bookInfoDTO)
        if (upsertResult.isFailure) {
            return Result.failure(upsertResult.exceptionOrNull() ?: Exception("upsertBookInfo API Error"))
        }
        return Result.success(bookInfoDTO.toDomainInfoDeepCopy())
    }

    suspend fun printDB(log: ILog = Log()) {
        val entriesResult = bookInfoDatabase.allBookInfos()
        if (entriesResult.isFailure) {
            log.d(this, "DB is empty")
            return
        }
        val entries = entriesResult.getOrThrow()
        for ((key, value) in entries) {
            log.d(this, "$key = $value")
        }
    }

    suspend fun printAPI(log: ILog = Log()) {
        val entries = bookInfoApi.allBookInfos().getOrNull()?.entries
        if (entries == null) {
            log.d(this, "API is empty")
            return
        }

        for ((key, value) in entries) {
            log.d(this, "$key = $value")
        }
    }

    suspend fun findBookInfosByTitle(title: String): Result<List<BookInfo>> {
        val result = bookInfoDatabase.findBookInfosByTitle(title)
        if (result.isFailure) {
            return Result.failure(result.exceptionOrNull() ?: Exception("findBookInfosByTitle Error"))
        }

        val bookInfoList = result.getOrThrow()
        return Result.success(bookInfoList.map { it.toDomainInfoDeepCopy() })
    }
}