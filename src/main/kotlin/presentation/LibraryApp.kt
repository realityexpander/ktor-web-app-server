package presentation

import TestingUtils
import common.log.Log
import common.uuid2.UUID2
import domain.Context
import domain.account.Account
import domain.account.data.AccountInfo
import domain.book.Book
import domain.book.data.BookInfo
import domain.book.data.network.BookInfoDTO
import domain.common.Role
import domain.library.Library
import domain.library.data.LibraryInfo
import domain.user.User
import domain.user.data.UserInfo
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(DelicateCoroutinesApi::class)
internal class LibraryApp(private val context: Context) {
    // Library App - Domain Layer Root Object
    // Note: All of this has been moved to the tests.
    // This is just a reference to how to use the Domain Layer.
    
    init {
        GlobalScope.launch {

            //context = Context.setupINSTANCE(context);  // For implementing a static Context. LEAVE for reference
            val testUtils = TestingUtils(context)
            context.log.d(this, "Populating Book DB and API")
            testUtils.populateFakeBooksInBookInfoRepoDBandAPI()

            // Create fake AccountInfo
            val accountInfo = AccountInfo(
                UUID2.createFakeUUID2<Account>(1),
                "User Name 1"
            )

            Populate_And_Poke_Book@
            if (true) {
                println()
                context.log.d(this, "Populate_And_Poke_Book")
                context.log.d(this, "----------------------------------")

                // Create a book object (it only has an id)
                val book = Book(UUID2.createFakeUUID2<Book>(1100), null, context)
                context.log.d(this, book.fetchInfoResult().toString())

                // Update info for a book
                val bookInfoResult = book.updateInfo(
                    BookInfo(
                        book.id(),
                        "The Updated Title",
                        "The Updated Author",
                        "The Updated Description",
                        Instant.parse("2023-01-01T00:00:00.00Z").toEpochMilli(),
                        System.currentTimeMillis(),
                        false
                    )
                )
                context.log.d(this, book.fetchInfoResult().toString())

                // Get the bookInfo (null if not loaded)
                val bookInfo3 = book.fetchInfo()
                if (bookInfo3 == null) {
                    context.log.d(this, "Book Missing --> book id: " + book.id() + " >> " + " is null")
                    assert(false)
                } else context.log.d(this, "Book Info --> $bookInfo3")

                // Try to get a book id that doesn't exist
                val book2 = Book(UUID2.createFakeUUID2<Book>(1200), null, context)
                if (book2.fetchInfoResult().isFailure) {
                    context.log.d(
                        this,
                        "Get Book Should fail : FAILURE --> book id: " + book2.id() +
                                " >> " + book2.fetchInfoResult().getOrNull()
                    )

                    assert(true)  // fetch SHOULD fail
                } else
                    context.log.d(this, "ERROR BookInfo Exists but shouldn't --> " + book2.fetchInfoResult().getOrNull())

                testUtils.printBookInfoDBandAPIEntries()
            }

            Populate_the_library_and_user_DBs@
            if (true) {
                ////////////////////////////////////////
                // Setup DB & API simulated resources //
                ////////////////////////////////////////

                // Create & populate a Library in the Library Repo
                val libraryInfo = testUtils.createFakeLibraryInfoInLibraryInfoRepo(1)
                if (libraryInfo.isFailure) {
                    context.log.d(this, "Create Library FAILURE --> " + libraryInfo.getOrNull())
                    assert(false)
                }
                val library1InfoId: UUID2<Library> = libraryInfo.getOrThrow().id()
                context.log.d(
                    this,
                    "Library Created --> id: " + library1InfoId + ", name: " + libraryInfo.getOrThrow().name
                )

                // Populate the library
                testUtils.populateLibraryWithFakeBooks(library1InfoId, 10)

                // create Accounts for Users
                val accountInfo1Result = testUtils.createFakeAccountInfoInAccountRepo(1)
                val accountInfo2Result = testUtils.createFakeAccountInfoInAccountRepo(2)
                val accountInfo1: AccountInfo =
                    (accountInfo1Result.getOrNull() ?: throw Exception("accountInfo1 is null")) // assume success
                val accountInfo2: AccountInfo =
                    (accountInfo2Result.getOrNull() ?: throw Exception("accountInfo2 is null")) // assume success

                // Create & populate User1 in the User Repo for the Context
                val user1InfoResult = testUtils.createFakeUserInfoInUserInfoRepo(1)
                val user1Info: UserInfo = user1InfoResult.getOrThrow()

                //////////////////////////////////
                // Actual App functionality     //
                //////////////////////////////////

                // Create the App objects
                val account1 = Account(accountInfo1, context)
                val account2 = Account(accountInfo2, context)
                val user1 = User(user1Info, account1, context)
                val library1 = Library(library1InfoId, context)
                val book1100 = Book(UUID2.createFakeUUID2<Book>(1100), null, context) // create ORPHANED book
                val book1200 = Book(UUID2.createFakeUUID2<Book>(1200), library1, context)

                // print User 1
                println()
                context.log.d(this, "User --> " + user1.id() + ", " + user1.fetchInfo()?.toPrettyJson(context))
                Checkout_2_Books_to_User@
                if (true) {
                    println()
                    context.log.d(this, "Checking out 2 books to user " + user1.id())
                    context.log.d(this, "----------------------------------")
                    val bookResult = library1.checkOutBookToUser(book1100, user1)
                    if (bookResult.isFailure) {
                        context.log.e(
                            this,
                            "Checked out book FAILURE--> " + bookResult.exceptionOrNull()?.message
                        )
                        throw Exception(
                            "Checked out book FAILURE--> " + bookResult.exceptionOrNull()?.message
                        )
                    } else context.log.d(
                        this,
                        "Checked out book SUCCESS --> " + bookResult.getOrThrow().id()
                    )

                    println()
                    val bookResult2 = library1.checkOutBookToUser(book1200, user1)
                    if (bookResult2.isFailure) {
                        context.log.e(
                            this,
                            "Checked out book FAILURE--> " + bookResult2.exceptionOrNull()?.message
                        )
                        throw Exception(
                            "Checked out book FAILURE--> " + bookResult2.exceptionOrNull()?.message
                        )
                    } else context.log.d(
                        this,
                        "Checked out book SUCCESS --> " + bookResult2.getOrThrow().id()
                    )

                    library1.dumpDB(context) // LEAVE for debugging
                }

                List_Books_checked_out_by_User@ // note: relies on Checkout_2_books_to_User
                if (true) {
                    println()
                    context.log.d(this, "Getting books checked out by user " + user1.id())
                    context.log.d(this, "----------------------------------")
                    val checkedOutBooksResult: Result<ArrayList<Book>> = library1.findBooksCheckedOutByUser(user1)
                    if (checkedOutBooksResult.isFailure) {
                        context.log.d(
                            this,
                            "OH NO! --> " + (checkedOutBooksResult.exceptionOrNull() as Exception).message
                        )
                        throw Exception(
                            "OH NO! --> " + (checkedOutBooksResult.exceptionOrNull() as Exception).message
                        )
                    }
                    assert(checkedOutBooksResult.isSuccess)
                    val checkedOutBooks: ArrayList<Book> = checkedOutBooksResult.getOrNull()
                        ?: throw Exception("checkedOutBooks is null")

                    // Print checked-out books
                    println()
                    context.log.d(this, "Checked Out Books for User [" + user1.fetchInfo()?.name + ", " + user1.id() + "]:")
                    for (book in checkedOutBooks) {
                        val bookInfoResult = book.fetchInfoResult()
                        if (bookInfoResult.isFailure) context.log.e(
                            this,
                            "Book Error: " + bookInfoResult.getOrThrow()
                        ) else
                            context.log.d(this, bookInfoResult.getOrThrow().toPrettyJson(context))
                    }

                    val acceptedBookCount: Int = user1.findAllAcceptedBooks().getOrThrow().size
                    if (acceptedBookCount != 2) throw Exception("acceptedBookCount != 2")
                }

                List_available_Books_and_Inventory_Counts_in_Library@
                if (true) {
                    println()
                    context.log.d(this, "\nGetting available books and counts in library:")
                    context.log.d(this, "----------------------------------")
                    val availableBookToNumAvailableResult: Result<Map<Book, Long>> =
                        library1.calculateAvailableBookIdToNumberAvailableList()
                    if (availableBookToNumAvailableResult.isFailure) {
                        context.log.d(
                            this,
                            "AvailableBookIdCounts FAILURE! --> " +
                                    availableBookToNumAvailableResult.exceptionOrNull()?.message
                        )

                        throw Exception(
                            "AvailableBookIdCounts FAILURE! --> " +
                                    availableBookToNumAvailableResult.exceptionOrNull()?.message
                        )
                    }
                    assert(availableBookToNumAvailableResult.isSuccess)

                    // Print out available books
                    val availableBooks: Map<Book, Long> = availableBookToNumAvailableResult.getOrThrow()
                    println()
                    context.log.d(this, "Available Books in Library:")
                    for ((key, value) in availableBooks) {
                        val bookInfoResult = key
                            .fetchInfoResult()
                        if (bookInfoResult.isFailure) context.log.e(
                            this,
                            "Book Error: " + bookInfoResult.exceptionOrNull()?.message
                        ) else context.log.d(
                            this,
                            bookInfoResult.getOrThrow().id().toString() + " >> num available: " + value
                        )
                    }

                    context.log.d(this, "Total Available Books (unique UUIDs): " + availableBooks.size)
                    if (availableBooks.size != 10) throw Exception("availableBooks.size() != 10")
                }

                Check_Out_and_check_In_Book_from_User_to_Library@
                if (true) {
                    println()
                    context.log.d(
                        this,
                        "Check in book:" + book1200.id() + ", from user: " + user1.id() + ", to library:" + library1.id()
                    )
                    context.log.d(this, "----------------------------------")
                    var acceptedBookCount: Int = user1.findAllAcceptedBooks().getOrThrow().size

                    // First check out a book
                    if (!user1.hasAcceptedBook(book1200)) {
                        val checkoutResult: Result<UUID2<Book>> = user1.checkOutBookFromLibrary(book1200, library1)
                        if (checkoutResult.isSuccess) context.log.d(
                            this,
                            "Checked out book SUCCESS --> book id:" + checkoutResult.getOrThrow()
                        ) else context.log.e(
                            this,
                            "Checked out book FAILURE --> book id:" + checkoutResult.exceptionOrNull()?.message
                        )
                        val afterCheckOutBookCount: Int = user1.findAllAcceptedBooks().getOrThrow().size
                        if (afterCheckOutBookCount != acceptedBookCount + 1) throw Exception("afterCheckOutBookCount != numBooksAccepted+1")
                    }

                    acceptedBookCount = user1.findAllAcceptedBooks().getOrThrow().size
                    val checkInBookResult = library1.checkInBookFromUser(book1200, user1)
                    if (checkInBookResult.isFailure)
                        context.log.e(
                            this,
                            "Check In book FAILURE --> book id:" + checkInBookResult.exceptionOrNull()?.message
                        ) else context.log.d(
                        this,
                        "Returned Book SUCCESS --> book id:" + checkInBookResult.getOrThrow().id()
                    )
                    val afterCheckInBookCount: Int =
                        user1.findAllAcceptedBooks().getOrThrow().size
                    if (afterCheckInBookCount != acceptedBookCount - 1)
                        throw Exception("afterNumBooksAccepted != acceptedBookCount-1")

                    library1.dumpDB(context)
                }

                // Load Library from Json
                if (true) {
                    println()
                    context.log.d(this, "Load Library from Json: ")
                    context.log.d(this, "----------------------------------")

                    // Create the "unknown" library with just an id.
                    val library2 = Library(UUID2.createFakeUUID2<Library>(99), context)

                    // Show the empty info object.
                    context.log.d(this, library2.toJson())
                    if (library2.toJson() != "{}") throw Exception("library2.toJson() != {}")

                    // todo update this json
                    val json =
                        """{
                      "id": {
                        "uuid": "00000000-0000-0000-0000-000000000099",
                        "uuid2Type": "Role.Library"
                      },
                      "name": "Ronald Reagan Library",
                      "registeredUserIdToCheckedOutBookIdsMap": {
                          "UUID2:Role.User@00000000-0000-0000-0000-000000000001": []
                      },
                      "bookIdToNumBooksAvailableMap": {
                          "UUID2:Role.Book@00000000-0000-0000-0000-000000001400": 25,
                          "UUID2:Role.Book@00000000-0000-0000-0000-000000001000": 25,
                          "UUID2:Role.Book@00000000-0000-0000-0000-000000001300": 25,
                          "UUID2:Role.Book@00000000-0000-0000-0000-000000001200": 25,
                          "UUID2:Role.Book@00000000-0000-0000-0000-000000001500": 25,
                          "UUID2:Role.Book@00000000-0000-0000-0000-000000001600": 25,
                          "UUID2:Role.Book@00000000-0000-0000-0000-000000001700": 25,
                          "UUID2:Role.Book@00000000-0000-0000-0000-000000001800": 25,
                          "UUID2:Role.Book@00000000-0000-0000-0000-000000001900": 25,
                          "UUID2:Role.Book@00000000-0000-0000-0000-000000001100": 25
                      }
                    }""".trimIndent()

                    // Check JSON loaded properly
                    if (true) {
                        println()
                        context.log.d(this, "Check JSON loaded properly: ")
                        val library2Result = library2.updateInfoFromJson(json)
                        if (library2Result.isFailure) {
                            // NOTE: FAILURE IS EXPECTED HERE
                            context.log.d(this, "^^^^^^^^ warning is expected and normal.")

                            // Since the library2 was not saved in the central database, we will get a "library not found error" which is expected
                            context.log.d(this, library2Result.exceptionOrNull()?.message.toString())

                            // The JSON was still loaded properly
                            context.log.d(this, "Results of Library2 json load:" + library2.toJson())

                            // Can't just check json as the ordering of the bookIdToNumBooksAvailableMap is random - LEAVE FOR REFERENCE
                            // assert library2.toJson().equals(json);
                            // if(!library2.toJson().equals(json)) throw new Exception("Library2 JSON not equal to expected JSON");

                            // Override the failure result in order to continue testing
                            library2._overrideFetchResultToIsSuccess()

                            // check for the same number of items
                            if (library2.calculateAvailableBookIdToNumberAvailableList().getOrThrow().size != 10)
                                throw Exception("Library2 should have 10 books")

                            // check the existence of a particular book
                            if (library2.isUnknownBook(
                                    Book(
                                        UUID2.createFakeUUID2<Book>(1500),
                                        null,
                                        context
                                    )
                                )
                            ) throw Exception("Library2 should have known Book with id 1500")
                        } else {
                            // Intentionally won't see this branch bc the library2 was never saved to the central database/api.
                            context.log.d(this, "Results of Library2 json load:")
                            context.log.d(this, library2.toJson())
                            throw Exception("Library2 JSON load should have failed")
                        }
                    }

                    // Create a Library Domain Object from the Info
                    if (true) {
                        println()
                        context.log.d(this, "Create Library from LibraryInfo: ")
                        context.log.d(this, "----------------------------------")
                        try {
                            val library3Info: LibraryInfo = Role.createInfoFromJson(
                                json,
                                LibraryInfo::class.java,
                                context
                            ) ?: throw Exception("libraryInfo3 is null")
                            val library3 = Library(library3Info, context)
                            context.log.d(this, "Results of Library3 json load:" + library3.toJson())

                            // check for the same number of items
                            if (library3.calculateAvailableBookIdToNumberAvailableList().getOrThrow().size != 10)
                                throw Exception("Library2 should have 10 books")

                            // check the existence of a particular book
                            if (library3.isUnknownBook(
                                    Book(
                                        UUID2.createFakeUUID2<Book>(1900),
                                        null,
                                        context
                                    )
                                )
                            ) throw Exception("Library2 should have known Book with id 1900")
                        } catch (e: Exception) {
                            context.log.e(this, "Exception: " + e.message)
                            throw e
                        }
                    }
                }

                // Load Book from DTO Json
                if (true) {
                    println()
                    context.log.d(this, "Load BookInfo from DTO Json: ")
                    context.log.d(this, "----------------------------------")

                    // todo update this json
                    val json =
                        """{
                      "id": {
                        "uuid": "00000000-0000-0000-0000-000000000010",
                        "uuid2Type": "Role.Book"
                      },
                      "title": "The Great Gatsby",
                      "author": "F. Scott Fitzgerald",
                      "description": "The Great Gatsby is a 1925 novel written by American author F. Scott Fitzgerald that follows a cast of characters living in the fictional towns of West Egg and East Egg on prosperous Long Island in the summer of 1922. The story primarily concerns the young and mysterious millionaire Jay Gatsby and his quixotic passion and obsession with the beautiful former debutante Daisy Buchanan. Considered to be Fitzgerald's magnum opus, The Great Gatsby explores themes of decadence, idealism, resistance to change, social upheaval, and excess, creating a portrait of the Jazz Age or the Roaring Twenties that has been described as a cautionary tale regarding the American Dream.",
                      "extraFieldToShowThisIsADTO": "Extra Unneeded Data from JSON payload load"
                    }""".trimIndent()

                    try {
                        val bookInfoDTO3 = BookInfoDTO(json, context)
                        val book3: Book = Book(BookInfo(bookInfoDTO3), null, context)
                        context.log.d(this, "Results of load BookInfo from DTO Json: " + book3.toJson())
                    } catch (e: Exception) {
                        context.log.e(this, "Exception: " + e.message)
                        throw e
                    }
                }

                // Load Book from DTO Json using DTO Book constructor
                if (true) {
                    println()
                    context.log.d(this, "Load Book from DTO Json using DTO Book constructor: ")
                    context.log.d(this, "----------------------------------")

                    // todo update this json
                    val json =
                        """{
                      "id": {
                        "uuid": "00000000-0000-0000-0000-000000000010",
                        "uuid2Type": "Role.Book"
                      },
                      "title": "The Great Gatsby",
                      "author": "F. Scott Fitzgerald",
                      "description": "The Great Gatsby is a 1925 novel written by American author F. Scott Fitzgerald that follows a cast of characters living in the fictional towns of West Egg and East Egg on prosperous Long Island in the summer of 1922. The story primarily concerns the young and mysterious millionaire Jay Gatsby and his quixotic passion and obsession with the beautiful former debutante Daisy Buchanan. Considered to be Fitzgerald's magnum opus, The Great Gatsby explores themes of decadence, idealism, resistance to change, social upheaval, and excess, creating a portrait of the Jazz Age or the Roaring Twenties that has been described as a cautionary tale regarding the American Dream.",
                      "extraFieldToShowThisIsADTO": "Extra Unneeded Data from JSON payload load"
                    }""".trimIndent()

                    try {
                        val bookInfoDTO3 = BookInfoDTO(json, context)
                        val book3: Book = Book(bookInfoDTO3, null, context) // passing in DTO directly to Book constructor
                        context.log.d(this, "Results of load BookInfo from DTO Json: " + book3.toJson())
                    } catch (e: Exception) {
                        context.log.e(this, "Exception: " + e.message)
                        throw e
                    }
                }

                Check_out_Book_via_User@
                if (true) {
                    println()
                    context.log.d(this, "Check_out_Book_via_User: ")
                    context.log.d(this, "----------------------------------")
                    val user2InfoResult = testUtils.createFakeUserInfoInUserInfoRepo(2)
                    val user2 = User(user2InfoResult.getOrThrow(), account2, context)

                    val book12InfoResult = testUtils.addFakeBookInfoToBookInfoRepo(12)
                    if (book12InfoResult.isFailure) {
                        context.log.e(this, "Book Error: " + book12InfoResult.exceptionOrNull()?.message)
                        throw (book12InfoResult.exceptionOrNull() ?: Exception("book12Result is null"))
                    } else {
                        val book12id: UUID2<Book> = book12InfoResult.getOrThrow().id()
                        val book12 = Book(book12id, null, context)

                        println()
                        context.log.d(this, "Check out book " + book12id + " to user " + user1.id())
                        val book12UpsertResult = library1.addTestBookToLibrary(book12, 1)
                        if (book12UpsertResult.isFailure) {
                            context.log.d(
                                this,
                                "Upsert Book Error: " + book12UpsertResult.exceptionOrNull()?.message
                            )
                            throw (book12UpsertResult.exceptionOrNull() ?: Exception("book12UpsertResult is null"))
                        }

                        val checkedOutBookResult: Result<UUID2<Book>> = user2.checkOutBookFromLibrary(book12, library1)
                        if (checkedOutBookResult.isFailure) {
                            context.log.d(
                                this,
                                "Checkout book FAILURE --> " + checkedOutBookResult.exceptionOrNull()?.message
                            )
                            throw (checkedOutBookResult.exceptionOrNull() ?: Exception("checkedOutBookResult is null"))
                        } else context.log.d(
                            this,
                            "Checkout Book SUCCESS --> checkedOutBook:" + checkedOutBookResult.getOrThrow()
                        )
                    }
                }

                Give_Book_To_User@
                if (true) {
                    println()
                    context.log.d(this, "Give_Book_To_User: ")
                    context.log.d(this, "----------------------------------")
                    val user01InfoResult = testUtils.createFakeUserInfoInUserInfoRepo(1)
                    val user01 = User(user01InfoResult.getOrThrow(), account1, context)
                    val user2InfoResult = testUtils.createFakeUserInfoInUserInfoRepo(2)
                    val user2 = User(user2InfoResult.getOrThrow(), account2, context)

                    val book12InfoResult = testUtils.addFakeBookInfoToBookInfoRepo(12)
                    if (book12InfoResult.isFailure) {
                        context.log.e(this, "Book Error: " + book12InfoResult.exceptionOrNull()?.message)
                    } else {
                        val book12id: UUID2<Book> = (book12InfoResult.getOrThrow()).id()
                        val book12 = Book(book12id, null, context)

                        user2.acceptBook(book12) // no library involved.

                        context.log.d(
                            this, "User (2):" + user2.id() +
                                    " Give Book:" + book12id + " to User(1):" + user01.id()
                        )
                        val giveBookToUserResult: Result<ArrayList<Book>> = user2.giveBookToUser(book12, user01)
                        if (giveBookToUserResult.isFailure) {
                            context.log.d(
                                this, "Give book FAILURE --> Book:" +
                                        giveBookToUserResult.exceptionOrNull()?.message
                            )
                            throw (giveBookToUserResult.exceptionOrNull() ?: Exception("giveBookToUserResult is null"))
                        } else context.log.d(
                            this,
                            "Give Book SUCCESS --> Book:" + giveBookToUserResult.getOrThrow()
                        )
                    }
                }

                Give_Book_From_User_To_User@
                if (true) {
                    println()
                    context.log.d(this, "Give_Book_From_User_To_User: ")
                    context.log.d(this, "----------------------------------")
                    val user01InfoResult = testUtils.createFakeUserInfoInUserInfoRepo(1)
                    val user01 = User(user01InfoResult.getOrThrow(), account1, context)
                    val user2InfoResult = testUtils.createFakeUserInfoInUserInfoRepo(2)
                    val user2 = User(user2InfoResult.getOrThrow(), account2, context)

                    val acceptBookResult: Result<ArrayList<Book>> = user2.acceptBook(book1100)
                    if (acceptBookResult.isFailure) {
                        context.log.e(
                            this,
                            "Accept Book FAILURE --> Book:" + acceptBookResult.exceptionOrNull()?.message
                        )
                        throw (acceptBookResult.exceptionOrNull()!!)
                    } else context.log.d(
                        this,
                        "Accept Book SUCCESS --> Book:" + acceptBookResult.exceptionOrNull()?.message
                    )

                    val giveBookResult: Result<ArrayList<Book>> = user2.giveBookToUser(book1100, user01)

                    if (giveBookResult.isFailure) {
                        context.log.e(
                            this, "Give Book FAILURE --> Book:" +
                                    giveBookResult.exceptionOrNull()?.message
                        )
                        throw (giveBookResult.exceptionOrNull() ?: Exception("giveBookResult is null"))
                    } else context.log.d(
                        this,
                        "Give Book SUCCESS --> Book:" + giveBookResult.getOrThrow()
                    )
                }

                Transfer_Checked_out_Book_Source_Library_to_Destination_Library@
                if (true) {
                    println()
                    context.log.d(this, "Transfer_Checked_out_Book_Source_Library_to_Destination_Library: ")
                    context.log.d(this, "----------------------------------")
                    val user2InfoResult = testUtils.createFakeUserInfoInUserInfoRepo(2)
                    val user2 = User(user2InfoResult.getOrThrow(), account2, context)

                    // Book12 represents a found book that is not in the library
                    val book13InfoResult = testUtils.addFakeBookInfoToBookInfoRepo(13)
                    val book13id: UUID2<Book> = book13InfoResult.getOrThrow().id()
                    val book13 =
                        Book(
                            book13id,
                            null,
                            context
                        ) // note: sourceLibrary is null, so this book comes from an ORPHAN Library
                    context.log.d(this, "OLD Source Library: name=" + book13.sourceLibrary().info()?.name)

                    // Simulate a User "finding" a Book and checking it out from its ORPHAN Private Library (ie: itself)
                    val checkoutResult: Result<UUID2<Book>> =
                        user2.checkOutBookFromLibrary(book13, book13.sourceLibrary())
                    if (checkoutResult.isFailure) {
                        context.log.e(
                            this,
                            "Checkout Book FAILURE --> Book:" + checkoutResult.exceptionOrNull()?.message
                        )
                        throw (checkoutResult.exceptionOrNull() ?: Exception("checkoutResult is null"))
                    } else
                        context.log.d(this, "Checkout Book SUCCESS --> Book:" + checkoutResult.getOrThrow())

                    // Represents a User assigning the "found" Book to a Library, while the Book is still checked out to the User.
                    val transferResult1 = book13.transferToLibrary(library1)
                    if (transferResult1.isFailure) {
                        context.log.e(
                            this,
                            "Transfer Book FAILURE --> Book:" + transferResult1.exceptionOrNull()?.message
                        )
                        throw (transferResult1.exceptionOrNull() ?: Exception("transferResult1 is null"))
                    } else {
                        context.log.d(
                            this,
                            "Transfer Book SUCCESS --> Book:" + transferResult1.getOrThrow()
                        )
                        val transferredBook13: Book = transferResult1.getOrThrow()
                        context.log.d(
                            this, "NEW Source Library: name=" +
                                    transferredBook13.sourceLibrary().info()?.name
                        )
                        if (transferredBook13.sourceLibrary().info()?.name == library1.info()?.name)
                            context.log.d(
                                this,
                                "SUCCESS: Book was transferred to the new Library"
                            ) else {
                            context.log.e(this, "FAILURE: Book was NOT transferred to the new Library")
                            throw Exception("FAILURE: Book was NOT transferred to the new Library")
                        }
                    }
                }

                Test_UUID2_HashMap@
                if (true) {
                    println()
                    context.log.d(this, "Test UUID2 works with MutableMap: ")
                    context.log.d(this, "----------------------------------")
                    val uuid2ToEntityMap: MutableMap<UUID2<Book>, UUID2<User>> = mutableMapOf()
                    val book1: UUID2<Book> = UUID2(UUID2.createFakeUUID2<Book>(1200))
                    val book2: UUID2<Book> = UUID2(UUID2.createFakeUUID2<Book>(1300))
                    val user01: UUID2<User> = UUID2(UUID2.createFakeUUID2<User>(1))
                    val user02: UUID2<User> = UUID2(UUID2.createFakeUUID2<User>(2))

                    uuid2ToEntityMap[book1] = user01
                    uuid2ToEntityMap[book2] = user02

                    var user: UUID2<User>? = uuid2ToEntityMap[book1]
                        ?: throw Exception("user is null")
                    context.log.d(this, "user=$user")

                    val book1a: UUID2<Book> =
                        Book.fetchBook(
                            UUID2.createFakeUUID2<Book>(1200),
                            context
                        ).getOrThrow()
                            .id()

                    val user2: UUID2<User> = uuid2ToEntityMap[book1a]
                        ?: throw Exception("user2 is null")
                    context.log.d(this, "user=$user")
                    assert(user2 == user)


                    uuid2ToEntityMap.remove(book1)
                    user = uuid2ToEntityMap[book1]
                    assert(user == null)
                    context.log.d(this, "user=$user")

                    // put it back
                    uuid2ToEntityMap[book1] = user01

                    // check keySet
                    val keySet: Set<UUID2<Book>> = uuid2ToEntityMap.keys
                    assert(keySet.size == 2)

                    // check values
                    val values: Collection<UUID2<User>> = uuid2ToEntityMap.values
                    assert(values.size == 2)

                    // check entrySet
                    val entrySet: Set<Map.Entry<UUID2<Book>, UUID2<User>>> = uuid2ToEntityMap.entries
                    assert(entrySet.size == 2)

                    // check containsKey
                    if (!uuid2ToEntityMap.containsKey(book1))
                        throw RuntimeException("containsKey(book1) failed")
                    if (!uuid2ToEntityMap.containsKey(book2))
                        throw RuntimeException("containsKey(book2) failed")
                    if (uuid2ToEntityMap.containsKey(UUID2.createFakeUUID2<Book>(1400)))
                        throw RuntimeException("containsKey(Book 1400) should have failed")
                }
            }
            context.log.d(
                this,
                """
            *****************************
            Trials Completed Successfully
            *****************************
            
            """.trimIndent()
            )
        }

//    companion object {
//        @Throws(Exception::class)
//        @JvmStatic
//        fun main(args: Array<String>) {
//
//            // Setup App Context Object singletons
//            val productionContext = Context.setupProductionInstance(Log())
//
//            // Note: Check the tests!
//            LibraryApp(productionContext)
//        }
//    }

    }
}

fun main() {

    // Setup App Context Object singletons
    val productionContext = Context.setupProductionInstance(Log())

    // Note: Check the tests!
    LibraryApp(productionContext)
}
