package domain.library.data

import common.data.local.IJsonDatabase
import common.log.ILog
import common.uuid2.UUID2
import domain.common.data.repo.IRepo
import domain.common.data.repo.Repo
import domain.library.Library

/**
 * ILibraryInfoRepo is an interface for the LibraryInfoFileRepo class.
 *
 * Meant to be used by the domain layer, not the data layer.
 *
 * Useful for testing.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

interface ILibraryInfoRepo : IRepo {
    suspend fun fetchLibraryInfo(id: UUID2<Library>): Result<LibraryInfo>
    suspend fun fetchAllLibraryInfo(): Result<List<LibraryInfo>>
    suspend fun updateLibraryInfo(libraryInfo: LibraryInfo): Result<LibraryInfo>
    suspend fun upsertLibraryInfo(libraryInfo: LibraryInfo): Result<LibraryInfo>
    suspend fun deleteLibraryInfo(libraryInfo: LibraryInfo): Result<Unit>
    suspend fun deleteDatabase(): Result<Unit>
}

abstract class LibraryInfoRepo(
    log: ILog,
    libraryInfoRepoDatabaseName: String = DEFAULT_LIBRARYINFO_REPO_DATABASE_NAME,
    private val database: IJsonDatabase<Library, LibraryInfo>
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
            if(database.findEntityById(libraryInfo.id()) == null)
                throw Exception("Library not found, id: ${libraryInfo.id()}")

            Result.success(database.deleteEntity(libraryInfo))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun deleteDatabase(): Result<Unit> {
        return try {
            Result.success(database.deleteDatabase())
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    companion object {
        const val DEFAULT_LIBRARYINFO_REPO_DATABASE_NAME = "libraryInfoRepoDB"
    }
}