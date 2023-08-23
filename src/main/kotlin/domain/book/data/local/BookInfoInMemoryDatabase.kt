package domain.book.data.local

import com.realityexpander.domain.book.data.local.BookInfoDatabase
import common.data.local.InfoEntityInMemoryDatabase
import common.data.network.FakeURL
import domain.book.Book

/**
 * BookInfoInMemoryDatabase
 *
 * Simulates an In-Memory local non-persisted database for BookInfoEntity.
 *
 * This class uses domain-specific language and wraps an implementation of the IDatabase interface
 * for BookInfoEntity.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

class BookInfoInMemoryDatabase : BookInfoDatabase(
    database = InfoEntityInMemoryDatabase<Book, BookInfoEntity>(
        FakeURL("memory://db.bookInfo.ram"),
        "user",
        "password"
    )
)
