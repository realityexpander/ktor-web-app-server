package domain

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.realityexpander.domain.account.data.AccountInfoRepo
import com.realityexpander.domain.book.data.local.BookInfoFileDatabase
import common.log.ILog
import common.log.Log
import common.uuid2.UUID2
import domain.account.data.AccountInfoInMemoryRepo
import domain.account.data.IAccountInfoRepo
import domain.book.data.BookInfoInMemoryRepo
import domain.book.data.BookInfoRepo
import domain.book.data.IBookInfoRepo
import domain.book.data.network.BookInfoFileApi
import domain.library.data.ILibraryInfoRepo
import domain.library.data.LibraryInfoInMemoryRepo
import domain.library.data.LibraryInfoRepo
import domain.user.data.IUserInfoRepo
import domain.user.data.UserInfoInMemoryRepo
import domain.user.data.UserInfoRepo

/**
 * Context is a singleton class that holds all the repositories and utility classes.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 kotlin conversion
 */

class Context(
    val bookInfoRepo: IBookInfoRepo,
    val userInfoRepo: IUserInfoRepo,
    val libraryInfoRepo: ILibraryInfoRepo,
    val accountInfoRepo: IAccountInfoRepo,
    val gson: Gson,
    val log: ILog
) {

    companion object {

        //////////////////////////////
        // Static Constructors      //
        //////////////////////////////

        fun setupContextInstance(
            log: ILog = Log(),
            context: Context = createDefaultProductionContext(log)  // pass in a custom context for testing
        ): Context {
            return context
        }

        // Generate singletons for the PRODUCTION Application
        private fun createDefaultProductionContext(log: ILog): Context {
            return Context(
                BookInfoRepo(log),      // BookInfoInMemoryRepo(log)
                UserInfoRepo(log),      // UserInfoInMemoryRepo(log)
                LibraryInfoRepo(log),   // LibraryInfoInMemoryRepo(log)
                AccountInfoRepo(log),   // AccountInfoInMemoryRepo(log),
                gsonConfig,
                log
            )
        }

        // Generate singletons for the TESTING Application
        // - uses in-memory repositories
        fun createDefaultTestInMemoryContext(log: ILog): Context {
            return Context(
                BookInfoInMemoryRepo(log),
                UserInfoInMemoryRepo(log),
                LibraryInfoInMemoryRepo(log),
                AccountInfoInMemoryRepo(log),
                gsonConfig,
                log
            )
        }

        // Generate singletons for the TESTING Application
        // - uses file-based repositories
        fun createDefaultTestFileContext(log: ILog): Context {
            val bookInfoApiFileName = "test-bookInfoApi.json"
            val bookInfoDBFileName = "test-bookInfoDB.json"

            return Context(
                BookInfoRepo(log,
                    bookInfoApiName = bookInfoApiFileName,
                    bookInfoApi = BookInfoFileApi(bookInfoApiFileName),

                    // bookInfoDatabase = BookInfoInMemoryDatabase(),
                    bookInfoDatabaseName = bookInfoDBFileName,
                    bookInfoDatabase = BookInfoFileDatabase(bookInfoDBFileName),
                    // bookInfoDatabase = BookInfoRedisDatabase("test-bookInfoDB"),
                ),
                UserInfoRepo(log,
                    userRepoDatabaseFilename ="test-userInfoRepoDatabase.json"),
                LibraryInfoRepo(log,
                    libraryInfoRepoDatabaseFilename ="test-libraryInfoRepoDatabase.json"),
                AccountInfoRepo(log,
                    accountInfoRepoDatabaseFilename ="test-accountInfoRepoDatabase.json"),
                gsonConfig,
                log
            )
        }

        val gsonConfig: Gson = GsonBuilder()
            .registerTypeAdapter(MutableMap::class.java, UUID2.Uuid2MapJsonDeserializer())
            .registerTypeAdapter(ArrayList::class.java, UUID2.Uuid2ArrayListJsonDeserializer())
            .registerTypeAdapter(ArrayList::class.java, UUID2.Uuid2ArrayListJsonSerializer())
            .registerTypeAdapter(UUID2::class.java, UUID2.Uuid2JsonSerializer())
            .registerTypeAdapter(UUID2::class.java, UUID2.Uuid2JsonDeserializer())
            .setPrettyPrinting()
            .create()
    }
}