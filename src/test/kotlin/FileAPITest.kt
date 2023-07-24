import common.data.network.FileAPI
import common.uuid2.UUID2
import common.uuid2.UUID2.Companion.fromUUIDString
import domain.book.Book
import domain.book.data.network.DTOBookInfo
import kotlinx.coroutines.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.util.*

class FileAPITest {

    private val tempName = UUID.randomUUID().toString()
    private val testApi = FileAPI<Book, DTOBookInfo>(
        apiDatabaseFilename = "test-$tempName-apiDB.json",
        serializer = DTOBookInfo.serializer()
    )

    private val dtoBookInfo = DTOBookInfo(
        id = "00000000-0000-0000-0000-000000000100".fromUUIDString(),
        title = "The Hobbit",
        author = "J.R.R. Tolkien",
        description = "The Hobbit, or There and Back Again is a children's fantasy novel by English author J. R. R. Tolkien. It was published on 21 September 1937 to wide critical acclaim, being nominated for the Carnegie Medal and awarded a prize from the New York Herald Tribune for best juvenile fiction. The book remains popular and is recognized as a classic in children's literature."
    )


    @BeforeEach
    fun setUp() {
        // no-op
    }

    @AfterEach
    fun tearDown() {
        runBlocking {
            // Delete the test database file
            testApi.deleteDatabaseFile()
        }
    }

    @Test
    fun findAllUUID2ToDtoInfoMap() {
        runBlocking {

            // • ARRANGE
            testApi.addDtoInfo(dtoBookInfo)

            // • ACT
            val result = testApi.findAllUUID2ToDtoInfoMap()

            // • ASSERT
            assertTrue(result.isSuccess, "Find all entities test failed, result is not success.")
            assertTrue(result.getOrNull() != null, "Find all entities test failed, result is null.")
            assertTrue(result.getOrNull()!!.isNotEmpty(), "Find all entities test failed, result is empty.")
            assertTrue(result.getOrNull()!!.containsKey(dtoBookInfo.id()), "Find all entities test failed, result does not contain key.")
        }
    }

    @Test
    fun fetchDtoInfo() {
        runBlocking {

            // • ARRANGE
            testApi.addDtoInfo(dtoBookInfo)

            // • ACT
            val result = testApi.fetchDtoInfo(dtoBookInfo.id)


            // • ASSERT
            assertTrue(result.isSuccess, "Find all entities test failed, result is not success.")
            assertTrue(result.getOrNull() != null, "Find all entities test failed, result is null.")
            assertTrue(result.getOrNull()!!.id == dtoBookInfo.id, "Find all entities test failed, result does not contain key.")
        }
    }

    @Test
    fun addDtoInfo() {
        runBlocking {

            // • ARRANGE
            testApi.addDtoInfo(dtoBookInfo)

            // • ACT
            val result = testApi.toDatabaseCopy().size

            // • ASSERT
            assertTrue(result == 1, "Add entity test failed, size is wrong.")
        }
    }

    @Test
    fun updateDtoInfo() {

        // • ARRANGE
        val updatedDTOBookInfo = DTOBookInfo(
            id = "00000000-0000-0000-0000-000000000100".fromUUIDString(),
            title = "The UPDATED Hobbit",
            author = "J.R.R. Tolkien",
            description = "UPDATED DESCRIPTION"
        )

        runBlocking {
            testApi.addDtoInfo(dtoBookInfo)

            // • ACT
            val result = testApi.updateDtoInfo(updatedDTOBookInfo)

            // • ASSERT
            assertTrue(result.isSuccess, "Update entity test failed, result is not success.")
            val updatedResult = testApi.findEntityById(updatedDTOBookInfo.id)
            assertNotNull(updatedResult, "Update entity test failed, result is null.")
            assertTrue(updatedResult?.title == updatedDTOBookInfo.title, "Update entity test failed, title is wrong.")
            assertTrue(updatedResult?.description == updatedDTOBookInfo.description, "Update entity test failed, description is wrong.")
        }
    }

    @Test
    fun deleteDtoInfo() {
        runBlocking {

            // • ARRANGE
            testApi.addDtoInfo(dtoBookInfo)

            // • ACT
            testApi.deleteDtoInfo(dtoBookInfo)

            // • ASSERT
            val result = testApi.toDatabaseCopy().size
            assertTrue(result == 0, "Delete entity test failed, size is wrong.")
        }
    }

}