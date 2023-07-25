package common.data.network

import common.uuid2.UUID2.Companion.fromUUIDStrToUUID2
import domain.book.Book
import domain.book.data.network.BookInfoDTO
import kotlinx.coroutines.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.util.*

class FileApiTest {

    private val tempName = UUID.randomUUID().toString()
    private val testApi = FileApi<Book, BookInfoDTO>(
        apiDatabaseFilename = "test-$tempName-apiDB.json",
        serializer = BookInfoDTO.serializer()
    )

    private val bookInfoDTO = BookInfoDTO(
        id = "00000000-0000-0000-0000-000000000100".fromUUIDStrToUUID2(),
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
            testApi.addDtoInfo(bookInfoDTO)

            // • ACT
            val result = testApi.findAllUUID2ToDtoInfoMap()

            // • ASSERT
            assertTrue(result.isSuccess, "Find all entities test failed, result is not success.")
            assertTrue(result.getOrNull() != null, "Find all entities test failed, result is null.")
            assertTrue(result.getOrNull()!!.isNotEmpty(), "Find all entities test failed, result is empty.")
            assertTrue(result.getOrNull()!!.containsKey(bookInfoDTO.id()), "Find all entities test failed, result does not contain key.")
        }
    }

    @Test
    fun fetchDtoInfo() {
        runBlocking {

            // • ARRANGE
            testApi.addDtoInfo(bookInfoDTO)

            // • ACT
            val result = testApi.fetchDtoInfo(bookInfoDTO.id)


            // • ASSERT
            assertTrue(result.isSuccess, "Find all entities test failed, result is not success.")
            assertTrue(result.getOrNull() != null, "Find all entities test failed, result is null.")
            assertTrue(result.getOrNull()!!.id == bookInfoDTO.id, "Find all entities test failed, result does not contain key.")
        }
    }

    @Test
    fun addDtoInfo() {
        runBlocking {

            // • ARRANGE
            testApi.addDtoInfo(bookInfoDTO)

            // • ACT
            val result = testApi.toDatabaseCopy().size

            // • ASSERT
            assertTrue(result == 1, "Add entity test failed, size is wrong.")
        }
    }

    @Test
    fun updateDtoInfo() {

        // • ARRANGE
        val updatedBookInfoDTO = BookInfoDTO(
            id = "00000000-0000-0000-0000-000000000100".fromUUIDStrToUUID2(),
            title = "The UPDATED Hobbit",
            author = "J.R.R. Tolkien",
            description = "UPDATED DESCRIPTION"
        )

        runBlocking {
            testApi.addDtoInfo(bookInfoDTO)

            // • ACT
            val result = testApi.updateDtoInfo(updatedBookInfoDTO)

            // • ASSERT
            assertTrue(result.isSuccess, "Update entity test failed, result is not success.")
            val updatedResult = testApi.findEntityById(updatedBookInfoDTO.id)
            assertNotNull(updatedResult, "Update entity test failed, result is null.")
            assertTrue(updatedResult?.title == updatedBookInfoDTO.title, "Update entity test failed, title is wrong.")
            assertTrue(updatedResult?.description == updatedBookInfoDTO.description, "Update entity test failed, description is wrong.")
        }
    }

    @Test
    fun deleteDtoInfo() {
        runBlocking {

            // • ARRANGE
            testApi.addDtoInfo(bookInfoDTO)

            // • ACT
            testApi.deleteDtoInfo(bookInfoDTO)

            // • ASSERT
            val result = testApi.toDatabaseCopy().size
            assertTrue(result == 0, "Delete entity test failed, size is wrong.")
        }
    }

}