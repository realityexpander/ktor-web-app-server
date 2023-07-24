import common.uuid2.UUID2
import domain.Context
import domain.account.Account
import domain.account.data.AccountInfo
import domain.book.data.BookInfo
import domain.book.data.network.DTOBookInfo
import domain.library.Library
import domain.user.User
import domain.book.Book
import domain.book.data.local.EntityBookInfo
import domain.common.Role
import domain.library.data.LibraryInfo
import domain.user.data.UserInfo
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import testUtils.TestLog
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.fail
import java.time.Instant

/**
 * LibraryAppTest
 *
 * Integration tests for the LibraryApp.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin Conversion
 */

class LibraryAppTest {
    private val context: Context = setupDefaultTestContext()
    private val testUtils: TestingUtils = TestingUtils(context)

    companion object {
        private const val shouldDisplayAllDebugLogs = false // Set to `true` to see all debug logs

        fun setupDefaultTestContext(): Context {
            val testLog = TestLog(!shouldDisplayAllDebugLogs) // false = print all logs to console, including info/debug
            val prodContext = Context.setupProductionInstance(testLog)

            // Modify the Production context into a Test context.
            return Context(
                prodContext.bookInfoRepo,
                prodContext.userInfoRepo,
                prodContext.libraryInfoRepo,
                prodContext.accountInfoRepo,
                prodContext.gson,
                testLog  // <--- Using the test logger
            )
        }
    }

    @BeforeEach
    fun setUp() {
        // no-op
    }

    internal class TestRoles(
        val account1: Account,
        val account2: Account,
        val user1: User,
        val library1: Library,
        val book1100: Book,
        val book1200: Book
    )

    private fun setupDefaultRolesAndScenario(
        context: Context,
        testUtils: TestingUtils
    ): TestRoles {

        ////////////////////////////////////////
        // Setup DB & API simulated resources //
        ////////////////////////////////////////

        return runBlocking {

            // • Put some fake BookInfo into the DB & API for BookInfo's
            testUtils.populateFakeBookInfoInBookRepoDBandAPI()

            // • Create & populate a Library in the Library Repo
            val libraryInfo = testUtils.createFakeLibraryInfoInLibraryInfoRepo(1)
            assertTrue(libraryInfo.isSuccess, "Create Library FAILURE --> $libraryInfo")
            val library1InfoId: UUID2<Library> = libraryInfo.getOrThrow().id()
            context.log.d(this,
                "Library Created --> id: " + libraryInfo.getOrThrow().id()
                        + ", name: " + libraryInfo.getOrThrow().name
            )

            // Populate the library with 10 books
            testUtils.populateLibraryWithFakeBooks(library1InfoId, 10)

            /////////////////////////////////
            // • Create Accounts for Users //
            /////////////////////////////////

            val accountInfo1Result = testUtils.createFakeAccountInfoInAccountRepo(1)
            val accountInfo2Result = testUtils.createFakeAccountInfoInAccountRepo(2)
            assertNotNull(accountInfo1Result)
            assertNotNull(accountInfo2Result)
            assertTrue(accountInfo1Result.isSuccess)
            assertTrue(accountInfo2Result.isSuccess)
            val accountInfo1: AccountInfo = accountInfo1Result.getOrThrow()
            val accountInfo2: AccountInfo = accountInfo2Result.getOrThrow()

            // Create & populate User1 in the User Repo for the Context
            val user1InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(1)
            assertNotNull(user1InfoResult)
            assertTrue(user1InfoResult.isSuccess)
            val user1Info: UserInfo = user1InfoResult.getOrThrow()

            ///////////////////////////
            // Create Default Roles  //
            ///////////////////////////

            val account1 = Account(accountInfo1, context)
            assertNotNull(account1)
            val library1 = Library(library1InfoId, context)
            assertNotNull(library1)

            // Create the Test Roles
            val testRoles = TestRoles(
                account1,
                Account(accountInfo2, context),
                User(user1Info, account1, context),
                library1,
                Book(UUID2.createFakeUUID2(1100, Book::class.java), null, context),  // create ORPHANED book
                Book(UUID2.createFakeUUID2(1200, Book::class.java), library1, context)
            )
            assertNotNull(testRoles)

            // print User1
            context.log.d(this, "User --> " + testRoles.user1.id + ", " + testRoles.user1.fetchInfo()?.toPrettyJson(context))

            return@runBlocking testRoles
        }
    }

