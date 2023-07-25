package com.realityexpander.domain.book.data.local

import common.uuid2.UUID2
import domain.book.Book
import domain.book.data.local.EntityBookInfo

interface IBookInfoDatabase {
    suspend fun fetchBookInfo(id: UUID2<Book>): Result<EntityBookInfo>
    suspend fun allBookInfos(): Result<Map<UUID2<Book>, EntityBookInfo>>
    suspend fun addBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo>
    suspend fun updateBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo>
    suspend fun upsertBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo>
    suspend fun deleteBookInfo(bookInfo: EntityBookInfo): Result<EntityBookInfo>
    suspend fun deleteDatabase(): Result<Unit>

    companion object {
        const val DEFAULT_BOOKINFO_FILE_DATABASE_FILENAME: String = "bookInfoFileDatabaseDB.json"
    }
}