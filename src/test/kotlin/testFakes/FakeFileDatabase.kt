package testFakes

import com.realityexpander.common.data.local.FileDatabase
import common.uuid2.UUID2
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer

class FakeFileDatabase<TKey : UUID2<*>, TEntity : FileDatabase.HasId<TKey>>(
    databaseFilename: String = FileDatabase.generateDefaultDatabaseFilename(),
    entityKSerializer: KSerializer<TEntity>,
    database: MutableMap<TKey, TEntity> = mutableMapOf(),
) : FileDatabase<TKey, TEntity>(databaseFilename, entityKSerializer, database) {

    init {
        runBlocking {
            super.loadFileDatabase()
        }
    }

    var calledUpdateLookupTables = false

    override suspend fun updateLookupTables() {
        calledUpdateLookupTables = true
    }
}