    @Test
    fun `Update BookInfo is Success`() {
        runBlocking {

            // • ARRANGE
            // Create fake book info in the DB & API
            val book1100Id = 1100
            testUtils.addFakeBookInfoToBookInfoRepo(book1100Id)

            // Create a book object (it only has an id)
            val book = Book(UUID2.createFakeUUID2(book1100Id, Book::class.java), null, context)
            context.log.d(this, book.fetchInfoResult().toString())
            val expectedUpdatedTitle = "The Updated Title"
            val expectedUpdatedAuthor = "The Updated Author"
            val expectedUpdatedDescription = "The Updated Description"
            val expectedUpdatedCreationTimeMillis = Instant.parse("2023-01-01T00:00:00.00Z").toEpochMilli()
            val expectedUpdatedLastModifiedTimeMillis = Instant.parse("2023-02-01T00:00:00.00Z").toEpochMilli()
            val expectedUpdatedIsDeleted = true

            // • ACT
            // Update info for a book
            val bookInfoResult: Result<BookInfo> = book.updateInfo(
                BookInfo(
                    book.id(),
                    expectedUpdatedTitle,
                    expectedUpdatedAuthor,
                    expectedUpdatedDescription,
                    expectedUpdatedCreationTimeMillis,
                    expectedUpdatedLastModifiedTimeMillis,
                    expectedUpdatedIsDeleted
                )
            )
            assertTrue(bookInfoResult.isSuccess, "Update BookInfo FAILURE --> " +
                    bookInfoResult.exceptionOrNull()?.message)

            // Get the bookInfo (null if not loaded)
            val bookInfo: BookInfo? = book.fetchInfo()
            if (bookInfo == null) {
                context.log.d(this, "Book Missing --> book id: " + book.id() + " >> " + " is null")
                fail("Book Missing --> book id: " + book.id() + " >> " + " is null")
            }

            // Check the title for updated info
            assertEquals(expectedUpdatedTitle, bookInfoResult.getOrThrow().title)
            assertEquals(expectedUpdatedAuthor, bookInfoResult.getOrThrow().author)
            assertEquals(expectedUpdatedDescription, bookInfoResult.getOrThrow().description)
        }
    }

    @Test
    fun `Fetch Non-Existing Book is Failure`() {
        runBlocking {

            // • ARRANGE
            val book99 = Book(UUID2.createFakeUUID2(99, Book::class.java), null, context)

            // • ACT
            // Try to get a book id that doesn't exist - SHOULD FAIL
            val book99InfoResult: Result<BookInfo> = book99.fetchInfoResult()

            // • ASSERT
            assertTrue(
                book99InfoResult.isFailure,
                "Book SHOULD NOT Exist, but does! --> " + book99.id()
            )
        }
    }

    @Test
    fun `Fetch Existing Book is Success`() {
        runBlocking {

            // • ARRANGE
            val roles = setupDefaultRolesAndScenario(context, testUtils)
            val book1200 = Book(UUID2.createFakeUUID2(1200, Book::class.java), roles.library1, context)

            // • ACT
            // Try to get a book id that exists - SHOULD SUCCEED
            val book1200InfoResult: Result<BookInfo> = book1200.fetchInfoResult()

            // • ASSERT
            assertTrue(
                book1200InfoResult.isSuccess,
                "Book SHOULD Exist, but doesn't! --> " + book1200.id()
            )
        }
    }

    @Test
    fun `CheckOut 2 Books to User is Success`() {
        runBlocking {

            // • ARRANGE
            val roles = setupDefaultRolesAndScenario(context, testUtils)

            // • ACT
            val book1100Result: Result<Book> = roles.library1.checkOutBookToUser(roles.book1100, roles.user1)
            val book1200Result: Result<Book> = roles.library1.checkOutBookToUser(roles.book1200, roles.user1)

            // • ASSERT
            assertTrue(book1100Result.isSuccess, "Checked out book FAILURE, bookId: " + roles.book1100.id)
            assertTrue(book1200Result.isSuccess, "Checked out book FAILURE, bookId: " + roles.book1200.id)

            roles.library1.dumpDB(context) // LEAVE for debugging
        }
    }

