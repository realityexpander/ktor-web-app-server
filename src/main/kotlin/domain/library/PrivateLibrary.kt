package domain.library

import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.Context
import domain.book.Book
import domain.library.data.LibraryInfo
import domain.user.User

/**
 *
 * Private Library
 * 
 * A Private Library is not a system Library, so it doesn't access the Account Role Object for any account checks.
 * 
 * Used to represent a "Personal" Library, or a library for a single "found" book, or a Library for a newly created
 * book, etc.
 *
 *
 *  * A **`PrivateLibrary`** is a **`Library`** that is not part of
 *    any system **`Library`**.
 *  * Any **`User`** can **`checkIn()`** and **`checkOut()`** any **`Book`**
 *    from this **`PrivateLibrary`**.
 *  * Users can have unlimited Private Libraries & unlimited number of Books in them.
 *  * A **`PrivateLibrary`** is identical to a normal **`Library`**, except it doesn't verify any
 *    **`Account`** info and any **`User`** can **`checkIn`** and
 *    **`checkOut`** any **`Book`**.
 *  * _Note: A special case **`PrivateLibrary`** is an Orphan ****`PrivateLibrary` which
 *    only allows a single Book of a specific id to be checked into/out of it. See below._
 *
 * 
 * BOOP design notes for this **`PrivateLibrary`** class:
 * 
 * This is a system design **alternative** to:
 *
 *  * ... using null to represent a Book which is not part of any Library.
 *  * ... naming this class "NoLibrary" or "PersonalLibrary" or "UnassignedLibrary"
 *  * ... to using "null" we create an object that conveys the intention behind what "null" means in this context.
 *  * Question: What is the concept of a "null" Library? Maybe it is a Library which is not part of any system Library?
 *  * How about a "Library" which is not part of any system Library is called a "PrivateLibrary"?
 *
 * ORPHAN Private Libraries:
 *
 *  * Orphan definition: *An orphan is a child that has no parent.*<br></br>
 *  * For a Book, it would have no "source" Public Library.
 *  * If a Private Library is created from a BookId, it is called an ORPHAN Private Library
 *    and its sole duty is to hold ONLY 1 Book of one specific BookId, and never any other BookIds.
 *  * It can only ever hold 1 Book at a time.
 *  * ORPHAN PrivateLibraries have the **`isForOnlyOneBook`** flag set to true.
 *  * Note: I could have subclassed **`PrivateLibrary`** into **`OrphanPrivateLibrary`**,
 *    but that would have added a deeper inheritance tree & complexity to the system for a simple edge use case.
 *
 */

class PrivateLibrary : Library, IUUID2 {
    // true = ORPHAN Private Library, false = normal Private Library
    private val isForOnlyOneBook: Boolean

    // Note: the naming here conveys the intent of the variable,
    //       even if the reader doesn't know about "Orphan" libraries.
    constructor(
        info: LibraryInfo,
        context: Context
    ) : super(info, context) {
        id()._setUUID2TypeStr(UUID2.calcUUID2TypeStr(PrivateLibrary::class.java))
        isForOnlyOneBook = false
    }
    constructor(
        id: UUID2<Library>,
        context: Context
    ) : super(id, context) {
        id()._setUUID2TypeStr(UUID2.calcUUID2TypeStr(PrivateLibrary::class.java))
        isForOnlyOneBook = false
    }
    constructor(
        bookId: UUID2<Book>,
        @Suppress("unused")
        isForOnlyOneBook: Boolean,  // note: always `true` for this constructor.
        context: Context
    ) : super(UUID2(bookId.uuid(), Library::class.java), context) // make the LibraryId match the BookId
    {
        // Note: This creates an ORPHAN private library.
        // It is an ORPHAN bc it is NOT associated with any other system Library (private or not).
        id()._setUUID2TypeStr(UUID2.calcUUID2TypeStr(PrivateLibrary::class.java))

        // It is an ORPHAN bc it is NOT associated with any other system Library (private or not).
        // ORPHAN Private Library can:
        //  - Contain only 1 Book,
        //  - And the 1 BookId must always match initial BookId that created this Orphan Library.
        this.isForOnlyOneBook = true
    }
    constructor(
        context: Context
    ) : this(UUID2.randomUUID2<Book>(), true,  context) {
        // Note: this creates an ORPHAN private library with a random id.
        context.log.w(this, "PrivateLibrary (" + id() + ") created with ORPHAN Library with Random Id.")
    }

    //////////////////////////////////////////////////
    // PrivateLibrary Domain Business Logic Methods //
    //////////////////////////////////////////////////

