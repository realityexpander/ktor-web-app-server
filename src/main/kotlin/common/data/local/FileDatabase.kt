package com.realityexpander.common.data.local

import com.realityexpander.jsonConfig
import com.realityexpander.ktorLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.internal.toImmutableMap
import java.io.File
import java.util.*

/**
 * FileDatabase
 *
 * This is a simple file-based database that uses Kotlin Serialization to store data in a JSON file.
 * It's not meant to be a full-featured database, but it's a good starting point for small projects.
 *
 * * It's thread-safe, as the file can only be written one request at a time.
 * * The database file is temporarily renamed while updates are being made, so that the file is never in an invalid
 *   state.
 * * Queued requests must wait until the file is available again.
 * * Be sure to implement the **`updateLookupTables()`** method to update any lookup tables you may have defined in
 *   the subclass.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

abstract class FileDatabase<TKey : Any, TEntity : FileDatabase.Entity<TKey>>(
    private val dbFilename: String = generateDefaultDbFilename(),
    private val serializer: KSerializer<TEntity>,
    private val db: MutableMap<TKey, TEntity> = mutableMapOf(),
) {
    abstract class Entity<TKey : Any> {
        abstract val id: TKey
    }

    companion object {
        const val MAX_POLLING_ATTEMPTS = 50

        fun generateDefaultDbFilename(): String {
            return "fileDB.json" + UUID.randomUUID().toString()
        }
    }

    init {

        if(!File(dbFilename).exists()
            && !File("__$dbFilename").exists()) {
            ktorLogger.error("$dbFilename does not exist, creating DB...")
        }

        CoroutineScope(Dispatchers.IO).launch {
            initDb()

            try {
                loadUsersDbFromDisk()
            } catch (e: Exception) {
                ktorLogger.error("Error loading usersDB.json: ${e.message}")
            }
        }

    }

    fun toDbCopy(): Map<TKey, TEntity> {
        return db.toImmutableMap()
    }

    fun findAllEntities(): List<TEntity> {
        return db.values.toList()
    }

    fun findEntityById(id: TKey): TEntity? {
        return db[id]
    }

    suspend fun addEntity(entity: TEntity) {
        db[entity.id] = entity
        saveUsersDbToDisk()
    }

    suspend fun updateEntity(entity: TEntity): TEntity {
        db[entity.id] = entity
        saveUsersDbToDisk()

        return entity;
    }

    suspend fun deleteEntity(entity: TEntity) {
        db.remove(entity.id)
        saveUsersDbToDisk()
    }

    suspend fun deleteEntityById(id: TKey) {
        db.remove(id)
        saveUsersDbToDisk()
    }

    // Note: Be sure to override this method in your subclass to make sure subclass's your lookup tables are updated.
    abstract fun updateLookupTables()

    ///////////////////////// PRIVATE FUNCTIONS /////////////////////////

    private fun initDb() {
        // ONLY init the DB if it doesn't exist. If you want to reset the db, delete the file.
        if (!File(dbFilename).exists()) {
            File(dbFilename).writeText("")
        }

        // Check for the temp file & delete it
        if (!File("__${dbFilename}").exists()) {
            File("__${dbFilename}").delete()
        }
    }

    private suspend fun loadUsersDbFromDisk() {
        if (!File(dbFilename).exists() && !File("__$dbFilename").exists()) {
            throw Exception("Database `$dbFilename` does not exist")
        }

        pollIfFileExists(dbFilename)

        val userDBJson = File(dbFilename).readText()
        if (userDBJson.isNotEmpty()) {
            val entities = jsonConfig.decodeFromString(
                ListSerializer(serializer),
                userDBJson
            )

            // Add the users to the primary Lookup Table
            for (entity in entities) {
                db[entity.id] = entity
            }
        }

        updateLookupTables()
    }

    private suspend fun saveUsersDbToDisk() {
        updateLookupTables()  // optimistically update the lookup tables

        CoroutineScope(Dispatchers.IO).launch {
            pollIfFileExists(dbFilename)
            try {
                val tempFilename = renameFileBeforeWriting(dbFilename)

                File(tempFilename).writeText(
                    jsonConfig.encodeToString(
                        ListSerializer(serializer),
                        db.values.toList()
                    )
                )
            } catch (e: Exception) {
                ktorLogger.error("Error saving FileDatabase: `$dbFilename`, error: ${e.message}")
            } finally {
                renameFileAfterWriting(dbFilename)
            }
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