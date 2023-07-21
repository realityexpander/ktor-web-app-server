package com.realityexpander.domain.auth

import com.realityexpander.common.data.local.FileDatabase
import com.realityexpander.common.uuid2.toUUID
import common.uuid2.UUID2
import domain.user.User
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import util.*
import java.util.*

@Serializable
data class UserEntity(
    override val id: UUID2<@Contextual User>,
    val email: String,
    val password: PasswordString,
    val authToken: TokenStr = "",
    val authJwtToken: JwtTokenStr = "",
    val clientIpAddressWhiteList: List<String> = listOf(),
    val passwordResetToken: String? = null,
    val passwordResetJwtToken: String? = null,
) : FileDatabase.Entity<UUID2<User>>()

class UserRepository(
    usersDBFilename: String = DEFAULT_USERS_DB_FILENAME,
) : FileDatabase<UUID2<User>, UserEntity>(usersDBFilename, UserEntity.serializer()) {

    private val authTokenToUserIdLookup = mutableMapOf<TokenStr, UUID2<User>>()
    private val authJwtTokenToUserIdLookup = mutableMapOf<JwtTokenStr, UUID2<User>>()
    private val emailToUserIdLookup = mutableMapOf<EmailStr, UUID2<User>>()

    fun findAllUsers(): List<UserEntity> {
        return super.findAllEntities()
    }

    fun findUserById(id: UUID2<User>): UserEntity? {
        return super.findEntityById(id)
    }

    fun findUserByEmail(email: EmailStr): UserEntity? {
        return emailToUserIdLookup[email]?.let { id -> super.findEntityById(id) }
    }

    fun findUserByAuthToken(authToken: TokenStr?): UserEntity? {
        if (authToken == null) return null
        return authTokenToUserIdLookup[authToken]?.let { id -> super.findEntityById(id) }
    }

    fun findUserByAuthJwtToken(authJwtToken: JwtTokenStr?): UserEntity? {
        if (authJwtToken == null) return null
        return authJwtTokenToUserIdLookup[authJwtToken]?.let { id -> super.findEntityById(id) }
    }

    fun findUserByPasswordResetToken(passwordResetToken: String): UserEntity? {
        return super.toDbCopy().values.find { it.passwordResetToken == passwordResetToken }
    }

    fun findUserByPasswordResetJwtToken(passwordResetJwtToken: String): UserEntity? {
        return super.toDbCopy().values.find { it.passwordResetJwtToken == passwordResetJwtToken }
    }

    suspend fun addUser(user: UserEntity) {
        super.addEntity(user)
    }

    suspend fun updateUser(user: UserEntity): UserEntity {
        super.updateEntity(user)

        return user;
    }

    suspend fun deleteUser(user: UserEntity) {
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

    override fun updateLookupTables() {
        val usersDb = super.toDbCopy()

        updateAuthTokenToEmailLookupTable(usersDb)
        updateAuthJwtTokenToEmailLookupTable(usersDb)
        updateEmailToIdLookupTable(usersDb)
    }

    ///////////////////////// PRIVATE FUNCTIONS /////////////////////////

    private fun updateAuthTokenToEmailLookupTable(usersDb: Map<UUID2<User>, UserEntity>) {
        for (user in usersDb.values) {
            authTokenToUserIdLookup[user.authToken] = user.id
        }
    }

    private fun updateAuthJwtTokenToEmailLookupTable(usersDb: Map<UUID2<User>, UserEntity>) {
        for (user in usersDb.values) {
            authJwtTokenToUserIdLookup[user.authJwtToken] = user.id
        }
    }

    private fun updateEmailToIdLookupTable(usersDb: Map<UUID2<User>, UserEntity>) {
        for (user in usersDb.values) {
            emailToUserIdLookup[user.email] = user.id
        }
    }

    companion object {
        const val DEFAULT_USERS_DB_FILENAME = "usersDB.json"
    }

}