    @Test
    fun `Find Books checkedOut by User is Success`() {
            runBlocking {

            // • ARRANGE
            val roles = setupDefaultRolesAndScenario(context, TestingUtils(context))

            // Checkout 2 books to User
            val bookResult1: Result<Book> = roles.library1.checkOutBookToUser(roles.book1100, roles.user1)
            val bookResult2: Result<Book> = roles.library1.checkOutBookToUser(roles.book1200, roles.user1)
            assertTrue(bookResult1.isSuccess, "Checked out book FAILURE, bookId: " + roles.book1100.id)
            assertTrue(bookResult2.isSuccess, "Checked out book FAILURE, bookId: " + roles.book1200.id)

            // • ACT & ASSERT

            // Find books checked out by user
            val checkedOutBooksResult = roles.library1.findBooksCheckedOutByUser(roles.user1)
            assertTrue(checkedOutBooksResult.isSuccess,
                "findBooksCheckedOutByUser FAILURE for userId" + roles.user1.id()
            )
            val checkedOutBooks = checkedOutBooksResult.getOrThrow()

            // List Books
            context.log.d(this,
                "Checked Out Books for User [" + roles.user1.fetchInfo()?.name + ", " +
                        roles.user1.id() + "]:"
            )
            for (book in checkedOutBooks) {
                val bookInfoResult: Result<BookInfo> = book.fetchInfoResult()
                assertTrue(bookInfoResult.isSuccess, "Book Error: bookId" + book.id())
                context.log.d(this, bookInfoResult.getOrThrow().toPrettyJson(context))
            }

            val acceptedBookCount: Int =
                roles.user1.findAllAcceptedBooks().getOrThrow().size
            assertEquals(
                2,
                acceptedBookCount,
                "acceptedBookCount != 2"
            )
        }
    }

    @Test
    fun `Calculate Available BookId To Number Available List is Success`() {
        runBlocking {

            // • ARRANGE
            val roles = setupDefaultRolesAndScenario(context, TestingUtils(context))

            // Checkout 2 books
            val book1100CheckOutResult: Result<Book> = roles.library1.checkOutBookToUser(roles.book1100, roles.user1)
            if (book1100CheckOutResult.isFailure) fail("Checked out book FAILURE, bookId: " + roles.book1100.id)
            val book1200CheckOutResult: Result<Book> = roles.library1.checkOutBookToUser(roles.book1200, roles.user1)
            if (book1200CheckOutResult.isFailure) fail("Checked out book FAILURE, bookId: " + roles.book1200.id)
            val availableBookToNumAvailableResult: Result<Map<Book, Long>> =
                roles.library1.calculateAvailableBookIdToNumberAvailableList()
            assertTrue(
                availableBookToNumAvailableResult.isSuccess,
                "findBooksCheckedOutByUser FAILURE for libraryId" + roles.library1.id()
            )

            // • ACT & ASSERT
            // create objects and populate info for available books
            val availableBooks = availableBookToNumAvailableResult.getOrThrow().keys
            assertTrue(availableBooks.isNotEmpty())

            // Print out available books
            context.log.d(this, "Available Books in Library:")
            for (key in availableBooks) {
                val bookInfoResult: Result<BookInfo> = key.fetchInfoResult()
                assertTrue(bookInfoResult.isSuccess, "Book Error: bookId" + key.id())
                context.log.d(this, bookInfoResult.getOrThrow().toPrettyJson(context))
            }

            context.log.d(this, "Total Available Books (unique UUIDs): " + availableBooks.size)
            assertEquals(
                availableBooks.size,
                10,
                "availableBooks.size() != 10"
            )
        }
    }

