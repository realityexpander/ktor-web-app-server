package domain.library.data

import com.realityexpander.common.data.local.JsonFileDatabase
import common.log.ILog
import common.uuid2.UUID2
import domain.common.data.repo.Repo
import domain.library.Library
import kotlinx.coroutines.runBlocking

/**
 * LibraryInfoRepo
 *
 * Simulates a persistent database using a local json file for the LibraryInfo.
 *
 * Note: Use Domain-specific language to define the API methods.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12
 */

class LibraryInfoRepo(
    log: ILog,
    libraryRepoDatabaseFilename: String = DEFAULT_LIBRARYINFO_REPO_DATABASE_FILENAME,

    // Use a file database to persist the library info
    private val database: JsonFileDatabase<Library, LibraryInfo> = object :
        JsonFileDatabase<Library, LibraryInfo>(
            libraryRepoDatabaseFilename,
            LibraryInfo.serializer()
        ) {
        init {
            runBlocking {
                super.loadFileDatabase()
            }
        }

        override suspend fun updateLookupTables() { /* no-op */ }
    }

) : Repo(log), ILibraryInfoRepo {

    override suspend fun fetchLibraryInfo(id: UUID2<Library>): Result<LibraryInfo> {
        return try {
            Result.success(database.findEntityById(id)
                ?: throw Exception("Library not found, id: $id")
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun fetchAllLibraryInfo(): Result<List<LibraryInfo>> {
        return try {
            Result.success(database.findAllEntities())
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun updateLibraryInfo(libraryInfo: LibraryInfo): Result<LibraryInfo> {
        return try {
            Result.success(database.updateEntity(libraryInfo)
                ?: throw Exception("Library not found, id: ${libraryInfo.id()}"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun upsertLibraryInfo(libraryInfo: LibraryInfo): Result<LibraryInfo> {
        return try {
            Result.success(database.upsertEntity(libraryInfo)
                ?: throw Exception("Library upsert error, id: ${libraryInfo.id()}"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun deleteLibraryInfo(libraryInfo: LibraryInfo): Result<Unit> {
        return try {
            Result.success(database.deleteEntity(libraryInfo))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun deleteDatabase(): Result<Unit> {
        return try {
            Result.success(database.deleteDatabaseFile())
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    companion object {
        const val DEFAULT_LIBRARYINFO_REPO_DATABASE_FILENAME = "libraryInfoRepoDB.json"
    }
}