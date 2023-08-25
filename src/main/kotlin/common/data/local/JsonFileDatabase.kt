package com.realityexpander.common.data.local

import com.realityexpander.jsonConfig
import com.realityexpander.ktorLogger
import common.data.local.IJsonDatabase
import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.HasId
import domain.common.data.Model
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.internal.toImmutableMap
import java.io.File
import java.util.*

open class JsonFileDatabase<TDomain : IUUID2, TEntity : Model> (  // <User, UserInfo> -> in database:<UUID2<User>, UserInfo>
    private val databaseFilename: String = generateDefaultDatabaseFilename(),
    private val entityKSerializer: KSerializer<TEntity>,
    private val database: MutableMap<UUID2<TDomain>, TEntity> = mutableMapOf(),
): IJsonDatabase<TDomain, TEntity> {
    private val databaseFile: String = DATABASE_FOLDER + databaseFilename
    private val hiddenDatabaseFile: String = DATABASE_FOLDER + "__$databaseFilename"

    init {

        if(!File(databaseFile).exists()
            && !File(hiddenDatabaseFile).exists()) {
            ktorLogger.warn("$databaseFilename does not exist, creating DB...")

            initDatabaseFile()
        }
    }

    // *IMPORTANT*: Be sure to call this method in your subclass's init block.
    suspend fun loadFileDatabase() {
        if(!File(databaseFile).exists()
            && !File(hiddenDatabaseFile).exists()) {
            ktorLogger.error("$databaseFilename does not exist!")
            return
        }

        try {
            loadJsonDatabaseFileFromDisk()
        } catch (e: Exception) {
            ktorLogger.error("Error loading $databaseFilename: ${e.message}")
            e.printStackTrace()
        }
    }

    // *IMPORTANT*: Be sure to override this method in your subclass to make sure subclass's your
    //              lookup tables are updated when items are added/removed from the database.
    open suspend fun updateLookupTables() {}

    companion object {
        const val MAX_POLLING_ATTEMPTS = 50
        const val DATABASE_FOLDER = "fileDatabases/"  // <-- Note the trailing slash

        fun generateDefaultDatabaseFilename(): String {
            return "jsonFileDB.json" + UUID.randomUUID().toString()
        }
    }

    suspend fun toDatabaseCopy(): Map<UUID2<TDomain>, TEntity> {
        yield()
        return database.toImmutableMap()
    }

    override suspend fun findAllEntities(): List<TEntity> {
        return database.values.toList()
    }

    override suspend fun findEntityById(id: UUID2<TDomain>): TEntity? {
        return database[id]
    }

    override suspend fun findEntitiesByField(field: String, rawSearchValue: String): List<TEntity> {
        return database.values.filter { entity ->
            entity::class.members.find { entityField ->
                entityField.name == field
            }
            ?.call(entity)  // extract the field value, `call` is a Kotlin reflection method that gets the value of the field.
            .toString()
            .contains(rawSearchValue, ignoreCase = true)
        }
    }

    override suspend fun addEntity(entity: TEntity): TEntity {
        @Suppress("UNCHECKED_CAST")
        entity as HasId<UUID2<TDomain>>

        database[entity.id()] = entity
        saveJsonDatabaseFileToDisk()

        return entity;
    }

    override suspend fun updateEntity(entity: TEntity): TEntity? {
        @Suppress("UNCHECKED_CAST")
        entity as HasId<UUID2<TDomain>>

        if(!database.containsKey(entity.id())) {
            return null
        }

        database[entity.id()] = entity
        saveJsonDatabaseFileToDisk()

        return entity;
    }

    override suspend fun upsertEntity(entity: TEntity): TEntity? {
        @Suppress("UNCHECKED_CAST")
        entity as HasId<UUID2<TDomain>>

        // simulate network upsert
        return if(findEntityById(entity.id()) != null) {
            updateEntity(entity)
        } else {
            addEntity(entity)
        }
    }

    override suspend fun deleteEntity(entity: TEntity) {
        database.remove(entity.id())
        saveJsonDatabaseFileToDisk()
    }

    override suspend fun deleteEntityById(id: UUID2<TDomain>) {
        database.remove(id)
        saveJsonDatabaseFileToDisk()
    }

    public override suspend fun deleteDatabase() {
        File(databaseFile).delete()
        File(hiddenDatabaseFile).delete() // just in case
        database.clear()
        updateLookupTables()
    }

    ///////////////////////// PRIVATE METHODS /////////////////////////

    private fun initDatabaseFile() {
        // ONLY create the DB if it doesn't exist.
        // NOTE: If you want to reset the db, use the deleteDatabaseFile() method.
        if (!File(databaseFile).exists()) {
            File(databaseFile).writeText("")
        }

        // Check for the temp file & delete it
        if (File(hiddenDatabaseFile).exists()) {
            File(hiddenDatabaseFile).delete()
        }
    }

    private suspend fun loadJsonDatabaseFileFromDisk() {
        if (!File(databaseFile).exists() && !File(hiddenDatabaseFile).exists()) {
            throw Exception("Database `$databaseFilename` does not exist")
        }

        pollUntilFileExists(databaseFile)

        val fileDatabaseJson = File(databaseFile).readText()
        if (fileDatabaseJson.isNotEmpty()) {
            val entities = jsonConfig.decodeFromString(
                ListSerializer(entityKSerializer),
                fileDatabaseJson
            )

            // Add the users to the primary Lookup Table
            for (entity in entities) {
                @Suppress("UNCHECKED_CAST")
                entity as HasId<UUID2<TDomain>>
                database[entity.id()] = entity
            }
        }

        updateLookupTables()
    }

    private suspend fun saveJsonDatabaseFileToDisk() {
        updateLookupTables()  // optimistically update the lookup tables

        pollUntilFileExists(databaseFile)

        try {
            val tempFilename = renameFileBeforeWriting(databaseFile)

            File(tempFilename).writeText(
                jsonConfig.encodeToString(
                    ListSerializer(entityKSerializer),
                    database.values.toList()
                )
            )
        } catch (e: Exception) {
            ktorLogger.error("Error saving JsonFileDatabase: `$databaseFilename`, error: ${e.message}")
            throw (e)
        } finally {
            renameFileAfterWriting(databaseFile)
        }
    }

    private suspend fun pollUntilFileExists(fileName: String) {
        var pollingAttempts = 0

        if(File(fileName).exists()) {
            return
        }

        while (!File(fileName).exists()) {
            delay(100)

            pollingAttempts++;
            if (pollingAttempts > MAX_POLLING_ATTEMPTS) {
                throw Exception("File $fileName does not exist after $MAX_POLLING_ATTEMPTS attempts.")
            }
        }
    }

    private fun renameFileBeforeWriting(fileName: String): String {
        if (File(fileName).exists()) {
            renameFile(fileName, "__$fileName")
        }

        return hiddenDatabaseFile
    }

    private fun renameFileAfterWriting(fileName: String) {
        if (File(hiddenDatabaseFile).exists()) {
            renameFile(hiddenDatabaseFile, fileName)
        }
    }

    private fun renameFile(oldName: String, newName: String) {
        File(oldName).renameTo(File(newName))
    }
}
