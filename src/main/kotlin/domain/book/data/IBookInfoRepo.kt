package domain.book.data

import com.realityexpander.domain.book.data.local.IBookInfoDatabase
import common.log.ILog
import common.log.Log
import common.uuid2.UUID2
import domain.book.Book
import domain.book.data.local.EntityBookInfo
import domain.book.data.network.DTOBookInfo
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
    suspend fun deleteDatabase(): Result<Unit>

    /////////////////////////////////////////////////////
    // Debugging & Testing Methods                     //
    /////////////////////////////////////////////////////

    suspend fun upsertTestEntityBookInfoToDB(entityBookInfo: EntityBookInfo): Result<BookInfo> {
        val upsertResult: Result<EntityBookInfo> = this.bookInfoDatabase.upsertBookInfo(entityBookInfo)
        if (upsertResult.isFailure) {
            return Result.failure(upsertResult.exceptionOrNull() ?: Exception("upsertBookInfo DB Error"))
        }
        return Result.success(entityBookInfo.toDomainInfoDeepCopy())
    }

    suspend fun upsertTestDTOBookInfoToApi(dtoBookInfo: DTOBookInfo): Result<BookInfo> {
        val upsertResult: Result<DTOBookInfo> = bookInfoApi.upsertBookInfo(dtoBookInfo)
        if (upsertResult.isFailure) {
            return Result.failure(upsertResult.exceptionOrNull() ?: Exception("upsertBookInfo API Error"))
        }
        return Result.success(dtoBookInfo.toDomainInfoDeepCopy())
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
}