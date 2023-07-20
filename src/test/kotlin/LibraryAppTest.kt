import common.uuid2.UUID2
import domain.Context
import domain.account.Account
import domain.account.data.AccountInfo
import domain.book.data.BookInfo
import domain.book.data.network.DTOBookInfo
import domain.library.Library
import domain.user.User
import domain.book.Book
import domain.common.Role
import domain.library.data.LibraryInfo
import domain.user.data.UserInfo
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import testFakes.common.util.log.TestLog
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.fail
import java.time.Instant

/**
 * LibraryAppTest integration tests for the LibraryApp.<br></br>
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin Conversion
 */

class LibraryAppTest {
    private val ctx: Context = setupDefaultTestContext()
    private val testUtils: TestingUtils = TestingUtils(ctx)
    
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
        ctx: Context,
        testUtils: TestingUtils
    ): TestRoles {

        ////////////////////////////////////////
        // Setup DB & API simulated resources //
        ////////////////////////////////////////

        // • Put some fake BookInfo into the DB & API for BookInfo's
        testUtils.populateFakeBookInfoInBookRepoDBandAPI()

        // • Create & populate a Library in the Library Repo
        val libraryInfo = testUtils.createFakeLibraryInfoInLibraryInfoRepo(1)
        assertTrue(libraryInfo.isSuccess, "Create Library FAILURE --> $libraryInfo")
        val library1InfoId: UUID2<Library> = libraryInfo.getOrThrow().id()
        ctx.log.d(
            this,
            "Library Created --> id: " + (libraryInfo.getOrNull() ?: fail("libraryInfo is null")).id
                 + ", name: " + (libraryInfo.getOrNull() ?: fail("libraryInfo is null")).name
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
        Assertions.assertTrue(accountInfo2Result.isSuccess)
        val accountInfo1: AccountInfo = accountInfo1Result.getOrThrow()
        val accountInfo2: AccountInfo = accountInfo2Result.getOrThrow()

        // Create & populate User1 in the User Repo for the Context
        val user1InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(1)
        assertNotNull(user1InfoResult)
        Assertions.assertTrue(user1InfoResult.isSuccess)
        val user1Info: UserInfo = (user1InfoResult?.getOrNull() ?: fail("user1InfoResult is null"))

        ///////////////////////////
        // Create Default Roles  //
        ///////////////////////////

        val account1 = Account(accountInfo1, ctx)
        assertNotNull(account1)
        val library1: Library = Library(library1InfoId, ctx)
        assertNotNull(library1)

        // Create the Test Roles
        val testRoles = TestRoles(
            account1,
            Account(accountInfo2, ctx),
            User(user1Info, account1, ctx),
            library1,
            domain.book.Book(UUID2.createFakeUUID2(1100, Book::class.java), null, ctx),  // create ORPHANED book
            domain.book.Book(UUID2.createFakeUUID2(1200, Book::class.java), library1, ctx)
        )
        assertNotNull(testRoles)

        // print User1
        ctx.log.d(this, "User --> " + testRoles.user1.id + ", " + testRoles.user1.fetchInfo()?.toPrettyJson(ctx))
        return testRoles
    }

    @Test
    fun `Update BookInfo is Success`() {
        // • ARRANGE

        // Create fake book info in the DB & API
        val bookId = 1100
        testUtils.addFakeBookInfoToBookInfoRepo(bookId)

        // Create a book object (it only has an id)
        val book = domain.book.Book(UUID2.createFakeUUID2(bookId, Book::class.java), null, ctx)
        ctx.log.d(this, book.fetchInfoResult().toString())
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
        assertTrue(bookInfoResult.isSuccess,
             "Update BookInfo FAILURE --> " + bookInfoResult.exceptionOrNull()?.message)

        // Get the bookInfo (null if not loaded)
        val bookInfo3: BookInfo? = book.fetchInfo()
        if (bookInfo3 == null) {
            ctx.log.d(this, "Book Missing --> book id: " + book.id() + " >> " + " is null")
            fail("Book Missing --> book id: " + book.id() + " >> " + " is null")
        }

        // Check the title for updated info
        assertEquals(expectedUpdatedTitle, bookInfoResult.getOrThrow().title)
        assertEquals(expectedUpdatedAuthor, bookInfoResult.getOrThrow().author)
        assertEquals(expectedUpdatedDescription, bookInfoResult.getOrThrow().description)
    }

    @Test
    fun `Fetch NonExisting Book is Failure`() {
        // • ARRANGE

        // • ACT
        // Try to get a book id that doesn't exist - SHOULD FAIL
        val book2 = domain.book.Book(UUID2.createFakeUUID2(99, Book::class.java), null, ctx)
        val bookInfoResult2: Result<BookInfo> = book2.fetchInfoResult()

        // • ASSERT
        assertTrue(bookInfoResult2.isFailure,
            "Book SHOULD NOT Exist, but does! --> " + book2.id())
    }

    @Test
    fun `CheckOut 2 Books to User is Success`() {
        // • ARRANGE
        val roles = setupDefaultRolesAndScenario(ctx, testUtils)

        // • ACT
        val bookResult: Result<Book> = roles.library1.checkOutBookToUser(roles.book1100, roles.user1)
        val bookResult2: Result<Book> = roles.library1.checkOutBookToUser(roles.book1200, roles.user1)

        // • ASSERT
        assertTrue(bookResult.isSuccess, "Checked out book FAILURE, bookId: " + roles.book1100.id)
        assertTrue(bookResult2.isSuccess, "Checked out book FAILURE, bookId: " + roles.book1200.id)

        roles.library1.dumpDB(ctx) // LEAVE for debugging
    }

    @Test
    fun `Find Books checkedOut by User is Success`() {
        // • ARRANGE
        val roles = setupDefaultRolesAndScenario(ctx, TestingUtils(ctx))

        // Checkout 2 books to User
        val bookResult1: Result<Book> = roles.library1.checkOutBookToUser(roles.book1100, roles.user1)
        val bookResult2: Result<Book> = roles.library1.checkOutBookToUser(roles.book1200, roles.user1)
        assertTrue(bookResult1.isSuccess,
            "Checked out book FAILURE, bookId: " + roles.book1100.id)
        assertTrue(bookResult2.isSuccess,
            "Checked out book FAILURE, bookId: " + roles.book1200.id)

        // • ACT & ASSERT

        // Find books checked out by user
        val checkedOutBooksResult = roles.library1.findBooksCheckedOutByUser(roles.user1)
        assertTrue(
            checkedOutBooksResult.isSuccess,
            "findBooksCheckedOutByUser FAILURE for userId" + roles.user1.id())
        val checkedOutBooks = checkedOutBooksResult.getOrThrow()

        // List Books
        ctx.log.d(this, "Checked Out Books for User [" + roles.user1.fetchInfo()?.name + ", " +
                roles.user1.id() + "]:")
        for (book in checkedOutBooks) {
            val bookInfoResult: Result<BookInfo> = book.fetchInfoResult()
            assertTrue(bookInfoResult.isSuccess,
                "Book Error: bookId" + book.id())
            ctx.log.d(this, bookInfoResult.getOrThrow().toPrettyJson(ctx))
        }

        val acceptedBookCount: Int =
            roles.user1.findAllAcceptedBooks().getOrThrow().size
        assertEquals(2,
            acceptedBookCount,
            "acceptedBookCount != 2")
    }

    @Test
    fun `Calculate availableBook To numAvailable Map is Success`() {
        // • ARRANGE
        val roles = setupDefaultRolesAndScenario(ctx, TestingUtils(ctx))

        // Checkout 2 books
        val bookResult1: Result<Book> = roles.library1.checkOutBookToUser(roles.book1100, roles.user1)
        val bookResult2: Result<Book> = roles.library1.checkOutBookToUser(roles.book1200, roles.user1)
        val availableBookToNumAvailableResult: Result<Map<Book, Long>> =
            roles.library1.calculateAvailableBookIdToNumberAvailableList()
        assertTrue(availableBookToNumAvailableResult.isSuccess,
            "findBooksCheckedOutByUser FAILURE for libraryId" + roles.library1.id())

        // create objects and populate info for available books
        val availableBooks= availableBookToNumAvailableResult.getOrThrow().keys
        assertTrue(availableBooks.isNotEmpty())

        // Print out available books
        println()
        ctx.log.d(this, "Available Books in Library:")
        for (key in availableBooks) {
            val bookInfoResult: Result<BookInfo> = key.fetchInfoResult()
            assertTrue(bookInfoResult.isSuccess,
                "Book Error: bookId" + key.id())
            ctx.log.d(this, bookInfoResult.getOrThrow().toPrettyJson(ctx))
        }

        ctx.log.d(this, "Total Available Books (unique UUIDs): " + availableBooks.size)
        assertEquals(availableBooks.size,
            10,
            "availableBooks.size() != 10")
    }

    @Test
    fun `CheckOut and CheckIn Book to Library is Success`() {
        // • ARRANGE
        val roles = setupDefaultRolesAndScenario(ctx, TestingUtils(ctx))
        val initialBookCount: Int = roles.user1.findAllAcceptedBooks().getOrThrow().size

        // • ACT & ASSERT

        // First check out book
        val checkoutResult: Result<UUID2<Book>> = roles.user1.checkOutBookFromLibrary(roles.book1200, roles.library1)
        assertTrue(checkoutResult.isSuccess,
            "Checked out book FAILURE --> book id:" + roles.book1200.id())
        val afterCheckOutBookCount: Int = roles.user1.findAllAcceptedBooks().getOrThrow().size
        assertEquals(
            afterCheckOutBookCount,
            initialBookCount + 1,
            "afterCheckOutBookCount != initialAcceptedBookCount+1")

        // Now check in Book
        val checkInBookResult: Result<Book> = roles.library1.checkInBookFromUser(roles.book1200, roles.user1)
        assertTrue(checkInBookResult.isSuccess,
            "Checked out book FAILURE --> book id:" + roles.book1200.id())
        val afterCheckInBookCount: Int = roles.user1.findAllAcceptedBooks().getOrThrow().size
        assertEquals(
            initialBookCount,
            afterCheckInBookCount,
            "afterCheckInBookCount != initialBookCount",)

        roles.library1.dumpDB(ctx)
    }

    private val ronaldReaganLibraryInfoJson: String =
        """
            {
              "name": "Ronald Reagan Library",
              "id": {
                "uuid": "00000000-0000-0000-0000-000000000099",
                "uuid2Type": "Role.Library"
              },
              "registeredUserIdToCheckedOutBookIdsMap": {
                "UUID2:Role.User@00000000-0000-0000-0000-000000000001": [
                  {
                    "uuid":"00000000-0000-0000-0000-000000001500",
                    "uuid2Type":"Role.Book"
                  }
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

    @Test
    fun `Update LibraryInfo by updateInfoFromJson is Success`() {
        // • ARRANGE
        val json = ronaldReaganLibraryInfoJson

        // Create the "unknown" library with just an id.
        val library99: Library = Library(UUID2.createFakeUUID2(99, Library::class.java), ctx)
        val book1500 = Book(UUID2.createFakeUUID2(1500, Book::class.java), null, ctx)

        val roles = setupDefaultRolesAndScenario(ctx, TestingUtils(ctx))

        // • ASSERT
        // Get empty info object.
        ctx.log.d(this, "Should be empty object: " + library99.toJson())
        assertEquals(
            library99.toJson(),
            "{}",
            "library99.toJson() != {}")

        // Check JSON loaded properly
        val library99UpdateResult = library99.updateInfoFromJson(json)
        if (library99UpdateResult.isFailure) {
            // NOTE: FAILURE IS EXPECTED HERE
            println("▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲ 2 Warnings `Library➤toJson()` are expected and normal.")
            ctx.log.d(this, "▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲ warnings are expected and normal.")

            // Since the library2 was not saved in the central database, we will get a "library not found error" which is expected
            ctx.log.d(this, "Error: " + library99UpdateResult.exceptionOrNull()?.message)

            // The JSON was still loaded properly
            ctx.log.d(this, "Results of Library2 json load:" + library99.toJson())

            // LEAVE FOR REFERENCE
            // Note: Can't just do simple "text equality" check on Json because the ordering of
            //   the `bookIdToNumBooksAvailableMap` is random.
            // // assert library2.toJson().equals(json);
            // // if(!library2.toJson().equals(json)) throw new Exception("Library2 JSON not equal to expected JSON");

            // check for same number of items
            assertEquals(
                library99.calculateAvailableBookIdToNumberAvailableList().getOrThrow().size,
                10,
                "Library2 should have 10 books")

            // check existence of a particular book
            assertTrue(library99.isKnownBook(book1500), "Library2 should have known Book with id 1500")
        } else {
            // Intentionally should NEVER see this branch bc the library2 was never saved to the central database/api.
            ctx.log.d(this, "Results of Library2 json load:")
            ctx.log.d(this, library99.toJson())
            fail("Library2 JSON load should have failed")
        }
    }

    @Test
    fun `Update LibraryInfo by updateInfoFromJson with wrong id in JSON is Failure`() {
        // • ARRANGE
        val json = ronaldReaganLibraryInfoJson
        val roles = setupDefaultRolesAndScenario(ctx, TestingUtils(ctx))
        val library99: Library = Library(UUID2.createFakeUUID2(99, Library::class.java), ctx)

        // • ASSERT
        assertEquals(
            library99.toJson(),
            "{}",
            "library99.toJson() != {}")

        // Attempt to load json when ID's don't match
        val result = library99.updateInfoFromJson(roles.library1.toJson())
        if (result.isFailure) {
            assertTrue(true, "Expected failure")
        } else {
            fail("Expected failure")
        }

        println("▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲ 2 Warnings `Library➤toJson()` are expected and normal.")
        ctx.log.d(this, "▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲ warnings are expected and normal.")
    }

    @Test
    fun `Create Library Role from createInfoFromJson is Success`() {
        // • ARRANGE
        val json = ronaldReaganLibraryInfoJson
        val expectedBook1900 = Book(UUID2.createFakeUUID2(1900, Book::class.java), null, ctx)

        // Create a Library Domain Object from the Info
        try {

            // • ACT
            val libraryInfo: LibraryInfo? = Role.createInfoFromJson(
                json,
                LibraryInfo::class.java,
                ctx
            )
            assertNotNull(libraryInfo)
            val library = Library(libraryInfo!!, ctx)
            ctx.log.d(this, "Results of Library3 json load:" + library.toJson())

            // • ASSERT
            // check for same number of items
            assertEquals(
                library.calculateAvailableBookIdToNumberAvailableList().getOrThrow().size,
                10,
                "Library2 should have 10 books")

            // check existence of a particular book
            assertTrue(library.isKnownBook(expectedBook1900),
                "Library2 should have known Book with id=" + expectedBook1900.id())
        } catch (e: Exception) {
            ctx.log.e(this, "Exception: " + e.message)
            fail(e.message)
        }
    }

    private val greatGatsbyDTOBookInfoJson: String =
            """{
              "id": {
                "uuid": "00000000-0000-0000-0000-000000000010",
                "uuid2Type": "Model.DTOInfo.DTOBookInfo"
              },
              "title": "The Great Gatsby",
              "author": "F. Scott Fitzgerald",
              "description": "The Great Gatsby is a 1925 novel written by American author F. Scott Fitzgerald that follows a cast of characters living in the fictional towns of West Egg and East Egg on prosperous Long Island in the summer of 1922. The story primarily concerns the young and mysterious millionaire Jay Gatsby and his quixotic passion and obsession with the beautiful former debutante Daisy Buchanan. Considered to be Fitzgerald's magnum opus, The Great Gatsby explores themes of decadence, idealism, resistance to change, social upheaval, and excess, creating a portrait of the Jazz Age or the Roaring Twenties that has been described as a cautionary tale regarding the American Dream.",
              "extraFieldToShowThisIsADTO": "Extra Unneeded Data from JSON payload load"
            }
            """.trimIndent()

    @Test
    fun `Create Book Role from DTOInfo Json`() {
        // • ARRANGE
        val json = greatGatsbyDTOBookInfoJson
        val expectedTitle = "The Great Gatsby"
        val expectedAuthor = "F. Scott Fitzgerald"
        val expectedUUID2: UUID2<Book> = UUID2.createFakeUUID2(10, Book::class.java)
        val expectedUuid2Type: String = expectedUUID2.uuid2Type

        // • ACT & ASSERT
        try {
            val dtoBookInfo3 = DTOBookInfo(json, ctx)
            assertNotNull(dtoBookInfo3)
            val book3 = domain.book.Book(BookInfo(dtoBookInfo3), null, ctx)
            assertNotNull(book3)

            ctx.log.d(this, "Results of load BookInfo from DTO Json: " + book3.toJson())
            assertEquals(
                expectedTitle,
                book3.info()?.title,
                "Book3 should have title:$expectedTitle"
            )
            assertEquals(
                expectedAuthor,
                book3.info()?.author,
                "Book3 should have author:$expectedAuthor"
            )
            assertEquals(
                expectedUUID2,
                book3.id(),
                "Book3 should have id: $expectedUUID2"
            )
            assertEquals(
                expectedUuid2Type,
                book3.id().uuid2Type,
                "Book3 should have UUID2 Type of:$expectedUuid2Type"
            )
        } catch (e: Exception) {
            ctx.log.e(this, "Exception: " + e.message)
            fail(e.message)
        }
    }

    @Test
    fun `Create new Book then CheckOut Book to User is Success`() {
        // • ARRANGE
        val roles = setupDefaultRolesAndScenario(ctx, testUtils)
        val user2InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(2)
        val user2 = User(user2InfoResult.getOrThrow(), roles.account2, ctx)
        assertNotNull(user2)
        val book12Result: Result<BookInfo> = testUtils.addFakeBookInfoToBookInfoRepo(12)
        assertTrue(book12Result.isSuccess,
            "Book12 should have been added to Library1"
        )

        // • ACT & ASSERT

        // Create new Book by id
        val book12id: UUID2<Book> = book12Result.getOrThrow().id()
        val book12 = Book(book12id, null, ctx)
        assertNotNull(book12)

        // Add Book to Library
        val book12UpsertResult: Result<Book> = roles.library1.addTestBookToLibrary(book12, 1)
        assertTrue(book12UpsertResult.isSuccess,
            "Book12 should have been added to Library1",
        )

        // Check out Book from Library
        ctx.log.d(this, "Check out book " + book12id + " to user " + roles.user1.id())
        val checkedOutBookResult: Result<UUID2<Book>> = user2.checkOutBookFromLibrary(book12, roles.library1)
        assertTrue(checkedOutBookResult.isSuccess,
            "Book12 should have been checked out by user2",
        )
    }

    @Test
    fun `User Accepts Book and Gives Book to another User is Success`() {
        // • ARRANGE
        val roles = setupDefaultRolesAndScenario(ctx, testUtils)
        val user01InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(1)
        assertNotNull(user01InfoResult)
        assertTrue(user01InfoResult.isSuccess,
            "User01 should have been added to UserInfoRepo"
        )
        val user01 = User(user01InfoResult.getOrThrow(), roles.account1, ctx)
        assertNotNull(user01)

        val user2InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(2)
        assertNotNull(user2InfoResult)
        assertTrue(user2InfoResult.isSuccess,
            "User2 should have been added to UserInfoRepo"
        )
        val user2 = User(user2InfoResult.getOrThrow(), roles.account2, ctx)
        assertNotNull(user2)

        val book12InfoResult: Result<BookInfo> = testUtils.addFakeBookInfoToBookInfoRepo(12)
        assertTrue(book12InfoResult.isSuccess,
            "Book12 should have been added to Library1"
        )
        val book12id: UUID2<Book> = book12InfoResult.getOrThrow().id()
        val book12 = Book(book12id, null, ctx)
        assertNotNull(book12)

        // • ACT & ASSERT
        val acceptBookResult: Result<ArrayList<Book>> = user2.acceptBook(book12) // no library involved.
        assertTrue(acceptBookResult.isSuccess,
            "User2 should have accepted Book12"
        )
        ctx.log.d(this, "User (2):" + user2.id() + " Give Book:" + book12id + " to User(1):" + user01.id())
        val giveBookToUserResult: Result<ArrayList<Book>> = user2.giveBookToUser(book12, user01)
        assertTrue(giveBookToUserResult.isSuccess,
            "User2 should have given Book12 to User01",
        )
    }

    @Test
    fun `Give CheckedOut Book From User To User is Success`() {
        // • ARRANGE
        val roles = setupDefaultRolesAndScenario(ctx, testUtils)
        val user01InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(1)
        val user01 = User(user01InfoResult.getOrThrow(), roles.account1, ctx)
        val user2InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(2)
        val user2 = User(user2InfoResult.getOrThrow(), roles.account2, ctx)
        val book12InfoResult: Result<BookInfo> = testUtils.addFakeBookInfoToBookInfoRepo(12)
        val book12id: UUID2<Book> = book12InfoResult.getOrThrow().id()
        val book12 = Book(book12id, roles.library1, ctx)

        // • ACT & ASSERT

        // Add book12 to library1
        val book12UpsertResult: Result<Book> = roles.library1.addTestBookToLibrary(book12, 1)
        assertTrue(
            book12UpsertResult.isSuccess,
            "Book12 should have been added to Library1")

        // Register user1 to library1
        val user01UpsertResult: Result<UUID2<User>> = roles.library1.info()?.registerUser(user01.id())
            ?: Result.failure(Exception("Library1 should have been able to register user01"))
        assertTrue(user01UpsertResult.isSuccess,
            "User01 should have been registered to Library1")

        // Make user2 checkout book12 from library1
        val checkedOutBookResult: Result<UUID2<Book>> = user2.checkOutBookFromLibrary(book12, roles.library1)
        assertTrue(checkedOutBookResult.isSuccess,
            "Book12 should have been checked out by user2",
        )
        ctx.log.d(this,
            "User (2):" + user2.id() + " Transfer Checked-Out Book:" + book12id + " to User(1):" + user01.id()
        )

        // Give book12 from user2 to user01
        // Note: The Library that the book is checked out from ALSO transfers the checkout to the new user.
        // - Will only allow the transfer to complete if the receiving user has an account in good standing (ie: no fines, etc.)
        val transferBookToUserResult: Result<ArrayList<Book>> = user2.giveBookToUser(book12, user01)
        assertTrue(transferBookToUserResult.isSuccess,
            "User2 should have given Book12 to User01")
        ctx.log.d(this, "Transfer Book SUCCESS --> Book:" + transferBookToUserResult.getOrThrow()[0].id() + " to User:" + user01.id())

        testUtils.printBookInfoDBandAPIEntries()
    }

    @Test
    fun `Give Book From User To User is Success`() {
        // • ARRANGE
        val roles = setupDefaultRolesAndScenario(ctx, testUtils)
        val user01InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(1)
        val user01 = User(user01InfoResult.getOrThrow(), roles.account1, ctx)
        val user2InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(2)
        val user2 = User(user2InfoResult.getOrThrow(), roles.account2, ctx)

        val acceptBookResult: Result<ArrayList<Book>> = user2.acceptBook(roles.book1100)
        assertTrue(acceptBookResult.isSuccess,
            "User2 should have accepted Book1100")

        val giveBookResult: Result<ArrayList<Book>> = user2.giveBookToUser(roles.book1100, user01)
        assertTrue(giveBookResult.isSuccess,
            "User2 should have given Book1100 to User01")
        ctx.log.d(this, "Give Book SUCCESS --> Book:" + giveBookResult.getOrThrow())
    }

    @Test
    fun `Transfer CheckedOut Book sourceLibrary to another Library is Success`() {
        // • ARRANGE
        val roles = setupDefaultRolesAndScenario(ctx, testUtils)
        val user2InfoResult: Result<UserInfo> = testUtils.createFakeUserInfoInUserInfoRepo(2)
        val user2 = User(user2InfoResult.getOrThrow(), roles.account2, ctx)

        // Book13 represents a found book that is not in the library
        val book13InfoResult: Result<BookInfo> = testUtils.addFakeBookInfoToBookInfoRepo(13)
        val book13id: UUID2<Book> = book13InfoResult.getOrThrow().id()
        val book13 = Book(book13id, null, ctx) // note: sourceLibrary is null, so this book comes from an ORPHAN Library
        ctx.log.d(this, "OLD Source Library: name=" + book13.sourceLibrary().info()?.name)

        // Simulate a User "finding" a Book and checking it out from its ORPHAN Private Library (ie: itself)
        val checkoutResult: Result<UUID2<Book>> = user2.checkOutBookFromLibrary(book13, book13.sourceLibrary())
        assertTrue(checkoutResult.isSuccess,
            "Book13 should have been checked out by user2")

        // Represents a User assigning the "found" Book to a Library, while the Book is still checked out to the User.
        val transferResult1: Result<Book> = book13.transferToLibrary(roles.library1)
        assertTrue(transferResult1.isSuccess,
            "Book13 should have been transferred to Library1")
        ctx.log.d(this, "Transfer Book SUCCESS --> Book:" + transferResult1.getOrThrow())

        val transferredBook13: Book = transferResult1.getOrThrow()
        ctx.log.d(this, "NEW Source Library: name=" + transferredBook13.sourceLibrary().info()?.name)
        assertEquals(
            transferredBook13.sourceLibrary().info()?.name,
            roles.library1.info()?.name,
            "Book13 should have been transferred to Library1")
    }

    companion object {
        const val shouldDisplayAllDebugLogs = false // Set to `true` to see all debug logs

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
}