    override suspend fun checkOutBookToUser(book: Book, user: User): Result<Book> {
        context.log.d(this, "checkOutBookToUser() called, bookId: " + book.id() + ", userId: " + user.id())
        fetchInfoFailureReason()?.let { return Result.failure(Exception(it)) }

        val libraryInfo = super.info() ?: return Result.failure(Exception("Failed to get LibraryInfo, bookId: " + book.id()))

        // Automatically upsert the User into the Library's User Register
        // - Private libraries are open to all users, so we don't need to check if the user is registered.
        val addRegisteredUserResult = libraryInfo.registerUser(user.id())
            ?: return Result.failure(Exception("Failed to register User in Library, userId: " + user.id()))
        if (addRegisteredUserResult.isFailure) return Result.failure(Exception("Failed to register User in Library, userId: " + user.id()))

        if (!isForOnlyOneBook) {
            // note: PrivateLibraries bypass all normal Library User Account checks
            return libraryInfo.checkOutPrivateLibraryBookToUser(book, user)
        }

        // Orphan Libraries can only check out 1 Book to 1 User.
        if (libraryInfo.findAllKnownBookIds().size != 1)
            return Result.failure(Exception("Orphan Private Library can only check-out 1 Book to Users, bookId: " + book.id()))

        // Only allow check out if the Book Id matches the initial Book Id that created this Orphan Library.
        val bookIds = libraryInfo.findAllKnownBookIds()
        val firstBookId = bookIds.toTypedArray()[0] // there should only be 1 bookId
        if (firstBookId != book.id()) return Result.failure(Exception("Orphan Private Library can only check-out 1 Book to Users and must be the same Id as the initial Book placed in the PrivateLibrary, bookId: " + book.id()))

        val checkOutResult = libraryInfo.checkOutPrivateLibraryBookToUser(book, user) // note: we bypass all normal Library User Account checking
        if (checkOutResult.isFailure) return Result.failure(checkOutResult.exceptionOrNull()
                ?: Exception("Failed to check-out Book from Library, bookId: " + book.id()))

        // Update the Info
        val updateInfoResult: Result<LibraryInfo> = updateInfo(libraryInfo)
        return if (updateInfoResult.isFailure)
            Result.failure(updateInfoResult.exceptionOrNull() ?: Exception("Failed to update LibraryInfo, bookId: " + book.id()))
        else
            checkOutResult
    }

    override suspend fun checkInBookFromUser(book: Book, user: User): Result<Book> {
        context.log.d(this, "checkInBookFromUser() called, bookId: " + book.id() + ", userId: " + user.id())
        fetchInfoFailureReason()?.let { return Result.failure(Exception(it)) }

        if (!isForOnlyOneBook) {
            // note: we bypass all normal Library User Account checking
            return super.info()?.checkInPrivateLibraryBookFromUser(book, user) 
                ?: Result.failure(Exception("Failed to check-in Book from Library, bookId: " + book.id()))
        }

        // Orphan Libraries can only check in 1 Book from Users.
        info()?.findAllKnownBookIds()?.size?.let {
            if (it != 1) return Result.failure(Exception("Orphan Private Library can only check-in 1 Book from Users, bookId: " + book.id()))
        }

        // Only allow checkIn if the BookId matches the initial BookId that created this Orphan PrivateLibrary.
        info()?.let { libraryInfo ->
            val bookIds: Set<UUID2<Book>> = libraryInfo.findAllKnownBookIds()
            val firstBookId: UUID2<Book> = bookIds.toTypedArray()[0] // there should only be 1 BookId
            
            if (firstBookId != book.id())
                return Result.failure(Exception("Orphan Private Library can only check-in 1 Book from Users and must be the same Id as the initial Book placed in the PrivateLibrary, bookId: " + book.id()))
        } ?: return Result.failure(Exception("Failed to check-in Book from Private Library, bookId: " + book.id()))

        // note: we bypass all normal Library User Account checking
        val checkInResult = super.info()?.checkInPrivateLibraryBookFromUser(book, user)
            ?: Result.failure(Exception("Failed to check-in Book from PrivateLibrary, bookId: " + book.id() + ", userId: " + user.id()))
        if (checkInResult.isFailure) 
            return Result.failure(Exception("Failed to check-in Book from Private Library, bookId: " + book.id()))

        // Update the Info
        val updateInfoResult = updateInfo(info()
            ?: return Result.failure(Exception("Failed to update LibraryInfo, bookId: " + book.id())))
        return if (updateInfoResult.isFailure)
            Result.failure(updateInfoResult.exceptionOrNull()
                ?: Exception("Failed to update LibraryInfo, bookId: " + book.id()))
        else
            checkInResult
    }
}