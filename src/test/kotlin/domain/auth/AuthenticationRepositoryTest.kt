package domain.auth

import com.realityexpander.domain.auth.UserAuthEntity
import com.realityexpander.domain.auth.AuthenticationRepository
import common.uuid2.UUID2
import common.uuid2.UUID2.Companion.fromUUID2StrToUUID2
import domain.user.User
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.util.*

class AuthenticationRepositoryTest {

    private val tempName = UUID.randomUUID().toString()

    private val userRepo = AuthenticationRepository(
        userAuthDatabaseFilename = "test-$tempName-usersDB.json",
    )
    @Suppress("UNCHECKED_CAST") // for UUID2<User>
    private val user = UserAuthEntity(
        id = "UUID2:Role.User@a4cdc86e-b2da-4c87-a0c4-313b8672692c".fromUUID2StrToUUID2() as UUID2<User>,
        email = "a@b.c",
        password =
            """
            |${"$"}argon2id${"$"}v=19${"$"}m=65536,t=3,p=1${"$"}uD3dqakbjKWCPUM8L17aJQ${"$"}Oa2aGDLaH8SEJIYS16ORhGroPQ+o4NdKJcgBT4TfCvtraywfkc1tBS6lcrL06GhGqHfeML1vkqJdsqHCxfmKLw
            |
            |""".trimMargin(),
        authToken = "b977bdd4-b1c3-4efe-beec-30f873f361ba",
        authJwtToken = "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJBdXRoZW50aWNhdGlvbiIsImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODA4MS8iLCJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjgwODEvL2FwaS9sb2dpbiIsImlkIjoiVVVJRDI6Um9sZS5Vc2VyQGE0Y2RjODZlLWIyZGEtNGM4Ny1hMGM0LTMxM2I4NjcyNjkyYyIsImVtYWlsIjoiYUBiLmMiLCJjbGllbnRJcEFkZHJlc3MiOiI3NzdlYzFkMS0yOGNmLTQwOWMtYmEzZS02NjI4OGZlMjcxOWYiLCJleHAiOjE2OTA1ODA4NDZ9.0-Md4Enf6ZMYWUu3D0GsqNw68Ci3nZEFjbjn8XKzw09_7mCCtW5Eb31uQJn4l-QsGOyoVseNH2TIbeBzToDXVw",
        clientIpAddressWhiteList = listOf("100.100.100.100"),
        passwordResetToken = "123456",
        passwordResetJwtToken = "78901",
    )


    @BeforeEach
    fun setUp() {
        // no-op
    }

    @AfterEach
    fun tearDown() {
        runBlocking {
            // Delete the test database file
            userRepo.deleteDatabase()
        }
    }

    @Test
    fun findAllUsers() {
        runBlocking {

            // • ARRANGE
            userRepo.addUser(user)

            // • ACT
            val result = userRepo.findAllUsers()

            // • ASSERT
            assertTrue(result.isNotEmpty(), "Find all entities test failed, result is empty.")
            assertTrue(result.contains(user), "Find all entities test failed, result does not contain key.")
        }
    }

    @Test
    fun findUserById() {
        runBlocking {

            // • ARRANGE
            userRepo.addUser(user)

            // • ACT
            val result = userRepo.findUserById(user.id())

            // • ASSERT
            assertTrue(result != null, "Find entity by id test failed, result is null.")
            assertEquals(user, result, "Find entity by id test failed, result does not match.")
        }
    }

    @Test
    fun findUserByEmail() {
        runBlocking {

            // • ARRANGE
            userRepo.addUser(user)

            // • ACT
            val result = userRepo.findUserByEmail(user.email)

            // • ASSERT
            assertTrue(result != null, "Find entity by email test failed, result is null.")
            assertEquals(user, result, "Find entity by email test failed, result does not match.")
        }
    }

    @Test
    fun findUserByAuthToken() {
        runBlocking {

            // • ARRANGE
            userRepo.addUser(user)

            // • ACT
            val result = userRepo.findUserByAuthToken(user.authToken)

            // • ASSERT
            assertTrue(result != null, "Find entity by authToken test failed, result is null.")
            assertEquals(user, result, "Find entity by authToken test failed, result does not match.")
        }
    }

