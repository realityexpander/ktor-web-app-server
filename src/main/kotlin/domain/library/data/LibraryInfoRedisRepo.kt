package com.realityexpander.domain.library.data

import com.realityexpander.common.data.local.JsonRedisDatabase
import com.realityexpander.domain.account.data.AccountInfoRepo.Companion.DEFAULT_LIBRARYINFO_REPO_DATABASE_FILENAME
import common.data.local.IJsonDatabase
import common.log.ILog
import domain.library.Library
import domain.library.data.LibraryInfo
import domain.library.data.LibraryInfoRepo

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

class LibraryInfoRedisRepo(
    log: ILog,
    libraryInfoRepoDatabaseName: String = DEFAULT_LIBRARYINFO_REPO_DATABASE_NAME,

    // Use a file database to persist the library info
    database: IJsonDatabase<Library, LibraryInfo> = object :
        JsonRedisDatabase<Library, LibraryInfo>(
            libraryInfoRepoDatabaseName,
            LibraryInfo.serializer()
        ) {}


) : LibraryInfoRepo(log, libraryInfoRepoDatabaseName, database) {

    companion object {
        const val DEFAULT_LIBRARYINFO_REPO_DATABASE_NAME = "libraryInfoRepoDB"
    }
}