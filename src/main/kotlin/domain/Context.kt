package domain

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import common.log.ILog
import common.log.Log
import common.uuid2.UUID2
import domain.book.data.BookInfoRepo
import domain.book.data.local.BookInfoDatabase
import domain.book.data.network.BookInfoApi
import domain.library.data.LibraryInfoRepo

/**
 * Context is a singleton class that holds all the repositories and utility classes.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12
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
        object TEST : ContextKind()
    }

    companion object {
        fun setupProductionInstance(log: ILog?): Context? {
            return if (log == null) setupInstance(
                ContextKind.PRODUCTION,
                Log(),
                null
            ) else
                setupInstance(ContextKind.PRODUCTION, log, null)
        }

        fun setupInstance(
            contextKind: ContextKind,
            log: ILog,
            context: Context?
        ): Context? {
            return when (contextKind) {
                ContextKind.PRODUCTION -> generateDefaultProductionContext(log)
                ContextKind.TEST -> {
                    println("Context.setupInstance(): contextType=TEST, using passed in Context")
                    context
                }
            }
        }

        // Generate sensible default singletons for the PRODUCTION application
        private fun generateDefaultProductionContext(log: ILog): Context {
            return Context(
                BookInfoRepo(
                    BookInfoApi(),
                    BookInfoDatabase(),
                    log
                ),
                UserInfoRepo(log),
                LibraryInfoRepo(log),
                AccountInfoRepo(log),
                GsonBuilder()
                    .registerTypeAdapter(MutableMap::class.java, UUID2.Uuid2MutableMapJsonDeserializer())
                    .setPrettyPrinting()
                    .create(),
                log
            )
        }
    }
}