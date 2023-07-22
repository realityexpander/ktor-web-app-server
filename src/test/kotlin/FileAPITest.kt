import common.data.network.FileAPI
import common.uuid2.UUID2
import domain.book.Book
import domain.book.data.network.DTOBookInfo
import kotlinx.coroutines.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import testUtils.waitForJobToComplete
import java.util.*

class FileAPITest {

    private val tempName = UUID.randomUUID().toString()
    private val testApi = FileAPI<Book, DTOBookInfo>(
        apiDBFilename = "test-$tempName-apiDB.json",
        serializer = DTOBookInfo.serializer()
    )

    private val dtoBookInfo = DTOBookInfo(
        id = UUID2.fromUUIDString("00000000-0000-0000-0000-000000000100"),
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
    fun findAllEntities() {
        runBlocking {

            // • ARRANGE
            testApi.addDtoInfo(dtoBookInfo)

            // • ACT
            val result = testApi.findAllEntities()

            // • ASSERT
            assertTrue(result.size == 1, "Find all entities test failed, size is wrong.")
            assertTrue(result[0].id() == dtoBookInfo.id, "Find all entities test failed, id is wrong.")
        }
    }

    @Test
    fun findEntityById() {
        runBlocking {

            // • ARRANGE
            testApi.addDtoInfo(dtoBookInfo)

            // • ACT
            val result = testApi.findEntityById(dtoBookInfo.id)


            // • ASSERT
            assertNotNull(result, "Find all entities test failed, result is null.")
            assertTrue(result?.id() == dtoBookInfo.id(), "Find all entities test failed, id is wrong.")
            assertTrue(result?.title == dtoBookInfo.title, "Find all entities test failed, id is wrong.")
        }
    }

    @Test
    fun addEntity() {
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
    fun updateEntity() {

        // • ARRANGE
        val updatedDTOBookInfo = DTOBookInfo(
            id = UUID2.fromUUIDString("00000000-0000-0000-0000-000000000100"),
            title = "The UPDATED Hobbit",
            author = "J.R.R. Tolkien",
            description = "UPDATED DESCRIPTION"
        )

        runBlocking {
            testApi.addDtoInfo(dtoBookInfo)

            // • ACT
            val result = testApi.updateDtoInfo(updatedDTOBookInfo)

            // • ASSERT
            val updatedResult = testApi.findEntityById(updatedDTOBookInfo.id)
            assertNotNull(updatedResult, "Update entity test failed, result is null.")
            assertTrue(updatedResult?.title == updatedDTOBookInfo.title, "Update entity test failed, title is wrong.")
            assertTrue(updatedResult?.description == updatedDTOBookInfo.description, "Update entity test failed, description is wrong.")
        }
    }

    @Test
    fun deleteEntity() {
        runBlocking {

            // • ARRANGE
            testApi.addDtoInfo(dtoBookInfo)

            // • ACT
            testApi.deleteEntity(dtoBookInfo)

            // • ASSERT
            val result = testApi.toDatabaseCopy().size
            assertTrue(result == 0, "Delete entity test failed, size is wrong.")
        }
    }

    @Test
    fun deleteEntityById() {
        runBlocking {

            // • ARRANGE
            testApi.addDtoInfo(dtoBookInfo)

            // • ACT
            testApi.deleteEntityById(dtoBookInfo.id)

            // • ASSERT
            val result = testApi.toDatabaseCopy().size
            assertTrue(result == 0, "Delete entity test failed, size is wrong.")
        }
    }

}