    @Test
    fun `CheckOut and CheckIn Book to Library is Success`() {
        runBlocking {

            // • ARRANGE
            val roles = setupDefaultRolesAndScenario(context, TestingUtils(context))
            val initialBookCount: Int = roles.user1.findAllAcceptedBooks().getOrThrow().size

            // • ACT & ASSERT

            // First check-out book
            val checkoutResult: Result<UUID2<Book>> =
                roles.user1.checkOutBookFromLibrary(
                    roles.book1200,
                    roles.library1
                )
            assertTrue(checkoutResult.isSuccess, "Checked out book FAILURE --> book id:" + roles.book1200.id())

            val afterCheckOutBookCount: Int = roles.user1.findAllAcceptedBooks().getOrThrow().size
            assertEquals(
                afterCheckOutBookCount,
                initialBookCount + 1,
                "afterCheckOutBookCount != initialAcceptedBookCount+1"
            )

            // Now check in Book
            val checkInBookResult: Result<Book> = roles.library1.checkInBookFromUser(roles.book1200, roles.user1)
            assertTrue(
                checkInBookResult.isSuccess,
                "Checked out book FAILURE --> book id:" + roles.book1200.id()
            )
            val afterCheckInBookCount: Int = roles.user1.findAllAcceptedBooks().getOrThrow().size
            assertEquals(
                initialBookCount,
                afterCheckInBookCount,
                "afterCheckInBookCount != initialBookCount",
            )

            roles.library1.dumpDB(context)
        }
    }

    @Test
    fun `Update LibraryInfo using updateInfoFromJson() is Success`() {
        runBlocking {

            // • ARRANGE
            val roles = setupDefaultRolesAndScenario(context, TestingUtils(context))
            val json = library99InfoJson

            // Create the "unknown" library with just an id.
            val library99 = Library(UUID2.createFakeUUID2(99, Library::class.java), context)
            val book1500 = Book(UUID2.createFakeUUID2(1500, Book::class.java), null, context)

            // • ACT & ASSERT
            val library99InfoResult = library99.updateInfoFromJson(json)
            assertTrue(library99InfoResult.isFailure, "LibraryInfo updateInfoFromJson() of unknown library succeeded unexpectedly")

            val libraryInfo = library99.info() ?: fail("libraryInfo is null")
            val library = Library(libraryInfo, context)
            context.log.d(this, "Results of Library3 json load:" + library.toJson())

            // • ASSERT
            // check for the same number of items
            assertEquals(
                library.calculateAvailableBookIdToNumberAvailableList().getOrThrow().size,
                10,
                "Library2 should have 10 books"
            )
            // check for the same number of items
            assertEquals(
                library.calculateAvailableBookIdToNumberAvailableList().getOrThrow().size,
                roles.library1.calculateAvailableBookIdToNumberAvailableList().getOrThrow().size,
                "Library2 should have same number of books as Library1"
            )

            // check for book1500
            val book1500Result = library.info()?.isBookAvailableToCheckout(book1500)
            assertTrue(book1500Result == true, "Book1500 should be available to checkout")
        }
    }

    @Test
    fun `Update LibraryInfo using updateInfoFromJson() with wrong id in json is Failure`() {
        runBlocking {

            // • ARRANGE
            val json = library99InfoJson // note: using the wrong id to update the json
            val library1 = Library(UUID2.createFakeUUID2(1, Library::class.java), context)

            // • ACT & ASSERT
            val library1UpdateInfoResult = library1.updateInfoFromJson(json)
            assertTrue(library1UpdateInfoResult.isFailure, "LibraryInfo updateInfoFromJson() of unknown library succeeded unexpectedly")
        }
    }

    private val library99InfoJson: String =
        """
        {
          "name": "Ronald Reagan Library",
          "id": "UUID2:Role.Library@00000000-0000-0000-0000-000000000099",
          "registeredUserIdToCheckedOutBookIdsMap": {
            "UUID2:Role.User@00000000-0000-0000-0000-000000000001": [
               "UUID2:Role.Book@00000000-0000-0000-0000-000000001500"
            ]
          },
          "bookIdToNumBooksAvailableMap": {
            "UUID2:Role.Book@00000000-0000-0000-0000-000000001400": 25,
            "UUID2:Role.Book@00000000-0000-0000-0000-000000001000": 25,
            "UUID2:Role.Book@00000000-0000-0000-0000-000000001300": 25,
            "UUID2:Role.Book@00000000-0000-0000-0000-000000001200": 25,
            "UUID2:Role.Book@00000000-0000-0000-0000-000000001500": 24,
            "UUID2:Role.Book@00000000-0000-0000-0000-000000001600": 25,
            "UUID2:Role.Book@00000000-0000-0000-0000-000000001700": 25,
            "UUID2:Role.Book@00000000-0000-0000-0000-000000001800": 25,
            "UUID2:Role.Book@00000000-0000-0000-0000-000000001900": 25,
            "UUID2:Role.Book@00000000-0000-0000-0000-000000001100": 25
          }
        }
        """.trimIndent()

