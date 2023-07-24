package domain.book.data

import common.log.ILog
import common.log.Log
import common.uuid2.UUID2
import domain.book.Book
import domain.book.data.BookInfoRepo.UpdateKind.*
import domain.book.data.local.BookInfoInMemoryDatabase
import domain.book.data.local.EntityBookInfo
import domain.book.data.network.BookInfoInMemoryApi
import domain.book.data.network.DTOBookInfo
import domain.common.data.repo.Repo

/**
 * BookInfoRepo is a repository for the BookInfo class.
 *
 * Business logic for Book Repo (simple CRUD operations; converts to/from DTOs/Entities/Domains)
 *
 * Simulates a database on a server via in-memory HashMap.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

class BookInfoRepo(
    private val bookInfoInMemoryApi: BookInfoInMemoryApi = BookInfoInMemoryApi(),
    private val bookInfoInMemoryDatabase: BookInfoInMemoryDatabase = BookInfoInMemoryDatabase(),
    override val log: ILog = Log()
) : Repo(log), IBookInfoRepo {

    override suspend fun fetchBookInfo(id: UUID2<Book>): Result<BookInfo> {
        log.d(this, "bookId $id")

        // Make the request to API
        val fetchBookApiResult: Result<DTOBookInfo> = bookInfoInMemoryApi.fetchBookInfo(id)
        if (fetchBookApiResult.isFailure) {

            // API failed, now try to get from cached DB
            val fetchBookDBResult: Result<EntityBookInfo> = bookInfoInMemoryDatabase.fetchBookInfo(id)
            if (fetchBookDBResult.isFailure) {
                return Result.failure(fetchBookDBResult.exceptionOrNull() ?: Exception("getBookInfo Error"))
            }

            val bookInfo: EntityBookInfo = fetchBookDBResult.getOrNull()
                ?: return Result.failure(Exception("getBookInfo Error"))
            return Result.success(bookInfo.toDomainInfoDeepCopy())
        }

        // Convert to Domain Model
        val bookInfo: BookInfo =
            if (fetchBookApiResult.isSuccess)
                fetchBookApiResult.getOrNull()?.toDomainInfoDeepCopy()
                    ?: return Result.failure(Exception("Model conversion error"))
            else
                return Result.failure(fetchBookApiResult.exceptionOrNull() ?: Exception("Model conversion error"))


        // Cache to Local DB
        val updateDBResult: Result<EntityBookInfo> = bookInfoInMemoryDatabase.updateBookInfo(bookInfo.toInfoEntity())
        if (updateDBResult.isFailure) {
            return Result.failure(updateDBResult.exceptionOrNull() ?: Exception("updateBookInfo Error"))
        }

        return Result.success(bookInfo)
    }

    override suspend fun updateBookInfo(bookInfo: BookInfo): Result<BookInfo> {
        log.d(this, "bookInfo: $bookInfo")
        val saveResult = saveBookInfoToApiAndDB(bookInfo, UPDATE)

        if (saveResult.isFailure) {
            return Result.failure(saveResult.exceptionOrNull() ?: Exception("updateBookInfo Error"))
        }

        return saveResult
    }

    override suspend fun addBookInfo(bookInfo: BookInfo): Result<BookInfo> {
        log.d(this, "bookInfo: $bookInfo")
        val saveResult = saveBookInfoToApiAndDB(bookInfo, ADD)

        if (saveResult.isFailure) {
            return Result.failure(saveResult.exceptionOrNull() ?: Exception("addBookInfo Error"))
        }

        return saveResult
    }

    override suspend fun upsertBookInfo(bookInfo: BookInfo): Result<BookInfo> {
        log.d(this, "bookId: " + bookInfo.id())
        val saveResult = saveBookInfoToApiAndDB(bookInfo, UPSERT)

        if (saveResult.isFailure) {
            return Result.failure(saveResult.exceptionOrNull() ?: Exception("upsertBookInfo Error"))
        }

        return saveResult
    }

    override suspend fun deleteDatabase(): Result<Unit> {
        log.d(this, "deleteDatabase")

        // Simulate network/database
        bookInfoInMemoryApi.deleteDatabase()
        bookInfoInMemoryDatabase.deleteDatabase()
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
        log.d(this, "updateType: " + updateKind + ", id: " + bookInfo.id())

        // Make the API request
        val apiChangeResult: Result<DTOBookInfo> = when (updateKind) {
            UPDATE -> {
                val bookExistsResult: Result<DTOBookInfo> = bookInfoInMemoryApi.fetchBookInfo(bookInfo.id())
                if (bookExistsResult.isFailure)
                    return Result.failure((bookExistsResult.exceptionOrNull() ?: Exception("fetchBookInfo Error")))

                bookInfoInMemoryApi.updateBookInfo(bookInfo.toInfoDTO())
            }
            UPSERT -> bookInfoInMemoryApi.upsertBookInfo(bookInfo.toInfoDTO())
            ADD -> bookInfoInMemoryApi.addBookInfo(bookInfo.toInfoDTO())
            else -> return Result.failure(Exception("UpdateType not supported: $updateKind"))
        }
        if (apiChangeResult.isFailure) {
            return Result.failure(apiChangeResult.exceptionOrNull() ?: Exception("fetchBookInfo Error"))
        }

        // Save to Local DB
        val dbChangeResult: Result<EntityBookInfo> = when (updateKind) {
            UPDATE -> {
                val bookExistsResult: Result<EntityBookInfo> = bookInfoInMemoryDatabase.fetchBookInfo(bookInfo.id())
                if (bookExistsResult.isFailure)
                    return Result.failure((bookExistsResult.exceptionOrNull() ?: Exception("getBookInfo Error")))

                bookInfoInMemoryDatabase.updateBookInfo(bookInfo.toInfoEntity())
            }
            UPSERT -> bookInfoInMemoryDatabase.upsertBookInfo(bookInfo.toInfoEntity())
            ADD -> bookInfoInMemoryDatabase.addBookInfo(bookInfo.toInfoEntity())
            else -> return Result.failure(Exception("UpdateType not supported: $updateKind"))
        }
        if (dbChangeResult.isFailure) {
            return Result.failure(dbChangeResult.exceptionOrNull() ?: Exception("updateBookInfo DB Error"))
        }

        return Result.success(bookInfo)
    }

    suspend fun upsertTestEntityBookInfoToDB(entityBookInfo: EntityBookInfo): Result<BookInfo> {
        val upsertResult: Result<EntityBookInfo> = bookInfoInMemoryDatabase.upsertBookInfo(entityBookInfo)
        if (upsertResult.isFailure) {
            return Result.failure(upsertResult.exceptionOrNull() ?: Exception("upsertBookInfo DB Error"))
        }
        return Result.success(entityBookInfo.toDomainInfoDeepCopy())
    }

    suspend fun upsertTestDTOBookInfoToApi(dtoBookInfo: DTOBookInfo): Result<BookInfo> {
        val upsertResult: Result<DTOBookInfo> = bookInfoInMemoryApi.upsertBookInfo(dtoBookInfo)
        if (upsertResult.isFailure) {
            return Result.failure(upsertResult.exceptionOrNull() ?: Exception("upsertBookInfo API Error"))
        }
        return Result.success(dtoBookInfo.toDomainInfoDeepCopy())
    }

    /////////////////////////////////////////////////////
    // Debugging Methods                               //
    //  - not part of interface or used in production) //
    /////////////////////////////////////////////////////

    suspend fun printDB() {
        for ((key, value) in bookInfoInMemoryDatabase.allBookInfos().entries) {
            log.d(this, "$key = $value")
        }
    }

    suspend fun printAPI() {
        val entries = bookInfoInMemoryApi.allBookInfos().getOrNull()?.entries
        if (entries == null) {
            log.d(this, "API is empty")
            return
        }

        for ((key, value) in entries) {
            log.d(this, "$key = $value")
        }
    }
}
