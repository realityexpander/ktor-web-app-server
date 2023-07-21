package com.realityexpander.common.data.local

import com.realityexpander.common.data.local.FileDatabase.Companion.MAX_POLLING_ATTEMPTS
import com.realityexpander.jsonConfig
import com.realityexpander.ktorLogger
import common.uuid2.UUID2
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.internal.toImmutableMap
import java.io.File
import java.util.*

/**
 * **FileDatabase**
 *
 * - Performs simple key-value operations on a JSON file.
 *
 * This is a simple file-based database that uses Kotlin Serialization to store data in a JSON formatted file.
 * It keeps the entire database in memory, and writes the entire database to disk after every CUD update.
 *
 * It's not meant to be a full-featured database, but it's a good starting point for small projects.
 *
 * * It's thread-safe, as the file can only be written one request at a time.
 * * The database file is temporarily renamed while updates are being made, so that the file is never in an invalid
 *   state and impossible to have multiple simultaneous writers.
 * * Queued requests will poll & wait until the file is available again.
 * * Be sure to implement the **`updateLookupTables()`** method to update any lookup tables you may have defined in
 *   the subclass.
 *
 * * **`TKey`** The type of the key used to identify the entities in the database.
 * * **`abstract class  Entity<TKey : Any>`** is a Marker interface for entities that can be stored in the database.
 *
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 * @param databaseFilename The name of the JSON file to use for the database.
 * @param entityKSerializer The Kotlin Serialization serializer to use for the Entities in the database.
 * @param database The in-memory database that represents the JSON file.
 * @property MAX_POLLING_ATTEMPTS The maximum number of times to poll the file system for the database file to become
 *           available.
 */

//abstract class FileDatabase<TKey : Any, TEntity : FileDatabase.Entity<TKey>>(
abstract class FileDatabase<TKey : UUID2<*>, TEntity : FileDatabase.HasId<TKey>>(
    private val databaseFilename: String = generateDefaultDatabaseFilename(),
    private val entityKSerializer: KSerializer<TEntity>,
    private val database: MutableMap<TKey, TEntity> = mutableMapOf(),
) {

    init {

        if(!File(databaseFilename).exists()
            && !File("__$databaseFilename").exists()) {
            ktorLogger.warn("$databaseFilename does not exist, creating DB...")
        }

        runBlocking {
            initJsonDatabaseFile()

            try {
                loadJsonDatabaseFileFromDisk()
            } catch (e: Exception) {
                ktorLogger.error("Error loading $databaseFilename: ${e.message}")
            }
        }
    }

    // Marker interface for entities that can be stored in the database.
    interface HasId<TKey : UUID2<*>> {
        abstract fun id(): TKey
    }

    companion object {
        const val MAX_POLLING_ATTEMPTS = 50

        fun generateDefaultDatabaseFilename(): String {
            return "fileDB.json" + UUID.randomUUID().toString()
        }
    }

    suspend fun toDatabaseCopy(): Map<TKey, TEntity> {
        return database.toImmutableMap()
    }

    suspend fun findAllEntities(): List<TEntity> {
        return database.values.toList()
    }

    suspend fun findEntityById(id: TKey): TEntity? {
        return database[id]
    }

    suspend fun addEntity(entity: TEntity): TEntity {
        database[entity.id()] = entity
        saveJsonDatabaseFileToDisk()

        return entity;
    }

    suspend fun updateEntity(entity: TEntity): TEntity {
        database[entity.id()] = entity
        saveJsonDatabaseFileToDisk()

        return entity;
    }

    suspend fun deleteEntity(entity: TEntity) {
        database.remove(entity.id())
        saveJsonDatabaseFileToDisk()
    }

    suspend fun deleteEntityById(id: TKey) {
        database.remove(id)
        saveJsonDatabaseFileToDisk()
    }

    // Note: Be sure to override this method in your subclass to make sure subclass's your lookup tables are updated.
    abstract suspend fun updateLookupTables()

    public fun deleteDatabaseFile() {
        File(databaseFilename).delete()
    }

    ///////////////////////// PRIVATE FUNCTIONS /////////////////////////

    private fun initJsonDatabaseFile() {
        // ONLY init the DB if it doesn't exist. If you want to reset the db, delete the file.
        if (!File(databaseFilename).exists()) {
            File(databaseFilename).writeText("")
        }

        // Check for the temp file & delete it
        if (!File("__${databaseFilename}").exists()) {
            File("__${databaseFilename}").delete()
        }
    }

    private suspend fun loadJsonDatabaseFileFromDisk() {
        if (!File(databaseFilename).exists() && !File("__$databaseFilename").exists()) {
            throw Exception("Database `$databaseFilename` does not exist")
        }

        pollIfFileExists(databaseFilename)

        val fileDatabaseJson = File(databaseFilename).readText()
        if (fileDatabaseJson.isNotEmpty()) {
            val entities = jsonConfig.decodeFromString(
                ListSerializer(entityKSerializer),
                fileDatabaseJson
            )

            // Add the users to the primary Lookup Table
            for (entity in entities) {
                database[entity.id()] = entity
            }
        }

        updateLookupTables()
    }

    private suspend fun saveJsonDatabaseFileToDisk() {
        updateLookupTables()  // optimistically update the lookup tables

        pollIfFileExists(databaseFilename)

        try {
            val tempFilename = renameFileBeforeWriting(databaseFilename)

            File(tempFilename).writeText(
                jsonConfig.encodeToString(
                    ListSerializer(entityKSerializer),
                    database.values.toList()
                )
            )
        } catch (e: Exception) {
            ktorLogger.error("Error saving FileDatabase: `$databaseFilename`, error: ${e.message}")
        } finally {
            renameFileAfterWriting(databaseFilename)
        }
    }

    private suspend fun pollIfFileExists(fileName: String) {
        var pollingAttempts = 0

        while (!File(fileName).exists()) {
            delay(100)

            pollingAttempts++;
            if (pollingAttempts > MAX_POLLING_ATTEMPTS) {
                throw Exception("File $fileName does not exist after $MAX_POLLING_ATTEMPTS attempts.")
            }
        }
    }

    private fun renameFile(oldName: String, newName: String) {
        File(oldName).renameTo(File(newName))
    }

    private fun renameFileBeforeWriting(fileName: String): String {
        if (File(fileName).exists()) {
            renameFile(fileName, "__$fileName")
        }

        return "__$fileName"
    }

    private fun renameFileAfterWriting(fileName: String) {
        if (File("__$fileName").exists()) {
            renameFile("__$fileName", fileName)
        }
    }
}