    @Test fun `Create Library Role from createInfoFromJson with Gson Serialization is Success`() {
        runBlocking {

            // • ARRANGE
            val expectedBook1900 = Book(UUID2.createFakeUUID2(1900, Book::class.java), null, context)

            // Create a Library Domain Object from the Info
            try {


                // • ACT
                val library99Info = Library(library99InfoJson, context).info()

                // • ASSERT
                assertNotNull(library99Info)
                val library99 = Library(library99Info!!, context)
                context.log.d(this, "Results of Library3 json load:" + library99.toJson())

                // check for the same number of items
                assertEquals(
                    library99.calculateAvailableBookIdToNumberAvailableList().getOrThrow().size,
                    10,
                    "Library2 should have 10 books"
                )

                // check the existence of a particular book
                assertTrue(
                    library99.isKnownBook(expectedBook1900),
                    "Library2 should have known Book with id=" + expectedBook1900.id()
                )
            } catch (e: Exception) {
                context.log.e(this, "Exception: " + e.message)
                fail(e.message)
            }
        }
    }

    @Test
    fun `Create Library Role from createInfoFromJson with Kotlinx Serialization is Success`() {
        runBlocking {

            // • ARRANGE
            val json = library99InfoJson
            val expectedBook1900 = Book(UUID2.createFakeUUID2(1900, Book::class.java), null, context)

            // Create a Library Domain Object from the Info
            try {

                // • ACT
                val library99Info: LibraryInfo? = Role.createInfoFromJson(
                    json,
                    LibraryInfo.serializer(),
                    context
                )

                // • ASSERT
                assertNotNull(library99Info)
                val library99 = Library(library99Info!!, context)
                context.log.d(this, "Results of Library3 json load:" + library99.toJson())

                // check for the same number of items
                assertEquals(
                    library99.calculateAvailableBookIdToNumberAvailableList().getOrThrow().size,
                    10,
                    "Library2 should have 10 books"
                )

                // check the existence of a particular book
                assertTrue(
                    library99.isKnownBook(expectedBook1900),
                    "Library2 should have known Book with id=" + expectedBook1900.id()
                )
            } catch (e: Exception) {
                context.log.e(this, "Exception: " + e.message)
                fail(e.message)
            }
        }
    }

    @Test
    fun `Create Book Role from DTOBookInfo Json is Success`() {
        val greatGatsbyDTOBookInfoJson: String =
        """
        {
          "id": "UUID2:Role.Book@00000000-0000-0000-0000-000000000010",
          "title": "The Great Gatsby",
          "author": "F. Scott Fitzgerald",
          "description": "The Great Gatsby is a 1925 novel written by American author F. Scott Fitzgerald that follows a cast of characters living in the fictional towns of West Egg and East Egg on prosperous Long Island in the summer of 1922. The story primarily concerns the young and mysterious millionaire Jay Gatsby and his quixotic passion and obsession with the beautiful former debutante Daisy Buchanan. Considered to be Fitzgerald's magnum opus, The Great Gatsby explores themes of decadence, idealism, resistance to change, social upheaval, and excess, creating a portrait of the Jazz Age or the Roaring Twenties that has been described as a cautionary tale regarding the American Dream.",
          "extraFieldToShowThisIsADTO": "Extra DTO Data from JSON payload load",
          "creationTimeMillis": 0,
          "lastModifiedTimeMillis": 0,
          "isDeleted": false
        }
        """.trimIndent()

        runBlocking {

            // • ARRANGE
            @Suppress("UnnecessaryVariable")
            val json = greatGatsbyDTOBookInfoJson
            val expectedTitle = "The Great Gatsby"
            val expectedAuthor = "F. Scott Fitzgerald"
            val expectedUUID2: UUID2<Book> = UUID2.createFakeUUID2(10, Book::class.java)
            val expectedUuid2Type: String = expectedUUID2.uuid2Type
            val expectedExtraFieldToShowThisIsADTO = "Extra DTO Data from JSON payload load"

            // • ACT & ASSERT
            try {
                val dtoBook10Info = DTOBookInfo(json, context)
                assertNotNull(dtoBook10Info)
                val book10 = Book(BookInfo(dtoBook10Info), null, context)
                assertNotNull(book10)

                context.log.d(this, "Results of load BookInfo from DTO Json: " + book10.toJson())
                assertEquals(
                    expectedTitle,
                    book10.info()?.title,
                    "Book3 should have title:$expectedTitle"
                )
                assertEquals(
                    expectedAuthor,
                    book10.info()?.author,
                    "Book3 should have author:$expectedAuthor"
                )
                assertEquals(
                    expectedUUID2,
                    book10.id(),
                    "Book3 should have id: $expectedUUID2"
                )
                assertEquals(
                    expectedUuid2Type,
                    book10.id().uuid2Type,
                    "Book3 should have UUID2 Type of:$expectedUuid2Type"
                )
                assertEquals(
                    expectedExtraFieldToShowThisIsADTO,
                    dtoBook10Info.extraFieldToShowThisIsADTO,
                    "Book3 should have extraFieldToShowThisIsADTO: $expectedExtraFieldToShowThisIsADTO"
                )
            } catch (e: Exception) {
                context.log.e(this, "Exception: " + e.message)
                fail(e.message)
            }

        }
    }

