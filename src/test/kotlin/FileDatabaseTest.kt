import com.realityexpander.common.data.local.FileDatabase
import common.uuid2.UUID2
import domain.book.Book
import domain.book.data.network.DTOBookInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import testFakes.FakeFileDatabase
import testUtils.waitForJobToComplete
import java.util.*

class FileDatabaseTest {

    private val tempName = UUID.randomUUID().toString()
    private val fakeFileDatabase: FakeFileDatabase<UUID2<Book>, DTOBookInfo> =
        FakeFileDatabase(
            databaseFilename = "test-$tempName-apiDB.json",
            entityKSerializer = DTOBookInfo.serializer()
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
        // Delete the test database file
        fakeFileDatabase.deleteDatabaseFile()
    }

    @Test
    fun findAllEntities() {
        val job = CoroutineScope(Dispatchers.IO).launch {

            // • ARRANGE
            fakeFileDatabase.addEntity(dtoBookInfo)

            // • ACT
            val result = fakeFileDatabase.findAllEntities()

            // • ASSERT
            assertTrue(result.size == 1, "Find all entities test failed, size is wrong.")
            assertTrue(result[0].id() == dtoBookInfo.id, "Find all entities test failed, id is wrong.")
        }
        job.waitForJobToComplete()
    }

    @Test
    fun findEntityById() {
        val job = CoroutineScope(Dispatchers.IO).launch {

            // • ARRANGE
            fakeFileDatabase.addEntity(dtoBookInfo)

            // • ACT
            val result = fakeFileDatabase.findEntityById(dtoBookInfo.id)

            // • ASSERT
            assertTrue(result != null, "Find entity by id test failed, result is null.")
            assertTrue(result!!.id() == dtoBookInfo.id, "Find entity by id test failed, id is wrong.")
        }
        job.waitForJobToComplete()
    }

    @Test
    fun addEntity() {
        val job = CoroutineScope(Dispatchers.IO).launch {

            // • ARRANGE
            val initialSize = fakeFileDatabase.findAllEntities().size

            // • ACT
            fakeFileDatabase.addEntity(dtoBookInfo)

            // • ASSERT
            val result = fakeFileDatabase.findAllEntities()
            assertTrue(result.size == initialSize + 1, "Add entity test failed, size is wrong.")
            assertTrue(result[0].id() == dtoBookInfo.id, "Add entity test failed, id is wrong.")
        }
        job.waitForJobToComplete()
    }

    @Test
    fun updateEntity() {
        val job = CoroutineScope(Dispatchers.IO).launch {

            // • ARRANGE
            fakeFileDatabase.addEntity(dtoBookInfo)
            val updatedDtoBookInfo = DTOBookInfo(
                id = dtoBookInfo.id,
                title = "The UPDATED TITLE Hobbit",
                author = "J.R.R. Tolkien",
                description = "UPDATED DESCRIPTION"
            )

            // • ACT
            fakeFileDatabase.updateEntity(updatedDtoBookInfo)

            // • ASSERT
            val result = fakeFileDatabase.findAllEntities()
            assertTrue(result.size == 1, "Update entity test failed, size is wrong.")
            assertTrue(result[0].id() == dtoBookInfo.id, "Update entity test failed, id is wrong.")
            assertTrue(result[0].title == updatedDtoBookInfo.title, "Update entity test failed, title is wrong.")
            assertTrue(result[0].author == updatedDtoBookInfo.author, "Update entity test failed, author is wrong.")
            assertTrue(result[0].description == updatedDtoBookInfo.description, "Update entity test failed, description is wrong.")
        }
        job.waitForJobToComplete()
    }

    @Test
    fun deleteEntity() {
        val job = CoroutineScope(Dispatchers.IO).launch {

            // • ARRANGE
            fakeFileDatabase.addEntity(dtoBookInfo)

            // • ACT
            fakeFileDatabase.deleteEntity(dtoBookInfo)

            // • ASSERT
            val result = fakeFileDatabase.findAllEntities()
            assertTrue(result.isEmpty(), "Delete entity test failed, size is wrong.")
        }
        job.waitForJobToComplete()
    }

    @Test
    fun deleteEntityById() {
        val job = CoroutineScope(Dispatchers.IO).launch {

            // • ARRANGE
            fakeFileDatabase.addEntity(dtoBookInfo)

            // • ACT
            fakeFileDatabase.deleteEntityById(dtoBookInfo.id)

            // • ASSERT
            val result = fakeFileDatabase.findAllEntities()
            assertTrue(result.isEmpty(), "Delete entity test failed, size is wrong.")
        }
        job.waitForJobToComplete()
    }

    @Test
    fun updateLookupTables() {
        val job = CoroutineScope(Dispatchers.IO).launch {

            // • ARRANGE + ACT
            fakeFileDatabase.addEntity(dtoBookInfo)

            // • ASSERT
            assertTrue(fakeFileDatabase.calledUpdateLookupTables, "Update lookup tables test failed, calledUpdateLookupTables is wrong.")
        }
        job.waitForJobToComplete()
    }

    @Test
    fun deleteDatabaseFile() {
        val job = CoroutineScope(Dispatchers.IO).launch {

            // • ARRANGE
            fakeFileDatabase.addEntity(dtoBookInfo)

            // • ACT
            fakeFileDatabase.deleteDatabaseFile()

            // • ASSERT
            val result = fakeFileDatabase.findAllEntities()
            assertTrue(result.isEmpty(), "Delete database file test failed, size is wrong.")
        }
        job.waitForJobToComplete()
    }
}
