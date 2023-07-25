package testFakes.common.data.local

import com.realityexpander.common.data.local.JsonFileDatabase
import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.Model
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer

/**
 * **FakeFileDatabase**
 *
 * A JsonFileDatabase implementation that creates a simple FakeFileDatabase for testing.
 *
 * Simulates a REST API that is backed by a json file.
 *
 * Data is persisted to disk, the database is not reset on each run.
 * So, be sure to delete the database file after each test run.
 *
 * @param TDomain The type of UUID2 to use for the database. ie: `User -> UUID2<User>`
 * @param TEntity The type of EntiyInfo for the entities in the database.
 * @param databaseFilename The filename of the database.
 * @param entityKSerializer The kotlinx json serializer to use for the database entities.
 */

class FakeFileDatabase<TDomain : IUUID2, TEntity : Model>(
    databaseFilename: String = JsonFileDatabase.generateDefaultDatabaseFilename(),
    entityKSerializer: KSerializer<TEntity>,
    database: MutableMap<TDomain, TEntity> = mutableMapOf(),
) : JsonFileDatabase<TDomain, TEntity>(databaseFilename, entityKSerializer) {

    init {
        runBlocking {
            super.loadFileDatabase() // required to load the database
        }
    }

    var calledUpdateLookupTables = false

    override suspend fun updateLookupTables() {
        calledUpdateLookupTables = true
    }
}
