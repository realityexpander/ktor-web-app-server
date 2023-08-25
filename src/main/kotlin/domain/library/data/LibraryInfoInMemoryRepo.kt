package domain.library.data

import common.log.ILog
import common.uuid2.UUID2
import domain.common.data.repo.Repo
import domain.library.Library
import domain.library.PrivateLibrary

/**
 * LibraryInfoFileRepo is a repository for LibraryInfo objects.
 *
 * Holds Library info for all the libraries in the system (simple CRUD operations).
 *
 * Simulates a database on a server via in-memory HashMap.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

class LibraryInfoInMemoryRepo(log: ILog) : Repo(log), ILibraryInfoRepo {
    // simulate a database on a server
    private val database: MutableMap<UUID2<Library>, LibraryInfo> = mutableMapOf()

    override suspend fun fetchLibraryInfo(id: UUID2<Library>): Result<LibraryInfo> {
        log.d(this, "libraryId: $id")

        // Simulate network/database
        return if (database.containsKey(id)) {
            Result.success(database[id] as LibraryInfo)
        } else Result.failure(Exception("Repo.LibraryInfo, Library not found, id: $id"))
    }

    override suspend fun fetchAllLibraryInfo(): Result<List<LibraryInfo>> {
        log.d(this, "fetchAllLibraryInfo")

        // Simulate network/database
        return Result.success(database.values.toList())
    }

    override suspend fun updateLibraryInfo(libraryInfo: LibraryInfo): Result<LibraryInfo> {
        log.d(this, "libraryInfo.id: " + libraryInfo.id())

        // Simulate network/database
        if (database.containsKey(libraryInfo.id())) {
            database[libraryInfo.id()] = libraryInfo
            return Result.success(libraryInfo)
        }
        return Result.failure(Exception("Repo.LibraryInfo, Library not found, id: " + libraryInfo.id()))
    }

    override suspend fun upsertLibraryInfo(libraryInfo: LibraryInfo): Result<LibraryInfo> {
        log.d(this, "libraryInfo.id: " + libraryInfo.id())

        // Simulate network/database
        database[libraryInfo.id()] = libraryInfo
        return Result.success(libraryInfo)
    }

    override suspend fun deleteLibraryInfo(libraryInfo: LibraryInfo): Result<Unit> {
        log.d(this, "libraryInfo.id: " + libraryInfo.id())

        // Simulate network/database
        database.remove(libraryInfo.id())
        return Result.success(Unit)
    }

    override suspend fun deleteDatabase(): Result<Unit> {
        log.d(this, "deleteDatabase")

        // Simulate network/database
        database.clear()
        return Result.success(Unit)
    }

    /////////////////////////
    /// Repo-specific Jobs //
    /////////////////////////

    fun removeAllOrphanPrivateLibrariesWithNoBooksInInventory() {
        log.d(this, "removeAllPrivateLibrariesWithNoBooksInInventory")
        for (entry in database.keys) {
            val uuid2TypeStr: String = entry.uuid2Type
            val libraryInfo: LibraryInfo? = database[entry]

            libraryInfo?.let {
                if (uuid2TypeStr == UUID2.calcUUID2TypeStr(PrivateLibrary::class.java)
                    && libraryInfo.findAllKnownBookIds().isEmpty()
                ) {
                    database.remove(entry)
                }
            }
        }
    }
}


