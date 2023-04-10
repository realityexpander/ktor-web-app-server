package data.local
import EmailString
import IdString
import JwtTokenString
import PasswordString
import TokenString
import com.realityexpander.jsonConfig
import com.realityexpander.ktorLogger
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.*

@Serializable
data class UserEntity(
    val id: String = UUID.randomUUID().toString(),
    val email: String,
    val password: PasswordString,
    val authToken: TokenString,
    val authJwtToken: JwtTokenString,
    val clientIpAddressWhiteList: List<String> = listOf(),
    val passwordResetToken: String? = null,
    val passwordResetJwtToken: String? = null,
)

const val MAX_POLLING_ATTEMPTS = 20
const val USERS_DB_FILENAME_DEFAULT = "usersDB.json"

class UserService(
    private val usersDBFilename: String = USERS_DB_FILENAME_DEFAULT,
) {
    private val usersDb = mutableMapOf<IdString, UserEntity>()
    private val authTokenToIdLookup = mutableMapOf<TokenString, IdString>()
    private val authJwtTokenToIdLookup = mutableMapOf<JwtTokenString, IdString>()
    private val emailToIdLookup = mutableMapOf<EmailString, IdString>()

    init {
        if(!File(usersDBFilename).exists()
            && !File("__$usersDBFilename").exists()) {
            ktorLogger.error("$usersDBFilename does not exist, creating DB...")
        }

        CoroutineScope(Dispatchers.IO).launch {
            initUserDb()

            try {
                loadUsersDbFromDisk()
            } catch (e: Exception) {
                ktorLogger.error("Error loading usersDB.json: ${e.message}")
            }
        }
    }

    private fun initUserDb() {
        // ONLY init the DB if it doesn't exist. If you want to reset the db, delete the file.
        if (!File(usersDBFilename).exists()) {
            File(usersDBFilename).writeText("")
        }

        // Check for the temp file & delete it
        if (!File("__${usersDBFilename}").exists()) {
            File("__${usersDBFilename}").delete()
        }
    }

    fun getAllUsers(): List<UserEntity> {
        return usersDb.values.toList()
    }

    fun getUserById(id: String): UserEntity? {
        return usersDb[id]
    }

    fun getUserByEmail(email: EmailString): UserEntity? {
        return usersDb[emailToIdLookup[email]]
    }

    fun getUserByAuthToken(authToken: TokenString?): UserEntity? {
        if (authToken == null) return null
        return usersDb[authTokenToIdLookup[authToken]]
    }

    fun getUserByAuthJwtToken(authJwtToken: JwtTokenString?): UserEntity? {
        if (authJwtToken == null) return null
        return usersDb[authJwtTokenToIdLookup[authJwtToken]]
    }

    fun getUserByPasswordResetToken(passwordResetToken: String): UserEntity? {
        return usersDb.values.find { it.passwordResetToken == passwordResetToken }
    }

    fun getUserByPasswordResetJwtToken(passwordResetJwtToken: String): UserEntity? {
        return usersDb.values.find { it.passwordResetJwtToken == passwordResetJwtToken }
    }

    suspend fun addUser(user: UserEntity) {
        usersDb[user.id] = user
        saveUsersDbToDisk()
    }

    suspend fun updateUser(user: UserEntity) {
        usersDb[user.id] = user
        saveUsersDbToDisk()
    }

    suspend fun deleteUser(user: UserEntity) {
        usersDb.remove(user.id)
        saveUsersDbToDisk()
    }

    suspend fun deleteUserById(id: IdString) {
        usersDb.remove(id)
        saveUsersDbToDisk()
    }

    suspend fun deleteUserByEmail(email: EmailString) {
        usersDb.remove(emailToIdLookup[email])
        saveUsersDbToDisk()
    }

    suspend fun deleteUserByAuthToken(authToken: TokenString) {
        usersDb.remove(authTokenToIdLookup[authToken])
        saveUsersDbToDisk()
    }

    suspend fun deleteUserByAuthJwtToken(authJwtToken: JwtTokenString) {
        usersDb.remove(authJwtTokenToIdLookup[authJwtToken])
        saveUsersDbToDisk()
    }


    ///////////////////////// PRIVATE FUNCTIONS /////////////////////////

    private suspend fun loadUsersDbFromDisk() {
        if (!File(usersDBFilename).exists() && !File("__$usersDBFilename").exists()) {
            throw Exception("Database `$usersDBFilename` does not exist")
        }

        pollIfFileExists(usersDBFilename)

        val userDBJson = File(usersDBFilename).readText()
        if (userDBJson.isNotEmpty()) {
            val users = jsonConfig.decodeFromString<List<UserEntity>>(userDBJson)
            for (user in users) {
                usersDb[user.id] = user
            }
        }

        updateLookupTables()
    }

    private suspend fun saveUsersDbToDisk() {
        pollIfFileExists(usersDBFilename)

        try {
            val tempFilename = renameFileBeforeWriting(usersDBFilename)

            File(tempFilename).writeText(
                jsonConfig.encodeToString(
                    usersDb.values.toList()
                )
            )
            updateLookupTables()
        } catch (e: Exception) {
            ktorLogger.error("Error saving usersDB.json: ${e.message}")
        } finally {
            renameFileAfterWriting(usersDBFilename)
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

    private fun updateLookupTables() {
        updateAuthTokenToEmailLookupTable()
        updateAuthJwtTokenToEmailLookupTable()
        updateEmailToIdLookupTable()
    }

    private fun updateAuthTokenToEmailLookupTable() {
        for (user in usersDb.values) {
            authTokenToIdLookup[user.authToken] = user.id
        }
    }

    private fun updateAuthJwtTokenToEmailLookupTable() {
        for (user in usersDb.values) {
            authJwtTokenToIdLookup[user.authJwtToken] = user.id
        }
    }

    private fun updateEmailToIdLookupTable() {
        for (user in usersDb.values) {
            emailToIdLookup[user.email] = user.id
        }
    }

}