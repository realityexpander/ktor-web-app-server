package domain.library.data

import common.uuid2.UUID2
import domain.common.data.repo.IRepo
import domain.library.Library

/**
 * ILibraryInfoRepo is an interface for the LibraryInfoRepo class.
 *
 * Meant to be used by the domain layer, not the data layer.
 *
 * Useful for testing.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

interface ILibraryInfoRepo : IRepo {
    fun fetchLibraryInfo(id: UUID2<Library>): Result<LibraryInfo>
    fun updateLibraryInfo(libraryInfo: LibraryInfo): Result<LibraryInfo>
    fun upsertLibraryInfo(libraryInfo: LibraryInfo): Result<LibraryInfo>
}