    @Test
    fun `Create Book Role from EntityBookInfo Json is Success`() {
        val greatGatsbyEntityBookInfoJson: String =
        """
        {
          "id": "UUID2:Role.Book@00000000-0000-0000-0000-000000000010",
          "title": "The Great Gatsby",
          "author": "F. Scott Fitzgerald",
          "description": "The Great Gatsby is a 1925 novel written by American author F. Scott Fitzgerald that follows a cast of characters living in the fictional towns of West Egg and East Egg on prosperous Long Island in the summer of 1922. The story primarily concerns the young and mysterious millionaire Jay Gatsby and his quixotic passion and obsession with the beautiful former debutante Daisy Buchanan. Considered to be Fitzgerald's magnum opus, The Great Gatsby explores themes of decadence, idealism, resistance to change, social upheaval, and excess, creating a portrait of the Jazz Age or the Roaring Twenties that has been described as a cautionary tale regarding the American Dream.",
          "extraFieldToShowThisIsAnEntity": "Extra Entity Data from JSON payload load",
          "creationTimeMillis": 0,
          "lastModifiedTimeMillis": 0,
          "isDeleted": false
        }
        """.trimIndent()

        runBlocking {

            // • ARRANGE
            @Suppress("UnnecessaryVariable")
            val json = greatGatsbyEntityBookInfoJson
            val expectedTitle = "The Great Gatsby"
            val expectedAuthor = "F. Scott Fitzgerald"
            val expectedUUID2: UUID2<Book> = UUID2.createFakeUUID2(10, Book::class.java)
            val expectedUuid2Type: String = expectedUUID2.uuid2Type
            val expectedExtraFieldToShowThisIsAnEntity = "Extra Entity Data from JSON payload load"

            // • ACT & ASSERT
            try {
                val entityBook10Info = EntityBookInfo(json, context)
                assertNotNull(entityBook10Info)
                val book10 = Book(BookInfo(entityBook10Info), null, context)
                assertNotNull(book10)

                context.log.d(this, "Results of load BookInfo from Entity Json: " + book10.toJson())
                assertEquals(
                    expectedTitle,
                    book10.info()?.title,
                    "Book should have title:$expectedTitle"
                )
                assertEquals(
                    expectedAuthor,
                    book10.info()?.author,
                    "Book should have author:$expectedAuthor"
                )
                assertEquals(
                    expectedUUID2,
                    book10.id(),
                    "Book should have id: $expectedUUID2"
                )
                assertEquals(
                    expectedUuid2Type,
                    book10.id().uuid2Type,
                    "Book should have UUID2 Type of:$expectedUuid2Type"
                )
                assertEquals(
                    expectedExtraFieldToShowThisIsAnEntity,
                    entityBook10Info.extraFieldToShowThisIsAnEntity,
                    "Book Entity info should have extraFieldToShowThisIsAnEntity: $expectedExtraFieldToShowThisIsAnEntity"
                )
            } catch (e: Exception) {
                context.log.e(this, "Exception: " + e.message)
                fail(e.message)
            }
        }
    }

