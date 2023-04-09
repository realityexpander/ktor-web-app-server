
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
    private val usersDb = mutableMapOf<EmailString, UserEntity>()
    private val authTokenToEmailLookup = mutableMapOf<TokenString, EmailString>()
    private val authJwtTokenToEmailLookup = mutableMapOf<JwtTokenString, EmailString>()

    init {
        if(!File(usersDBFilename).exists()
            && !File("__usersDB.json").exists()) {
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

    fun getUserById(id: String): UserEntity? {
        return usersDb.values.find { it.id == id }
    }

    fun getUserByEmail(email: EmailString): UserEntity? {
        return usersDb[email]
    }

    fun getUserByToken(authToken: TokenString?): UserEntity? {
        if (authToken == null) return null
        return usersDb[authTokenToEmailLookup[authToken]]
        //return usersDb.values.find { it.authToken == token }
    }

    fun getUserByAuthJwtToken(authJwtToken: JwtTokenString): UserEntity? {
        return usersDb.values.find { it.authJwtToken == authJwtToken }
    }

    fun getUserByPasswordResetToken(passwordResetToken: String): UserEntity? {
        return usersDb.values.find { it.passwordResetToken == passwordResetToken }
    }

    fun getUserByPasswordResetJwtToken(passwordResetJwtToken: String): UserEntity? {
        return usersDb.values.find { it.passwordResetJwtToken == passwordResetJwtToken }
    }

    suspend fun addUser(user: UserEntity) {
        usersDb[user.email] = user
        saveUsersDbToDisk()
    }

    suspend fun updateUser(user: UserEntity) {
        usersDb[user.email] = user
        saveUsersDbToDisk()
    }

    suspend fun deleteUser(user: UserEntity) {
        usersDb.remove(user.email)
        saveUsersDbToDisk()
    }

    suspend fun deleteUserByEmail(email: EmailString) {
        usersDb.remove(email)
        saveUsersDbToDisk()
    }

    suspend fun deleteUserByToken(authToken: TokenString) {
        usersDb.remove(authTokenToEmailLookup[authToken])
//        usersDb.remove(usersDb.values.find { it.authToken == token }?.email)
        saveUsersDbToDisk()
    }

    suspend fun deleteUserByJwtToken(authJwtToken: JwtTokenString) {
        usersDb.remove(authJwtTokenToEmailLookup[authJwtToken])
//        usersDb.remove(usersDb.values.find { it.authJwtToken == jwtToken }?.email)
        saveUsersDbToDisk()
    }


    ///////////////////////// PRIVATE FUNCS /////////////////////////

    // Load the users from the resources json file
    private suspend fun loadUsersDbFromDisk() {
//        if (!File(USERS_DB_FILENAME).exists()) {
//            File(USERS_DB_FILENAME).writeText("")
//        }

        pollIfFileExists(usersDBFilename)

        val userDBJson = File(usersDBFilename).readText()
        if (userDBJson.isNotEmpty()) {
            val users = jsonConfig.decodeFromString<List<UserEntity>>(userDBJson)
            for (user in users) {
                usersDb[user.email] = user
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
            //Thread.sleep(100)
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
    }

    private fun updateAuthTokenToEmailLookupTable() {
        // setup token to email Lookup table
        for (user in usersDb.values) {
            authTokenToEmailLookup[user.authToken] = user.email
        }
    }

    private fun updateAuthJwtTokenToEmailLookupTable() {
        // setup auth JWT token to email Lookup table
        for (user in usersDb.values) {
            authJwtTokenToEmailLookup[user.authJwtToken] = user.email
        }
    }

}