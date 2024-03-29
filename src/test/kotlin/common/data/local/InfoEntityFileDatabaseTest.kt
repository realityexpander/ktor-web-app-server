package common.data.local

import common.uuid2.UUID2.Companion.fromUUIDStrToUUID2
import domain.book.Book
import domain.book.data.network.BookInfoDTO
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import testFakes.common.data.local.FakeFileDatabase
import java.util.*

class InfoEntityFileDatabaseTest {

    private val tempName = UUID.randomUUID().toString()
    private val fakeFileDatabase: FakeFileDatabase<Book, BookInfoDTO> =
        FakeFileDatabase(
            databaseFilename = "test-$tempName-apiDB.json",
            entityKSerializer = BookInfoDTO.serializer()
        )

    private val bookInfoDTO = BookInfoDTO(
        id = "00000000-0000-0000-0000-000000000100".fromUUIDStrToUUID2<Book>(),
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
            fakeFileDatabase.deleteDatabase()
        }
    }

    @Test
    fun findAllEntities() {
        runBlocking {

            // • ARRANGE
            fakeFileDatabase.addEntity(bookInfoDTO)

            // • ACT
            val result = fakeFileDatabase.findAllEntities()

            // • ASSERT
            assertTrue(result.size == 1, "Find all entities test failed, size is wrong.")
            assertTrue(result[0].id() == bookInfoDTO.id, "Find all entities test failed, id is wrong.")
        }
    }

    @Test
    fun `findEntityById for existing id is Success`() {
        runBlocking {

            // • ARRANGE
            fakeFileDatabase.addEntity(bookInfoDTO)

            // • ACT
            val result = fakeFileDatabase.findEntityById(bookInfoDTO.id)

            // • ASSERT
            assertTrue(result != null, "Find entity by id test failed, result is null.")
            assertTrue(result!!.id() == bookInfoDTO.id, "Find entity by id test failed, id is wrong.")
        }
    }

    @Test
    fun `findEntityById for non-existing id is Failure`() {
        runBlocking {

            // • ARRANGE
            // no-op

            // • ACT
            val result = fakeFileDatabase.findEntityById(bookInfoDTO.id)

            // • ASSERT
            assertFalse(result != null, "Find entity by id test succeeded but should have failed, result is not null.")
            assertFalse(result?.id() == bookInfoDTO.id, "Find entity by id test succeeded but should have failed.")
        }
    }

    @Test
    fun addEntity() {
        runBlocking {

            // • ARRANGE
            val initialSize = fakeFileDatabase.findAllEntities().size

            // • ACT
            fakeFileDatabase.addEntity(bookInfoDTO)

            // • ASSERT
            val result = fakeFileDatabase.findAllEntities()
            assertTrue(result.size == initialSize + 1, "Add entity test failed, size is wrong.")
            assertTrue(result[0].id() == bookInfoDTO.id, "Add entity test failed, id is wrong.")
        }
    }

    @Test
    fun updateEntity() {
        runBlocking {

            // • ARRANGE
            fakeFileDatabase.addEntity(bookInfoDTO)
            val updatedBookInfoDTO = BookInfoDTO(
                id = bookInfoDTO.id,
                title = "The UPDATED TITLE Hobbit",
                author = "J.R.R. Tolkien",
                description = "UPDATED DESCRIPTION"
            )

            // • ACT
            fakeFileDatabase.updateEntity(updatedBookInfoDTO)

            // • ASSERT
            val result = fakeFileDatabase.findAllEntities()
            assertTrue(result.size == 1, "Update entity test failed, size is wrong.")
            assertTrue(result[0].id() == bookInfoDTO.id, "Update entity test failed, id is wrong.")
            assertTrue(result[0].title == updatedBookInfoDTO.title, "Update entity test failed, title is wrong.")
            assertTrue(result[0].author == updatedBookInfoDTO.author, "Update entity test failed, author is wrong.")
            assertTrue(result[0].description == updatedBookInfoDTO.description, "Update entity test failed, description is wrong.")
        }
    }

    @Test
    fun deleteEntity() {
        runBlocking {

            // • ARRANGE
            fakeFileDatabase.addEntity(bookInfoDTO)

            // • ACT
            fakeFileDatabase.deleteEntity(bookInfoDTO)

            // • ASSERT
            val result = fakeFileDatabase.findAllEntities()
            assertTrue(result.isEmpty(), "Delete entity test failed, size is wrong.")
        }
    }

    @Test
    fun `Subclassed method updateLookupTables() is called after every CUD change is Success`() {
        runBlocking {

            // • ARRANGE + ACT
            fakeFileDatabase.addEntity(bookInfoDTO)
            // • ASSERT
            assertTrue(fakeFileDatabase.calledUpdateLookupTables,
                "Update lookup tables test failed, never happened for addEntity")

            // • ARRANGE + ACT
            fakeFileDatabase.calledUpdateLookupTables = false  // reset test
            fakeFileDatabase.updateEntity(bookInfoDTO)
            // • ASSERT
            assertTrue(fakeFileDatabase.calledUpdateLookupTables,
                "Update lookup tables test failed, never happened for updateEntity")

            // • ARRANGE + ACT
            fakeFileDatabase.calledUpdateLookupTables = false  // reset test
            fakeFileDatabase.deleteEntity(bookInfoDTO)
            // • ASSERT
            assertTrue(fakeFileDatabase.calledUpdateLookupTables,
                "Update lookup tables test failed, never happened for deleteEntity")

            // • ARRANGE + ACT
            fakeFileDatabase.calledUpdateLookupTables = false  // reset test
            fakeFileDatabase.deleteDatabase()
            // • ASSERT
            assertTrue(fakeFileDatabase.calledUpdateLookupTables,
                "Update lookup tables test failed, never happened for deleteDatabaseFile")
        }
    }

    @Test
    fun deleteDatabaseFile() {
        runBlocking {

            // • ARRANGE
            fakeFileDatabase.addEntity(bookInfoDTO)

            // • ACT
            fakeFileDatabase.deleteDatabase()

            // • ASSERT
            val result = fakeFileDatabase.findAllEntities()
            assertTrue(result.isEmpty(), "Delete database file test failed, should be empty.")
        }
    }
}
