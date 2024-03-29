package domain.book.data

import com.realityexpander.domain.book.data.local.BookInfoFileDatabase
import com.realityexpander.domain.book.data.local.BookInfoRedisDatabase
import com.realityexpander.domain.book.data.local.IBookInfoDatabase
import com.realityexpander.domain.book.data.network.BookInfoRedisApi
import common.log.ILog
import common.log.Log
import common.uuid2.UUID2
import domain.book.Book
import domain.book.data.local.BookInfoEntity
import domain.book.data.local.BookInfoInMemoryDatabase
import domain.book.data.network.BookInfoDTO
import domain.book.data.network.BookInfoFileApi
import domain.book.data.network.IBookInfoApi
import domain.common.data.repo.Repo

/**
 * BookInfoRepo is a repository for the BookInfo class.
 *
 * Business logic for Book Repo (simple CRUD operations; converts to/from DTOs/Entities/Domains)
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

open class BookInfoRepo(
    override val log: ILog = Log(),

    private val bookInfoApiName: String = BookInfoRedisApi.DEFAULT_BOOKINFO_API_DATABASE_NAME,
    override val bookInfoApi: IBookInfoApi = BookInfoRedisApi(bookInfoApiName),
    private val bookInfoDatabaseName: String = BookInfoRedisDatabase.DEFAULT_BOOKINFO_DATABASE_NAME,
    override val bookInfoDatabase: IBookInfoDatabase = BookInfoRedisDatabase(bookInfoDatabaseName)
) : Repo(log), IBookInfoRepo {

    override suspend fun fetchBookInfo(id: UUID2<Book>): Result<BookInfo> {
        log.d(this, "bookId $id")

        // Make the request to API
        val fetchBookApiResult: Result<BookInfoDTO> = bookInfoApi.fetchBookInfo(id)
        if (fetchBookApiResult.isFailure) {

            // API failed, now try to get from cached DB
            val fetchBookDBResult: Result<BookInfoEntity> = bookInfoDatabase.fetchBookInfo(id)
            if (fetchBookDBResult.isFailure) {
                return Result.failure(fetchBookDBResult.exceptionOrNull() ?: Exception("getBookInfo Error"))
            }

            val bookInfo: BookInfoEntity = fetchBookDBResult.getOrNull()
                ?: return Result.failure(Exception("getBookInfo Error"))
            return Result.success(bookInfo.toDomainInfoDeepCopy())
        }

        // Convert to Domain Info
        val bookInfo: BookInfo =
            if (fetchBookApiResult.isSuccess)
                fetchBookApiResult.getOrNull()?.toDomainInfoDeepCopy()
                    ?: return Result.failure(Exception("Model conversion error"))
            else
                return Result.failure(fetchBookApiResult.exceptionOrNull() ?: Exception("Model conversion error"))


        // Cache to Local DB
        val updateDBResult: Result<BookInfoEntity> = bookInfoDatabase.updateBookInfo(bookInfo.toInfoEntity())
        if (updateDBResult.isFailure) {
            return Result.failure(updateDBResult.exceptionOrNull() ?: Exception("updateBookInfo Error"))
        }

        return Result.success(bookInfo)
    }

    override suspend fun updateBookInfo(bookInfo: BookInfo): Result<BookInfo> {
        log.d(this, "bookInfo: $bookInfo")
        val saveResult = saveBookInfoToApiAndDB(bookInfo, UpdateKind.UPDATE)

        if (saveResult.isFailure) {
            return Result.failure(saveResult.exceptionOrNull() ?: Exception("updateBookInfo Error"))
        }

        return saveResult
    }

    override suspend fun addBookInfo(bookInfo: BookInfo): Result<BookInfo> {
        log.d(this, "bookInfo: $bookInfo")
        val saveResult = saveBookInfoToApiAndDB(bookInfo, UpdateKind.ADD)

        if (saveResult.isFailure) {
            return Result.failure(saveResult.exceptionOrNull() ?: Exception("addBookInfo Error"))
        }

        return saveResult
    }

    override suspend fun upsertBookInfo(bookInfo: BookInfo): Result<BookInfo> {
        log.d(this, "bookId: " + bookInfo.id())
        val saveResult = saveBookInfoToApiAndDB(bookInfo, UpdateKind.UPSERT)

        if (saveResult.isFailure) {
            return Result.failure(saveResult.exceptionOrNull() ?: Exception("upsertBookInfo Error"))
        }

        return saveResult
    }

    override suspend fun deleteBookInfo(bookInfo: BookInfo): Result<Unit> {
        log.d(this, "bookId: " + bookInfo.id())

        // Make the API request to delete
        val apiChangeResult: Result<Unit> = bookInfoApi.deleteBookInfo(bookInfo.toInfoDTO())
        if (apiChangeResult.isFailure) {
            return Result.failure(apiChangeResult.exceptionOrNull() ?: Exception("deleteBookInfo Api Error, bookId: " + bookInfo.id()))
        }

        // Delete from Local DB
        val dbChangeResult: Result<Unit> = bookInfoDatabase.deleteBookInfo(bookInfo.toInfoEntity())
        if (dbChangeResult.isFailure) {
            return Result.failure(dbChangeResult.exceptionOrNull() ?: Exception("deleteBookInfo DB Error, bookId: " + bookInfo.id()))
        }

        return Result.success(Unit)
    }

    override suspend fun deleteDatabase(): Result<Unit> {
        log.d(this, "deleteDatabase")

        // Simulate network/database
        bookInfoApi.deleteDatabase()
        bookInfoDatabase.deleteDatabase()
        return Result.success(Unit)
    }

    /////////////////////////////
    // Private Helper Methods  //
    /////////////////////////////

    private enum class UpdateKind {
        ADD,
        UPDATE,
        UPSERT,
        DELETE
    }

    private suspend fun saveBookInfoToApiAndDB(
        bookInfo: BookInfo,
        updateKind: UpdateKind
    ): Result<BookInfo> {
        log.d(this, "updateKind: " + updateKind + ", id: " + bookInfo.id())

        // Make the API request
        val apiChangeResult: Result<BookInfoDTO> = when (updateKind) {
            UpdateKind.UPDATE -> {
                val bookExistsResult: Result<BookInfoDTO> = bookInfoApi.fetchBookInfo(bookInfo.id())
                if (bookExistsResult.isFailure)
                    return Result.failure((bookExistsResult.exceptionOrNull() ?: Exception("fetchBookInfo Error")))

                bookInfoApi.updateBookInfo(bookInfo.toInfoDTO())
            }
            UpdateKind.UPSERT -> bookInfoApi.upsertBookInfo(bookInfo.toInfoDTO())
            UpdateKind.ADD -> bookInfoApi.addBookInfo(bookInfo.toInfoDTO())
            else -> return Result.failure(Exception("UpdateType not supported: $updateKind"))
        }
        if (apiChangeResult.isFailure) {
            return Result.failure(apiChangeResult.exceptionOrNull() ?: Exception("fetchBookInfo Error"))
        }

        // Save to Local DB
        val dbChangeResult: Result<BookInfoEntity> = when (updateKind) {
            UpdateKind.UPDATE -> {
                val bookExistsResult: Result<BookInfoEntity> = bookInfoDatabase.fetchBookInfo(bookInfo.id())
                if (bookExistsResult.isFailure)
                    return Result.failure((bookExistsResult.exceptionOrNull() ?: Exception("getBookInfo Error")))

                bookInfoDatabase.updateBookInfo(bookInfo.toInfoEntity())
            }
            UpdateKind.UPSERT -> bookInfoDatabase.upsertBookInfo(bookInfo.toInfoEntity())
            UpdateKind.ADD -> bookInfoDatabase.addBookInfo(bookInfo.toInfoEntity())
            else -> return Result.failure(Exception("UpdateType not supported: $updateKind"))
        }
        if (dbChangeResult.isFailure) {
            return Result.failure(dbChangeResult.exceptionOrNull() ?: Exception("updateBookInfo DB Error"))
        }

        return Result.success(bookInfo)
    }
}
