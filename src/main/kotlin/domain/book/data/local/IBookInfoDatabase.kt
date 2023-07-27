package com.realityexpander.domain.book.data.local

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

    companion object {
        const val DEFAULT_BOOKINFO_FILE_DATABASE_FILENAME: String = "bookInfoFileDatabaseDB.json"
    }
}