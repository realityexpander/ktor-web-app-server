package com.realityexpander.domain.auth

import com.realityexpander.common.data.local.JsonFileDatabase
import common.uuid2.UUID2
import domain.common.data.info.local.InfoEntity
import domain.user.User
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import util.*

// Note: Not part of the LibraryApp hierarchy YET.
// This is a standalone repository for the Ktor Auth.

@Serializable
data class UserAuthEntity(
    override val id: UUID2<User>,
    val email: String,
    val password: PasswordString,
    val authToken: TokenStr = "",
    val authJwtToken: JwtTokenStr = "",
    val clientIpAddressWhiteList: List<String> = listOf(),
    val passwordResetToken: String? = null,
    val passwordResetJwtToken: String? = null,
) : InfoEntity(id) {
    override fun id(): UUID2<User> {
        return id
    }
}

class AuthenticationRepository(
    userAuthDatabaseFilename: String = DEFAULT_AUTHENTICATION_REPOSITORY_DATABASE_FILENAME,
    private val authTokenToUserIdLookup: MutableMap<TokenStr, UUID2<User>> = mutableMapOf(),
    private val authJwtTokenToUserIdLookup: MutableMap<JwtTokenStr, UUID2<User>> = mutableMapOf(),
    private val emailToUserIdLookup: MutableMap<EmailStr, UUID2<User>> = mutableMapOf()
) : JsonFileDatabase<User, UserAuthEntity>(userAuthDatabaseFilename, UserAuthEntity.serializer()) {

    init {
        runBlocking {
            super.loadFileDatabase()  // required to load the database
        }
    }

    suspend fun findAllUsers(): List<UserAuthEntity> {
        return super.findAllEntities()
    }

    suspend fun findUserById(id: UUID2<User>): UserAuthEntity? {
        return super.findEntityById(id)
    }

    suspend fun findUserByEmail(email: EmailStr): UserAuthEntity? {
        return emailToUserIdLookup[email]?.let { id -> super.findEntityById(id) }
    }

    suspend fun findUserByAuthToken(authToken: TokenStr?): UserAuthEntity? {
        if (authToken == null) return null
        return authTokenToUserIdLookup[authToken]?.let { id -> super.findEntityById(id) }
    }

    suspend fun findUserByAuthJwtToken(authJwtToken: JwtTokenStr?): UserAuthEntity? {
        if (authJwtToken == null) return null
        return authJwtTokenToUserIdLookup[authJwtToken]?.let { id -> super.findEntityById(id) }
    }

    suspend fun findUserByPasswordResetToken(passwordResetToken: String): UserAuthEntity? {
        return super.toDatabaseCopy().values.find { it.passwordResetToken == passwordResetToken }
    }

    suspend fun findUserByPasswordResetJwtToken(passwordResetJwtToken: String): UserAuthEntity? {
        return super.toDatabaseCopy().values.find { it.passwordResetJwtToken == passwordResetJwtToken }
    }

    suspend fun addUser(user: UserAuthEntity) {
        super.addEntity(user)
    }

    suspend fun updateUser(user: UserAuthEntity): UserAuthEntity {
        super.updateEntity(user)

        return user;
    }

    suspend fun deleteUser(user: UserAuthEntity) {
        super.deleteEntity(user)
    }

    suspend fun deleteUserById(id: UUID2<User>) {
        super.deleteEntity(super.findEntityById(id)!!)
    }

    suspend fun deleteUserByEmail(email: EmailStr) {
        val userId = emailToUserIdLookup[email]
            ?: throw Exception("User with email $email not found")
        val user = super.findEntityById(userId)
            ?: throw Exception("User with email $email not found")

        super.deleteEntity(user)
    }

    suspend fun deleteUserByAuthToken(authToken: TokenStr) {
        val userId = authTokenToUserIdLookup[authToken]
            ?: throw Exception("User with authToken $authToken not found")
        val user = super.findEntityById(userId)
            ?: throw Exception("User with authToken $authToken not found")

        super.deleteEntity(user)
    }

    suspend fun deleteUserByAuthJwtToken(authJwtToken: JwtTokenStr) {
        val userId = authJwtTokenToUserIdLookup[authJwtToken]
            ?: throw Exception("User with authJwtToken $authJwtToken not found")
        val user = super.findEntityById(userId)
            ?: throw Exception("User with authJwtToken $authJwtToken not found")

        super.deleteEntity(user)
    }

    override suspend fun updateLookupTables() {
        val usersDb = super.toDatabaseCopy()

        updateAuthTokenToEmailLookupTable(usersDb)
        updateAuthJwtTokenToEmailLookupTable(usersDb)
        updateEmailToIdLookupTable(usersDb)
    }

    ///////////////////////// PRIVATE METHODS /////////////////////////

    private suspend fun updateAuthTokenToEmailLookupTable(usersDb: Map<UUID2<User>, UserAuthEntity>) {
        for (user in usersDb.values) {
            authTokenToUserIdLookup[user.authToken] = user.id
        }
    }

    private suspend fun updateAuthJwtTokenToEmailLookupTable(usersDb: Map<UUID2<User>, UserAuthEntity>) {
        for (user in usersDb.values) {
            authJwtTokenToUserIdLookup[user.authJwtToken] = user.id
        }
    }

    private suspend fun updateEmailToIdLookupTable(usersDb: Map<UUID2<User>, UserAuthEntity>) {
        for (user in usersDb.values) {
            emailToUserIdLookup[user.email] = user.id
        }
    }

    companion object {
        const val DEFAULT_AUTHENTICATION_REPOSITORY_DATABASE_FILENAME = "userRepositoryDB.json"
    }
}