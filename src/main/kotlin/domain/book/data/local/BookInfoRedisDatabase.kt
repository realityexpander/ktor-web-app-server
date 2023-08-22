package com.realityexpander.domain.book.data.local

import com.realityexpander.common.data.network.RedisDatabase
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
    database = RedisDatabase<Book, BookInfoEntity>(
        bookInfoDatabaseName,
        BookInfoEntity.serializer(),
        redisUrl,
        redisClient
    )
) {
    init {
        (database as RedisDatabase<Book, BookInfoEntity>).initDatabase(
            fieldsToSearchIndex = arrayOf("title", "author", "description")
        )
    }

    companion object {
        const val DEFAULT_BOOKINFO_DATABASE_NAME: String = "bookInfoDB" // json root of the database
    }
}

//
//class BookInfoRedisDatabase1(
//    bookInfoDatabaseName: String = DEFAULT_BOOKINFO_DATABASE_NAME,
//    private val redisUrl: String = "redis://localhost:6379",
//    private val redisClient: RedisModulesClient = RedisModulesClient.create(redisUrl),
//
//    // Use a redis database to persist the book info
//    private val database: IDatabase<Book, BookInfoEntity> =
//        RedisDatabase(
//            bookInfoDatabaseName,
//            BookInfoEntity.serializer(),
//            redisUrl,
//            redisClient
//        )
//) : IBookInfoDatabase {
//
//    init {
//        (database as RedisDatabase<Book, BookInfoEntity>).initDatabase(
//            fieldsToSearchIndex = arrayOf("title", "author", "description")
//        )
//    }
//
//    override suspend fun fetchBookInfo(id: UUID2<Book>): Result<BookInfoEntity> {
//        return database.fetchEntityInfo(id)
//    }
//
//    override suspend fun allBookInfos(): Result<Map<UUID2<Book>, BookInfoEntity>> {
//        return database.findAllUUID2ToEntityInfoMap()
//    }
//
//    override suspend fun addBookInfo(bookInfo: BookInfoEntity): Result<BookInfoEntity> {
//        return database.addEntityInfo(bookInfo)
//    }
//
//    override suspend fun updateBookInfo(bookInfo: BookInfoEntity): Result<BookInfoEntity> {
//        return database.updateEntityInfo(bookInfo)
//    }
//
//    override suspend fun upsertBookInfo(bookInfo: BookInfoEntity): Result<BookInfoEntity> {
//        return database.upsertEntityInfo(bookInfo)
//    }
//
//    override suspend fun deleteBookInfo(bookInfo: BookInfoEntity): Result<Unit> {
//        return database.deleteEntityInfo(bookInfo)
//    }
//
//    override suspend fun deleteDatabase(): Result<Unit> {
//        return database.deleteAllEntityInfos()
//    }
//
//    override suspend fun findBookInfosByTitle(searchTerm: String): Result<List<BookInfoEntity>> {
//        return database.findEntityInfosByField("title", searchTerm)
//    }
//
//    override suspend fun findBookInfosByField(field: String, searchTerm: String): Result<List<BookInfoEntity>> {
//        return database.findEntityInfosByField(field, searchTerm)
//    }
//
//    companion object {
//        const val DEFAULT_BOOKINFO_DATABASE_NAME: String = "bookInfoDB" // json root of the database
//    }
//}