    @Test
    fun `Create new Book then CheckOut Book to User is Success`() {
        runBlocking {

            // • ARRANGE
            val roles = setupDefaultRolesAndScenario(context, testUtils)
            val user2InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(2)
            val user2 = User(user2InfoResult.getOrThrow(), roles.account2, context)
            assertNotNull(user2)
            val book12Result: Result<BookInfo> = testUtils.addFakeBookInfoToBookInfoRepo(12)
            assertTrue(book12Result.isSuccess, "Book12 should have been added to Library1")

            // • ACT & ASSERT

            // Create a new Book by id
            val book12id: UUID2<Book> = book12Result.getOrThrow().id()
            val book12 = Book(book12id, null, context)
            assertNotNull(book12)

            // Add Book to Library
            val book12UpsertResult: Result<Book> = roles.library1.addTestBookToLibrary(book12, 1)
            assertTrue(book12UpsertResult.isSuccess, "Book12 should have been added to Library1")

            // Check out Book from the Library
            context.log.d(this, "Check out book " + book12id + " to user " + roles.user1.id())
            val checkedOutBookResult: Result<UUID2<Book>> = user2.checkOutBookFromLibrary(book12, roles.library1)
            assertTrue(checkedOutBookResult.isSuccess, "Book12 should have been checked out by user2")
        }
    }

    @Test
    fun `User Accepts Book and Gives Book to another User is Success`() {
        runBlocking {

            // • ARRANGE
            val roles = setupDefaultRolesAndScenario(context, testUtils)
            val user1InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(1)
            assertNotNull(user1InfoResult)
            assertTrue(user1InfoResult.isSuccess, "User01 should have been added to UserInfoRepo")
            val user1 = User(user1InfoResult.getOrThrow(), roles.account1, context)
            assertNotNull(user1)

            val user2InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(2)
            assertNotNull(user2InfoResult)
            assertTrue(user2InfoResult.isSuccess, "User2 should have been added to UserInfoRepo")
            val user2 = User(user2InfoResult.getOrThrow(), roles.account2, context)
            assertNotNull(user2)

            val book12InfoResult: Result<BookInfo> = testUtils.addFakeBookInfoToBookInfoRepo(12)
            assertTrue(book12InfoResult.isSuccess, "Book12 should have been added to Library1")
            val book12id: UUID2<Book> = book12InfoResult.getOrThrow().id()
            val book12 = Book(book12id, null, context)
            assertNotNull(book12)

            // • ACT & ASSERT
            val acceptBookResult: Result<ArrayList<Book>> = user2.acceptBook(book12) // no library involved.
            assertTrue(acceptBookResult.isSuccess, "User2 should have accepted Book12")

            context.log.d(this, "User (2):" + user2.id() + " Give Book:" + book12id + " to User(1):" + user1.id())
            val giveBookToUserResult: Result<ArrayList<Book>> = user2.giveBookToUser(book12, user1)
            assertTrue(giveBookToUserResult.isSuccess, "User2 should have given Book12 to User01")
        }
    }

