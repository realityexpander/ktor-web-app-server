package com.realityexpander.domain.book.data.local

import common.data.local.IDatabase
import common.uuid2.UUID2
import domain.book.Book
import domain.book.data.local.BookInfoEntity

interface IBookInfoDatabase {
    suspend fun fetchBookInfo(id: UUID2<Book>): Result<BookInfoEntity>
    suspend fun allBookInfos(): Result<Map<UUID2<Book>, BookInfoEntity>>
    suspend fun addBookInfo(bookInfo: BookInfoEntity): Result<BookInfoEntity>
    suspend fun updateBookInfo(bookInfo: BookInfoEntity): Result<BookInfoEntity>
    suspend fun upsertBookInfo(bookInfo: BookInfoEntity): Result<BookInfoEntity>
    suspend fun deleteBookInfo(bookInfo: BookInfoEntity): Result<Unit>
    suspend fun deleteDatabase(): Result<Unit>

    suspend fun findBookInfosByField(field: String, searchTerm: String): Result<List<BookInfoEntity>>
    suspend fun findBookInfosByTitle(searchTerm: String): Result<List<BookInfoEntity>>

    companion object {
        const val DEFAULT_BOOKINFO_FILE_DATABASE_FILENAME: String = "bookInfoFileDatabaseDB.json"
    }
}

abstract class BookInfoDatabase(
    val database: IDatabase<Book, BookInfoEntity>
) : IBookInfoDatabase {
    override suspend fun fetchBookInfo(id: UUID2<Book>): Result<BookInfoEntity> {
        return database.fetchEntityInfo(id)
    }

    override suspend fun allBookInfos(): Result<Map<UUID2<Book>, BookInfoEntity>> {
        return database.findAllUUID2ToEntityInfoMap()
    }

    override suspend fun updateBookInfo(bookInfo: BookInfoEntity): Result<BookInfoEntity> {
        return database.updateEntityInfo(bookInfo)
    }

    override suspend fun addBookInfo(bookInfo: BookInfoEntity): Result<BookInfoEntity> {
        return database.addEntityInfo(bookInfo)
    }

    override suspend fun upsertBookInfo(bookInfo: BookInfoEntity): Result<BookInfoEntity> {
        return database.upsertEntityInfo(bookInfo)
    }

    override suspend fun deleteBookInfo(bookInfo: BookInfoEntity): Result<Unit> {
        return database.deleteEntityInfo(bookInfo)
    }

    override suspend fun deleteDatabase(): Result<Unit> {
        return database.deleteAllEntityInfos()
    }

    override suspend fun findBookInfosByField(field: String, searchTerm: String): Result<List<BookInfoEntity>> {
        return database.findEntityInfosByField(field, searchTerm)
    }

    override suspend fun findBookInfosByTitle(searchTerm: String): Result<List<BookInfoEntity>> {
        return database.findEntityInfosByField("title", searchTerm)
    }
}