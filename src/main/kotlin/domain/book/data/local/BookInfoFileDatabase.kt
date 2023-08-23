package com.realityexpander.domain.book.data.local

import com.realityexpander.common.data.local.InfoEntityFileDatabase
import domain.book.data.local.BookInfoEntity

/**
 * BookInfoFileDatabase
 *
 * Simulates a persistent database using a local file database for the BookInfoEntity.
 *
 * Note: Use Domain-specific language to define the API methods.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12
 */

class BookInfoFileDatabase(
    bookInfoFileDatabaseFilename: String = DEFAULT_BOOKINFO_FILE_DATABASE_FILENAME,
): BookInfoDatabase(
    // Use a file database to persist the book info
    database = InfoEntityFileDatabase(
        bookInfoFileDatabaseFilename,
        BookInfoEntity.serializer()
    )
) {
    companion object {
        const val DEFAULT_BOOKINFO_FILE_DATABASE_FILENAME: String = "bookInfoFileDatabaseDB.json"
    }
}