    @Test
    fun `Give CheckedOut Book From User To User is Success`() {
        runBlocking {

            // • ARRANGE
            val roles = setupDefaultRolesAndScenario(context, testUtils)
            val user1InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(1)
            val user1 = User(user1InfoResult.getOrThrow(), roles.account1, context)

            val user2InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(2)
            val user2 = User(user2InfoResult.getOrThrow(), roles.account2, context)

            val book12InfoResult: Result<BookInfo> = testUtils.addFakeBookInfoToBookInfoRepo(12)
            val book12id: UUID2<Book> = book12InfoResult.getOrThrow().id()
            val book12 = Book(book12id, roles.library1, context)

            // • ACT & ASSERT

            // Add book12 to library1
            val book12UpsertResult: Result<Book> = roles.library1.addTestBookToLibrary(book12, 1)
            assertTrue(book12UpsertResult.isSuccess, "Book12 should have been added to Library1")

            // Register user1 to library1
            val user01UpsertResult: Result<UUID2<User>> = roles.library1.info()?.registerUser(user1.id())
                ?: Result.failure(Exception("Library1 should have been able to register user01"))
            assertTrue(user01UpsertResult.isSuccess, "User01 should have been registered to Library1")

            // Make user2 checkout book12 from library1
            val checkedOutBookResult: Result<UUID2<Book>> = user2.checkOutBookFromLibrary(book12, roles.library1)
            assertTrue(checkedOutBookResult.isSuccess, "Book12 should have been checked out by user2")
            context.log.d(
                this,
                "User (2):" + user2.id() + " Transfer Checked-Out Book:" + book12id + " to User(1):" + user1.id()
            )

            // Give book12 from user2 to user01
            // Note: The Library that the book is checked out from ALSO transfers the checkout to the new user.
            // - Will only allow the transfer to complete if the receiving user has an account in good standing (ie: no fines, etc.)
            val transferBookToUserResult: Result<ArrayList<Book>> = user2.giveBookToUser(book12, user1)
            assertTrue(transferBookToUserResult.isSuccess, "User2 should have given Book12 to User01")
            context.log.d(this, "Transfer Book SUCCESS --> Book:" +
                    transferBookToUserResult.getOrThrow()[0].id() +
                    " to User:" + user1.id()
            )

            testUtils.printBookInfoDBandAPIEntries()
        }
    }

    @Test
    fun `Give Book From User To User is Success`() {
        runBlocking {

            // • ARRANGE
            val roles = setupDefaultRolesAndScenario(context, testUtils)
            val user1InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(1)
            val user1 = User(user1InfoResult.getOrThrow(), roles.account1, context)

            val user2InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(2)
            val user2 = User(user2InfoResult.getOrThrow(), roles.account2, context)

            val acceptBookResult: Result<ArrayList<Book>> = user2.acceptBook(roles.book1100)
            assertTrue(acceptBookResult.isSuccess, "User2 should have accepted Book1100")

            val giveBookResult: Result<ArrayList<Book>> = user2.giveBookToUser(roles.book1100, user1)
            assertTrue(giveBookResult.isSuccess, "User2 should have given Book1100 to User01")
            context.log.d(this, "Give Book SUCCESS --> Book:" + giveBookResult.getOrThrow())
        }
    }

    @Test
    fun `Transfer CheckedOut Book sourceLibrary to another Library is Success`() {
        runBlocking {

        // • ARRANGE
        val roles = setupDefaultRolesAndScenario(context, testUtils)
        val user2InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(2)
        val user2 = User(user2InfoResult.getOrThrow(), roles.account2, context)

        // Book13 represents a found book that is not in the library
        val book13InfoResult: Result<BookInfo> = testUtils.addFakeBookInfoToBookInfoRepo(13)
        val book13id: UUID2<Book> = book13InfoResult.getOrThrow().id()
        val book13 = Book(book13id, null, context) // note: sourceLibrary is null, so this book comes from an ORPHAN Library
        context.log.d(this, "OLD Source Library: name=" + book13.sourceLibrary().info()?.name)

        // Simulate a User "finding" a Book and checking it out from its ORPHAN Private Library (ie: itself)
        val checkoutResult: Result<UUID2<Book>> = user2.checkOutBookFromLibrary(book13, book13.sourceLibrary())
        assertTrue(checkoutResult.isSuccess,
            "Book13 should have been checked out by user2")

        // Represents a User assigning the "found" Book to a Library, while the Book is still checked out to the User.
        val transferResult1: Result<Book> = book13.transferToLibrary(roles.library1)
        assertTrue(transferResult1.isSuccess,
            "Book13 should have been transferred to Library1")
        context.log.d(this, "Transfer Book SUCCESS --> Book:" + transferResult1.getOrThrow())

        val transferredBook13: Book = transferResult1.getOrThrow()
        context.log.d(this, "NEW Source Library: name=" + transferredBook13.sourceLibrary().info()?.name)
        assertEquals(
            transferredBook13.sourceLibrary().info()?.name,
            roles.library1.info()?.name,
            "Book13 should have been transferred to Library1")
        }
    }
}