    @Test
    fun findUserByAuthJwtToken() {
        runBlocking {

            // • ARRANGE
            userRepo.addUser(user)

            // • ACT
            val result = userRepo.findUserByAuthJwtToken(user.authJwtToken)

            // • ASSERT
            assertTrue(result != null, "Find entity by authJwtToken test failed, result is null.")
            assertEquals(user, result, "Find entity by authJwtToken test failed, result does not match.")
        }
    }

    @Test
    fun findUserByPasswordResetToken() {
        runBlocking {

            // • ARRANGE
            userRepo.addUser(user)

            // • ACT
            val result = userRepo.findUserByPasswordResetToken(user.passwordResetToken!!)

            // • ASSERT
            assertTrue(result != null, "Find entity by passwordResetToken test failed, result is null.")
            assertEquals(user, result, "Find entity by passwordResetToken test failed, result does not match.")
        }
    }

    @Test
    fun findUserByPasswordResetJwtToken() {
        runBlocking {

            // • ARRANGE
            userRepo.addUser(user)

            // • ACT
            val result = userRepo.findUserByPasswordResetJwtToken(user.passwordResetJwtToken!!)

            // • ASSERT
            assertTrue(result != null, "Find entity by passwordResetJwtToken test failed, result is null.")
            assertEquals(user, result, "Find entity by passwordResetJwtToken test failed, result does not match.")
        }
    }

    @Test
    fun addUser() {
        runBlocking {

            // • ARRANGE
            userRepo.addUser(user)

            // • ACT
            val result = userRepo.findUserById(user.id())

            // • ASSERT
            assertTrue(result != null, "Add entity test failed, result is null.")
            assertEquals(user, result, "Add entity test failed, result does not match.")
        }
    }

    @Test
    fun updateUser() {
        runBlocking {

            // • ARRANGE
            userRepo.addUser(user)

            // • ACT
            val result = userRepo.updateUser(user)

            // • ASSERT
            assertEquals(user, result, "Update entity test failed, result does not match.")
        }
    }

    @Test
    fun deleteUser() {
        runBlocking {

            // • ARRANGE
            userRepo.addUser(user)

            // • ACT
            val result = userRepo.deleteUser(user)

            // • ASSERT
            val deletedUser = userRepo.findUserById(user.id())
            assertTrue(deletedUser == null, "Delete entity test failed, result is not null.")
        }
    }

    @Test
    fun deleteUserById() {
        runBlocking {

            // • ARRANGE
            userRepo.addUser(user)

            // • ACT
            val result = userRepo.deleteUserById(user.id())

            // • ASSERT
            val deletedUser = userRepo.findUserById(user.id())
            assertTrue(deletedUser == null, "Delete entity by id test failed, result is not null.")
        }
    }

    @Test
    fun deleteUserByEmail() {
        runBlocking {

            // • ARRANGE
            userRepo.addUser(user)

            // • ACT
            val result = userRepo.deleteUserByEmail(user.email)

            // • ASSERT
            val deletedUser = userRepo.findUserById(user.id())
            assertTrue(deletedUser == null, "Delete entity by email test failed, result is not null.")
        }
    }

    @Test
    fun deleteUserByAuthToken() {
        runBlocking {

            // • ARRANGE
            userRepo.addUser(user)

            // • ACT
            val result = userRepo.deleteUserByAuthToken(user.authToken)

            // • ASSERT
            val deletedUser = userRepo.findUserById(user.id())
            assertTrue(deletedUser == null, "Delete entity by authToken test failed, result is not null.")
        }
    }

    @Test
    fun deleteUserByAuthJwtToken() {
        runBlocking {

            // • ARRANGE
            userRepo.addUser(user)

            // • ACT
            val result = userRepo.deleteUserByAuthJwtToken(user.authJwtToken)

            // • ASSERT
            val deletedUser = userRepo.findUserById(user.id())
            assertTrue(deletedUser == null, "Delete entity by authJwtToken test failed, result is not null.")
        }
    }
}