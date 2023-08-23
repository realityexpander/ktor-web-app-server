package com.realityexpander.domain.book.data.network

import com.realityexpander.common.data.local.InfoEntityRedisDatabase
import com.realityexpander.common.data.local.JsonRedisDatabase
import com.realityexpander.common.data.network.InfoDTORedisApi
import com.redis.lettucemod.RedisModulesClient
import domain.book.Book
import domain.book.data.local.BookInfoEntity
import domain.book.data.network.BookInfoApi
import domain.book.data.network.BookInfoDTO

/**
 * BookInfoRedisApi
 *
 * Simulates a persistent network API using a Redis client to the database for the BookInfoDTO.
 *
 * Note: Use Domain-specific language to define the API methods.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12
 */

class BookInfoRedisApi(
    bookInfoApiDatabaseName: String = DEFAULT_BOOKINFO_API_DATABASE_NAME,
    redisUrl: String = "redis://localhost:6379",
    redisClient: RedisModulesClient = RedisModulesClient.create(redisUrl),
) : BookInfoApi(
    // Use a file Api to persist the book info
    api = InfoDTORedisApi<Book, BookInfoDTO>(
        bookInfoApiDatabaseName,
        BookInfoDTO.serializer(),
        redisUrl,
        redisClient
    )
) {
    init {
        (api as InfoDTORedisApi<Book, BookInfoDTO>).initDatabase(
            fieldsToSearchIndex = arrayOf("id", "title", "author", "description")
        )
    }

    companion object {
        const val DEFAULT_BOOKINFO_API_DATABASE_NAME = "bookInfoApiDB"
    }
}