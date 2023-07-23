package domain

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import common.log.ILog
import common.log.Log
import common.uuid2.UUID2
import domain.account.data.AccountInfoRepo
import domain.book.data.BookInfoRepo
import domain.book.data.local.BookInfoInMemoryDatabase
import domain.book.data.network.BookInfoInMemoryApi
import domain.library.data.LibraryInfoRepo
import domain.user.data.UserInfoRepo

/**
 * Context is a singleton class that holds all the repositories and utility classes.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 kotlin conversion
 */

class Context(
    val bookInfoRepo: BookInfoRepo,
    val userInfoRepo: UserInfoRepo,
    val libraryInfoRepo: LibraryInfoRepo,
    val accountInfoRepo: AccountInfoRepo,
    val gson: Gson,
    val log: ILog
) {
    //////////////////////////////
    // Static Constructors      //
    //////////////////////////////

    sealed class ContextKind {
        object PRODUCTION : ContextKind()
        object TESTING : ContextKind()
    }

    companion object {

        fun setupProductionInstance(log: ILog?): Context {
            return if (log == null)
                setupInstance(
                    ContextKind.PRODUCTION,
                    Log()
                )
            else
                setupInstance(
                    ContextKind.PRODUCTION,
                    log
                )
        }

        fun setupInstance(
            contextKind: ContextKind,
            log: ILog,
            context: Context = generateDefaultProductionContext(log)
        ): Context {
            return when (contextKind) {
                ContextKind.PRODUCTION -> generateDefaultProductionContext(log)
                ContextKind.TESTING -> {
                    println("Context.setupInstance(): contextType=TESTING, using passed in Context")
                    context
                }
            }
        }

        // Generate sensible default singletons for the PRODUCTION Application
        private fun generateDefaultProductionContext(log: ILog): Context {
            return Context(
                BookInfoRepo(
                    BookInfoInMemoryApi(),
                    BookInfoInMemoryDatabase(),
                    log
                ),
                UserInfoRepo(log),
                LibraryInfoRepo(log),
                AccountInfoRepo(log),
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