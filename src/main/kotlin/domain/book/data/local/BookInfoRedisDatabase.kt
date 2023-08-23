package com.realityexpander.domain.book.data.local

import com.realityexpander.common.data.local.InfoEntityRedisDatabase
import com.redis.lettucemod.RedisModulesClient
import domain.book.Book
import domain.book.data.local.BookInfoEntity

/**
 * BookInfoRedisDatabase
 *
 * * Implements a persistent database using a Redis database for the BookInfoEntity.
 * * Uses Domain-specific language to define the API methods.
 * * Defines the search indexes for the database.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12
 */
class BookInfoRedisDatabase(
    bookInfoDatabaseName: String = DEFAULT_BOOKINFO_DATABASE_NAME,
    redisUrl: String = "redis://localhost:6379",
    redisClient: RedisModulesClient = RedisModulesClient.create(redisUrl),
): BookInfoDatabase(
    // Use a redis database to persist the book info
    database = InfoEntityRedisDatabase<Book, BookInfoEntity>(
        bookInfoDatabaseName,
        BookInfoEntity.serializer(),
        redisUrl,
        redisClient
    )
) {
    init {
        (database as InfoEntityRedisDatabase<Book, BookInfoEntity>).initDatabase(
            fieldsToSearchIndex = arrayOf("id", "title", "author", "description")
        )
    }

    companion object {
        const val DEFAULT_BOOKINFO_DATABASE_NAME: String = "bookInfoDatabaseDB" // json root of the database
    }
}
