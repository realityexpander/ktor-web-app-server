package domain.library.data

import com.realityexpander.common.data.local.JsonFileDatabase
import com.realityexpander.domain.account.data.AccountInfoRepo.Companion.DEFAULT_LIBRARYINFO_REPO_DATABASE_FILENAME
import common.data.local.IJsonDatabase
import common.log.ILog
import domain.library.Library
import kotlinx.coroutines.runBlocking

/**
 * LibraryInfoFileRepo
 *
 * Simulates a persistent database using a local json file for the LibraryInfo.
 *
 * Note: Use Domain-specific language to define the API methods.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12
 */

class LibraryInfoFileRepo(
    log: ILog,
    libraryInfoRepoDatabaseFilename: String = DEFAULT_LIBRARYINFO_REPO_DATABASE_FILENAME,

    // Use a file database to persist the library info
    database: IJsonDatabase<Library, LibraryInfo> = object :
        JsonFileDatabase<Library, LibraryInfo>(
            libraryInfoRepoDatabaseFilename,
            LibraryInfo.serializer()
        ) {
        init {
            runBlocking {
                super.loadFileDatabase()
            }
        }

        override suspend fun updateLookupTables() { /* no-op */ }
    }

) : LibraryInfoRepo(log, database)