package domain.library.data

import com.realityexpander.common.data.local.FileDatabase
import common.log.ILog
import common.uuid2.UUID2
import domain.common.data.repo.Repo
import domain.library.Library
import kotlinx.coroutines.runBlocking

class LibraryInfoPersistentRepo(
    log: ILog,
    libraryRepoDatabaseFilename: String = DEFAULT_LIBRARY_REPO_DATABASE_FILENAME,
) : Repo(log), ILibraryInfoRepo {

    // Use a file database to persist the library info
    private val libraryInfoFileDatabase = object :
        FileDatabase<UUID2<Library>, LibraryInfo>(
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

    override suspend fun fetchLibraryInfo(id: UUID2<Library>): Result<LibraryInfo> {
        return try {
            Result.success(libraryInfoFileDatabase.findEntityById(id)
                ?: throw Exception("Library not found, id: $id")
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun updateLibraryInfo(libraryInfo: LibraryInfo): Result<LibraryInfo> {
        return try {
            Result.success(libraryInfoFileDatabase.updateEntity(libraryInfo) ?: throw Exception("Library not found, id: ${libraryInfo.id()}"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun upsertLibraryInfo(libraryInfo: LibraryInfo): Result<LibraryInfo> {
        return try {
            Result.success(libraryInfoFileDatabase.upsertEntity(libraryInfo) ?: throw Exception("Library upsert error, id: ${libraryInfo.id()}"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun deleteDatabase(): Result<Unit> {
        return try {
            Result.success(libraryInfoFileDatabase.deleteDatabaseFile())
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    companion object {
        const val DEFAULT_LIBRARY_REPO_DATABASE_FILENAME = "libraryInfoRepoDB.json"
    }
}