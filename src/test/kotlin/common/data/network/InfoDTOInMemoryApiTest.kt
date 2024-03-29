package common.data.network

import common.uuid2.UUID2.Companion.fromUUIDStrToUUID2
import domain.book.Book
import domain.book.data.network.BookInfoDTO
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.util.*

class InfoDTOInMemoryApiTest {

    private val tempName = UUID.randomUUID().toString()
    private val testApi = InfoDTOInMemoryApi<Book, BookInfoDTO>()

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

    @Test
    fun findAllUUID2ToDtoInfoMap() {
        runBlocking {

            // • ARRANGE
            testApi.addDTOInfo(bookInfoDTO)

            // • ACT
            val result = testApi.findAllUUID2ToDTOInfoMap()

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
            testApi.addDTOInfo(bookInfoDTO)

            // • ACT
            val result = testApi.fetchDTOInfo(bookInfoDTO.id())

            // • ASSERT
            assertTrue(result.isSuccess, "Fetch entity test failed, result is not success.")
            assertTrue(result.getOrNull() != null, "Fetch entity test failed, result is null.")
            assertEquals(bookInfoDTO, result.getOrNull(), "Fetch entity test failed, result does not match.")
        }
    }

    @Test
    fun updateDtoInfo() {
        runBlocking {

            // • ARRANGE
            val updatedBookInfoDTO = BookInfoDTO(
                id = "00000000-0000-0000-0000-000000000100".fromUUIDStrToUUID2(),
                title = "The UPDATED Hobbit",
                author = "J.R.R. Tolkien",
                description = "UPDATED DESCRIPTION"
            )
            testApi.addDTOInfo(bookInfoDTO)

            // • ACT
            val result = testApi.updateDTOInfo(updatedBookInfoDTO)

            // • ASSERT
            assertTrue(result.isSuccess, "Update entity test failed, result is not success.")
            assertTrue(result.getOrNull() != null, "Update entity test failed, result is null.")
            assertEquals(updatedBookInfoDTO, result.getOrNull(), "Update entity test failed, result does not match.")
        }
    }

    @Test
    fun addDtoInfo() {
        runBlocking {

            // • ARRANGE
            testApi.addDTOInfo(bookInfoDTO)

            // • ACT
            val result = testApi.addDTOInfo(bookInfoDTO)

            // • ASSERT
            assertTrue(result.isFailure, "Add entity test failed, result is not failure.")
            assertTrue(result.exceptionOrNull() != null, "Add entity test failed, result is null.")
            assertTrue(result.exceptionOrNull()!!.message!!.contains("already exists"), "Add entity test failed, result does not match.")
        }
    }

    @Test
    fun upsertDtoInfo() {
        runBlocking {

            // • ARRANGE
            val updatedBookInfoDTO = BookInfoDTO(
                id = "00000000-0000-0000-0000-000000000100".fromUUIDStrToUUID2(),
                title = "The UPDATED Hobbit",
                author = "J.R.R. Tolkien",
                description = "UPDATED DESCRIPTION"
            )
            testApi.addDTOInfo(bookInfoDTO)

            // • ACT
            val result = testApi.upsertDTOInfo(updatedBookInfoDTO)

            // • ASSERT
            assertTrue(result.isSuccess, "Upsert entity test failed, result is not success.")
            assertTrue(result.getOrNull() != null, "Upsert entity test failed, result is null.")
            assertEquals(updatedBookInfoDTO, result.getOrNull(), "Upsert entity test failed, result does not match.")
        }
    }

    @Test
    fun deleteDtoInfo() {
        runBlocking {

            // • ARRANGE
            testApi.addDTOInfo(bookInfoDTO)

            // • ACT
            val result = testApi.deleteDTOInfo(bookInfoDTO)

            // • ASSERT
            assertTrue(result.isSuccess, "Delete entity test failed, result is not success.")
            assertEquals(Unit, result.getOrNull(), "Delete entity test failed, result does not match.")
        }
    }
}