package domain.book.data

import common.log.ILog
import common.log.Log
import common.uuid2.UUID2
import domain.book.Book
import domain.book.data.BookInfoRepo.UpdateKind.*
import domain.book.data.local.BookInfoDatabase
import domain.book.data.local.EntityBookInfo
import domain.book.data.network.BookInfoApi
import domain.book.data.network.DTOBookInfo
import domain.common.data.repo.Repo
import sun.jvm.hotspot.oops.CellTypeState.value

/**
 * BookInfoRepo is a repository for the BookInfo class.
 *
 * Business logic for Book Repo (simple CRUD operations; converts to/from DTOs/Entities/Domains)
 *
 * Simulates a database on a server via in-memory HashMap.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */

class BookInfoRepo @JvmOverloads constructor(
    val bookInfoApi: BookInfoApi = BookInfoApi(),
    val bookInfoDatabase: BookInfoDatabase = BookInfoDatabase(),
    val log: ILog = Log()
) : Repo(log), IBookInfoRepo {

    override fun fetchBookInfo(id: UUID2<Book>): Result<BookInfo> {
        log.d(this, "bookId $id")

        // Make the request to API
        val fetchBookApiResult: Result<DTOBookInfo> = bookInfoApi.fetchBookInfo(id)
        if (fetchBookApiResult.isFailure) {

            // API failed, now try to get from cached DB
            val fetchBookDBResult: Result<EntityBookInfo> = bookInfoDatabase.fetchBookInfo(id)
            if (fetchBookDBResult.isFailure) {
                return Result.failure(fetchBookDBResult.exceptionOrNull() ?: Exception("getBookInfo Error"))
            }

            val bookInfo: EntityBookInfo = fetchBookDBResult.getOrNull()
                ?: return Result.failure(Exception("getBookInfo Error"))
            return Result.success(bookInfo.toDeepCopyDomainInfo())
        }

        // Convert to Domain Model
        val bookInfo: BookInfo =
            if (fetchBookApiResult.isSuccess)
                fetchBookApiResult.getOrNull()?.toDeepCopyDomainInfo()
                    ?: return Result.failure(Exception("Model conversion error"))
            else
                return Result.failure(fetchBookApiResult.exceptionOrNull() ?: Exception("Model conversion error"))


        // Cache to Local DB
        val updateDBResult: Result<EntityBookInfo> = bookInfoDatabase.updateBookInfo(bookInfo.toInfoEntity())
        if (updateDBResult.isFailure) {
            return Result.failure(updateDBResult.exceptionOrNull() ?: Exception("updateBookInfo Error"))
        }

        return Result.success(bookInfo)
    }

    override fun updateBookInfo(bookInfo: BookInfo): Result<BookInfo> {
        log.d(this, "bookInfo: $bookInfo")
        val saveResult = saveBookInfoToApiAndDB(bookInfo, UPDATE)

        if (saveResult.isFailure) {
            return Result.failure(saveResult.exceptionOrNull() ?: Exception("updateBookInfo Error"))
        }

        return saveResult
    }

    override fun addBookInfo(bookInfo: BookInfo): Result<BookInfo> {
        log.d(this, "bookInfo: $bookInfo")
        val saveResult = saveBookInfoToApiAndDB(bookInfo, ADD)

        if (saveResult.isFailure) {
            return Result.failure(saveResult.exceptionOrNull() ?: Exception("addBookInfo Error"))
        }

        return saveResult
    }

    override fun upsertBookInfo(bookInfo: BookInfo): Result<BookInfo> {
        log.d(this, "bookId: " + bookInfo.id())
        val saveResult = saveBookInfoToApiAndDB(bookInfo, UPSERT)

        if (saveResult.isFailure) {
            return Result.failure(saveResult.exceptionOrNull() ?: Exception("upsertBookInfo Error"))
        }

        return saveResult
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

    private fun saveBookInfoToApiAndDB(
        bookInfo: BookInfo,
        updateKind: UpdateKind
    ): Result<BookInfo> {
        log.d(this, "updateType: " + updateKind + ", id: " + bookInfo.id())

        // Make the API request
        val apiChangeResult: Result<DTOBookInfo> = when (updateKind) {
            UPDATE -> {
                val bookExistsResult: Result<DTOBookInfo> = bookInfoApi.fetchBookInfo(bookInfo.id())
                if (bookExistsResult.isFailure)
                    return Result.failure((bookExistsResult.exceptionOrNull() ?: Exception("fetchBookInfo Error")))

                bookInfoApi.updateBookInfo(bookInfo.toInfoDTO())
            }
            UPSERT -> bookInfoApi.upsertBookInfo(bookInfo.toInfoDTO())
            ADD -> bookInfoApi.addBookInfo(bookInfo.toInfoDTO())
            else -> return Result.failure(Exception("UpdateType not supported: $updateKind"))
        }
        if (apiChangeResult.isFailure) {
            return Result.failure(apiChangeResult.exceptionOrNull() ?: Exception("fetchBookInfo Error"))
        }

        // Save to Local DB
        val dbChangeResult: Result<EntityBookInfo> = when (updateKind) {
            UPDATE -> {
                val bookExistsResult: Result<EntityBookInfo> = bookInfoDatabase.fetchBookInfo(bookInfo.id())
                if (bookExistsResult.isFailure)
                    return Result.failure((bookExistsResult.exceptionOrNull() ?: Exception("getBookInfo Error")))

                bookInfoDatabase.updateBookInfo(bookInfo.toInfoEntity())
            }
            UPSERT -> bookInfoDatabase.upsertBookInfo(bookInfo.toInfoEntity())
            ADD -> bookInfoDatabase.addBookInfo(bookInfo.toInfoEntity())
            else -> return Result.failure(Exception("UpdateType not supported: $updateKind"))
        }
        if (dbChangeResult.isFailure) {
            return Result.failure(dbChangeResult.exceptionOrNull() ?: Exception("updateBookInfo DB Error"))
        }

        return Result.success(bookInfo)
    }

    fun upsertTestEntityBookInfoToDB(entityBookInfo: EntityBookInfo): Result<BookInfo> {
        val upsertResult: Result<EntityBookInfo> = bookInfoDatabase.upsertBookInfo(entityBookInfo)
        if (upsertResult.isFailure) {
            return Result.failure(upsertResult.exceptionOrNull() ?: Exception("upsertBookInfo DB Error"))
        }
        return Result.success(entityBookInfo.toDeepCopyDomainInfo())
    }

    fun upsertTestDTOBookInfoToApi(dtoBookInfo: DTOBookInfo): Result<BookInfo> {
        val upsertResult: Result<DTOBookInfo> = bookInfoApi.upsertBookInfo(dtoBookInfo)
        if (upsertResult.isFailure) {
            return Result.failure(upsertResult.exceptionOrNull() ?: Exception("upsertBookInfo API Error"))
        }
        return Result.success(dtoBookInfo.toDeepCopyDomainInfo())
    }

    /////////////////////////////////////////////////////
    // Debugging Methods                               //
    //  - not part of interface or used in production) //
    /////////////////////////////////////////////////////

    fun printDB() {
        for ((key, value) in bookInfoDatabase.allBookInfos.entries) {
            log.d(this, "$key = $value")
        }
    }

    fun printAPI() {
        for ((key, value) in bookInfoApi.allBookInfos.entries) {
            log.d(this, "$key = $value")
        }
    }
}
