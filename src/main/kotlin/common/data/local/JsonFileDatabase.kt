package com.realityexpander.common.data.local

import com.realityexpander.common.data.local.JsonFileDatabase.Companion.MAX_POLLING_ATTEMPTS
import com.realityexpander.jsonConfig
import com.realityexpander.ktorLogger
import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.common.data.HasId
import domain.common.data.Model
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.internal.toImmutableMap
import java.io.File
import java.util.*

/**
 * **JsonFileDatabase**
 *
 * - Performs simple key-value database operations on a JSON file.
 *
 * This implements a persistent database that uses Kotlinx Serialization to store data in a JSON formatted file.
 * The entire database in kept memory, and writes the entire database to disk after every CUD change.
 * Reads are very fast because the data is kept in RAM, but writes are slower since the entire database
 * is written to disk after every change.
 *
 * It's not meant to be a full-featured database, but it's a good starting point for small projects and
 * for prototyping & testing.
 *
 * * It's thread-safe, as the file can only be written one request at a time.
 * * The database file is temporarily renamed while updates are being made, so that the file is never in an invalid
 *   state and impossible to have multiple simultaneous writers.
 * * Queued requests will poll & wait until the file is available again.
 * * Be sure to implement the **`updateLookupTables()`** method to update any lookup tables you may have defined in
 *   the subclass.
 *
 * * **`TKey`** The type of the key used to identify the entities in the database.
 * * **`abstract class  Entity<TKey : Any>`** is a Marker interface for entities that will be stored in the database.
 *
 * Note: It is possible to have multiple databases, but they must have different filenames.
 *
 * @param databaseFilename The name of the JSON file to use for the database.
 * @param entityKSerializer The Kotlin class serializer to use for the Entities in the database.
 * @param database The in-memory database that represents the JSON file.
 * @property MAX_POLLING_ATTEMPTS The maximum number of times to poll the file system for the database file to become
 *           available. Each attempt is 100ms, so the default is 5 seconds.
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

abstract class JsonFileDatabase<TDomain : IUUID2, TEntity : Model> (  // <User, UserInfo> -> in database:<UUID2<User>, UserInfo>
    private val databaseFilename: String = generateDefaultDatabaseFilename(),
    private val entityKSerializer: KSerializer<TEntity>,
    private val database: MutableMap<UUID2<TDomain>, TEntity> = mutableMapOf(),
) {
    private val databaseFile: String = databaseFolder + databaseFilename
    private val hiddenDatabaseFile: String = databaseFolder + "__$databaseFilename"

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

    companion object {
        const val MAX_POLLING_ATTEMPTS = 50
        const val databaseFolder = "fileDatabases/"  // <-- Note the trailing slash

        fun generateDefaultDatabaseFilename(): String {
            return "jsonFileDB.json" + UUID.randomUUID().toString()
        }
    }

    suspend fun toDatabaseCopy(): Map<UUID2<TDomain>, TEntity> {
        yield()
        return database.toImmutableMap()
    }

    suspend fun findAllEntities(): List<TEntity> {
        return database.values.toList()
    }

    suspend fun findEntityById(id: UUID2<TDomain>): TEntity? {
        return database[id]
    }

    suspend fun addEntity(entity: TEntity): TEntity {
        @Suppress("UNCHECKED_CAST")
        entity as HasId<UUID2<TDomain>>

        database[entity.id()] = entity
        saveJsonDatabaseFileToDisk()

        return entity;
    }

    suspend fun updateEntity(entity: TEntity): TEntity? {
        @Suppress("UNCHECKED_CAST")
        entity as HasId<UUID2<TDomain>>

        if(!database.containsKey(entity.id())) {
            return null
        }

        database[entity.id()] = entity
        saveJsonDatabaseFileToDisk()

        return entity;
    }

    suspend fun upsertEntity(entity: TEntity): TEntity? {
        @Suppress("UNCHECKED_CAST")
        entity as HasId<UUID2<TDomain>>

        // simulate network upsert
        return if(findEntityById(entity.id()) != null) {
            updateEntity(entity)
        } else {
            addEntity(entity)
        }
    }

    suspend fun deleteEntity(entity: TEntity) {
        database.remove(entity.id())
        saveJsonDatabaseFileToDisk()
    }

    suspend fun deleteEntityById(id: UUID2<TDomain>) {
        database.remove(id)
        saveJsonDatabaseFileToDisk()
    }

    // Note: Be sure to override this method in your subclass to make sure subclass's your lookup tables are updated.
    abstract suspend fun updateLookupTables()

    public suspend fun deleteDatabaseFile() {
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