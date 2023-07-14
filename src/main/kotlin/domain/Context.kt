package domain

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import common.log.ILog
import common.log.Log
import common.uuid2.UUID2

/**
 * Context is a singleton class that holds all the repositories and utility classes.<br></br>
 * <br></br>
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */
class Context(
    bookInfoRepo: BookInfoRepo,
    userInfoRepo: UserInfoRepo,
    libraryInfoRepo: LibraryInfoRepo,
    accountInfoRepo: AccountInfoRepo,
    gson: Gson,
    log: ILog
) {
    // static public Context INSTANCE = null;  // LEAVE for reference - Enforces singleton instance & allows global access
    // Repository Singletons
    private val bookInfoRepo: BookInfoRepo
    private val userInfoRepo: UserInfoRepo
    private val libraryInfoRepo: LibraryInfoRepo
    private val accountInfoRepo: AccountInfoRepo

    // Utility Singletons
    val gson: Gson
    val log: ILog

    init {
        this.bookInfoRepo = bookInfoRepo
        this.userInfoRepo = userInfoRepo
        this.libraryInfoRepo = libraryInfoRepo
        this.accountInfoRepo = accountInfoRepo
        this.log = log
        this.gson = gson
    }

    //////////////////////////////
    // Static Constructors      //
    //////////////////////////////

    //    LEAVE FOR REFERENCE - This is how you would enforce a singleton instance using a static method & variable
    //    // If `context` is `null` OR `StaticContext` this returns the default static Context,
    //    // otherwise returns the `context` passed in.
    //    public static Context setupINSTANCE(Context context) {
    //        if (context == null) {
    //            if(INSTANCE != null) return INSTANCE;
    //
    //            System.out.println("Context.getINSTANCE(): passed in Context is null, creating default Context");
    //            INSTANCE = new Context();
    //            return INSTANCE;  // return default Context (singleton)
    //        } else {
    //            System.out.println("Context.getINSTANCE(): using passed in Context");
    //            INSTANCE = context;  // set the default Context to the one passed in
    //            return context;
    //        }
    //    }
    //    public static Context getINSTANCE() {
    //        return setupINSTANCE(null);
    //    }
    fun bookInfoRepo(): BookInfoRepo {
        return bookInfoRepo
    }

    fun userInfoRepo(): UserInfoRepo {
        return userInfoRepo
    }

    fun libraryInfoRepo(): LibraryInfoRepo {
        return libraryInfoRepo
    }

    fun accountInfoRepo(): AccountInfoRepo {
        return accountInfoRepo
    }

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