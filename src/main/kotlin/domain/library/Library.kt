package domain.library

import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.Context
import domain.book.Book
import domain.common.Role
import domain.library.data.LibraryInfo
import domain.library.data.LibraryInfoRepo
import okhttp3.internal.toImmutableMap

/**
 * Library Role Object<br></br>
 * <br></br>
 * Library is a Role Object that represents a Library in the LibraryApp domain.<br></br>
 * <br></br>
 * *ONLY* interacts with its own Repo, Context, and other Role Objects<br></br>
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */
open class Library : Role<LibraryInfo>, IUUID2 {
    private val repo: LibraryInfoRepo

    constructor(
        info: LibraryInfo,
        context: Context
    ) : super(info, context) {
        repo = context.libraryInfoRepo()
        context.log.d(this, "Library (" + id() + ") created from Info")
    }

    constructor(
        json: String,
        clazz: Class<LibraryInfo>,
        context: Context
    ) : super(json, clazz, context) {
        repo = context.libraryInfoRepo()
        context.log.d(this, "Library (" + id() + ") created from Json with class: " + clazz.getName())
    }

    constructor(
        id: UUID2<Library>,
        context: Context
    ) : super(id, context) {
        repo = context.libraryInfoRepo()
        context.log.d(this, "Library (" + id() + ") created using id with no Info")
    }

    constructor(json: String, context: Context) : this(json, LibraryInfo::class.java, context)
    constructor(context: Context) : this(UUID2.randomUUID2(Library::class.java), context)

    ////////////////////////////////
    // Published Simple Getters   //
    ////////////////////////////////

    // Convenience method to get the Type-safe id from the Class
    fun id(): UUID2<Library> {
        return super.id() as UUID2<Library>
    }

    /////////////////////////////////////
    // Role/UUID2 Required Overrides  //
    /////////////////////////////////////

    override fun fetchInfoResult(): Result<LibraryInfo> {
        // context.log.d(this,"Library (" + this.id.toString() + ") - fetchInfoResult"); // LEAVE for debugging
        return repo.fetchLibraryInfo(id())
    }

    override fun updateInfo(updatedInfo: LibraryInfo): Result<LibraryInfo> {
        // context.log.d(this,"Library (" + this.id.toString() + ") - updateInfo, newInfo: " + newInfo.toString());  // LEAVE for debugging

        // Optimistically Update the Cached LibraryInfo
        super.updateFetchInfoResult(Result.success(updatedInfo))

        // Update the Repo
        return repo.updateLibraryInfo(updatedInfo)
    }

    override fun uuid2TypeStr(): String {
        // Get the Class Inheritance Path from the Class Path
        return UUID2.calcUUID2TypeStr(this.javaClass)
    }

    ///////////////////////////////////////////
    // Library Role Business Logic Methods   //
    // - Methods to modify it's LibraryInfo  //
    // - Communicate with other ROle objects //
    ///////////////////////////////////////////
    open fun checkOutBookToUser(book: Book, user: User): Result<Book> {
        context.log.d(this, "Library (" + id() + ") - bookId: " + book.id() + ", userId: " + user.id())
        if (fetchInfoFailureReason() != null) return Result.failure(Exception(fetchInfoFailureReason()))
        if (isUnableToFindOrRegisterUser(user)) return Result.failure(Exception("User is not known, userId: " + user.id()))

        // Note: this calls a wrapper to the User's Account Role object
        if (!user.isAccountInGoodStanding()) return Result.failure(Exception("User Account is not active, userId: " + user.id()))

        // Note: this calls a wrapper to the User's Account Role object
        if (user.hasReachedMaxAmountOfAcceptedPublicLibraryBooks()) return Result.failure(Exception("User has reached max num Books accepted, userId: " + user.id()))
        if (user.hasAcceptedBook(book)) return Result.failure(Exception("User has already accepted this Book, userId: " + user.id() + ", bookId: " + book.id()))

        // Get User's AccountInfo object
        val userAccountInfo: AccountInfo = user.accountInfo()
            ?: return Result.failure(Exception("User AccountInfo is null, userId: " + user.id()))

        // Check User fines are not exceeded
        if (userAccountInfo.isMaxFineExceeded()) return Result.failure(Exception("User has exceeded maximum fines, userId: " + user.id()))

        // Check out Book to User
        val checkOutBookResult: Result<Book> = this.info()?.checkOutPublicLibraryBookToUser(book, user) ?: return Result.failure(Exception("LibraryInfo is null, libraryId: " + id()))
        if (checkOutBookResult.isFailure) return Result.failure(checkOutBookResult.exceptionOrNull() ?: Exception("Unknown failure checking out Book, userId: " + user.id() + ", bookId: " + book.id()))

        // Update Info, since we modified data for this Library
        val updateInfoResult: Result<LibraryInfo> = updateInfo(this.info()
            ?: return Result.failure(Exception("LibraryInfo is null, libraryId: " + id())))
        return if (updateInfoResult.isFailure)
            Result.failure((updateInfoResult.exceptionOrNull() ?: Exception("Unknown failure updating LibraryInfo, libraryId: " + id())))
        else
            Result.success(book)
    }

    open fun checkInBookFromUser(book: Book, user: User): Result<Book> {
        context.log.d(
            this,
            "Library (${id()}) - bookId ${book.id()} from userID ${user.id()}")
        )
        if (fetchInfoFailureReason() != null) return Result.failure(Exception(fetchInfoFailureReason()))
        if (isUnableToFindOrRegisterUser(user)) return Result.failure(Exception("User is not known, id: " + user.id()))
        val checkInBookResult: Result<Book> = this.info().checkInPublicLibraryBookFromUser(book, user)
        if (checkInBookResult.isFailure) return Result.failure((checkInBookResult.exceptionOrNull() ?: Exception("Unknown failure checking in Book, userId: " + user.id() + ", bookId: " + book.id())))

        // Update Info, since we modified data for this Library
        val updateInfoResult: Result<LibraryInfo> = updateInfo(this.info())
        return if (updateInfoResult.isFailure)
            Result.failure((updateInfoResult.exceptionOrNull() ?: Exception("Unknown failure updating LibraryInfo, libraryId: " + id())))
        else
            Result.success(book)
    }

    fun transferCheckedOutBookSourceLibraryToThisLibrary(bookToTransfer: Book, user: User): Result<Book> {
        context.log.d(this, "Library (${id()}) - bookId ${bookToTransfer.id()} from userID ${user.id()}")
        if (fetchInfoFailureReason() != null) return Result.failure(Exception(fetchInfoFailureReason()))

        // Check in Book to current Source Library
        val checkInBookResult: Result<Book> = checkInBookFromUser(bookToTransfer, user)
        if (checkInBookResult.isFailure) return Result.failure((checkInBookResult.exceptionOrNull()
            ?: Exception("Unknown failure checking in Book, userId: " + user.id() + ", bookId: " + bookToTransfer.id())))

        // Transfer Book to this Library
        val transferBookResult: Result<Book> = transferBookSourceLibraryToThisLibrary(bookToTransfer)

        // Check out Book to User from this Library
        val checkOutBookResult: Result<Book> = checkOutBookToUser(bookToTransfer, user)
        if (checkOutBookResult.isFailure) return Result.failure((checkOutBookResult.exceptionOrNull()
            ?: Exception("Unknown failure checking out Book, userId: " + user.id() + ", bookId: " + bookToTransfer.id())))

        // Update Info, since we modified data for this Library
        val updateInfoResult: Result<LibraryInfo> = updateInfo(this.info()
            ?: return Result.failure(Exception("LibraryInfo is null, libraryId: " + id())))
        return if (updateInfoResult.isFailure)
            Result.failure((updateInfoResult.exceptionOrNull()
                ?: Exception("Unknown failure updating LibraryInfo, libraryId: " + id())))
        else
            Result.success(bookToTransfer)
    }

    // Note: this does not change the Checkout status of the User
    fun transferBookSourceLibraryToThisLibrary(bookToTransfer: Book): Result<Book> {
        context.log.d(this, "Library (${id()}) - bookId ${bookToTransfer.id()}")
        if (fetchInfoFailureReason() != null) return Result.failure(Exception(fetchInfoFailureReason()))

        // Get the Book's Source Library
        val fromSourceLibrary: Library = bookToTransfer.sourceLibrary()
            ?: return Result.failure(Exception("Book's Source Library is null, bookId: " + bookToTransfer.id()))

        // Check `from` Source Library is same as this Library
        if (fromSourceLibrary.id() == id())
            return Result.failure(Exception("Book's Source Library is the same as this Library, bookId: " + bookToTransfer.id()))

        // Check if `from` Source Library is known
        if (fromSourceLibrary.fetchInfoFailureReason() != null) return Result.failure(Exception("Book's Source Library is not known, bookId: " + bookToTransfer.id()))

        // Check if Book is known at `from` Source Library
        val fromSourceLibraryInfo: LibraryInfo = fromSourceLibrary.info()
            ?: return Result.failure(Exception("LibraryInfo is null, libraryId: " + fromSourceLibrary.id()))
        if ( !fromSourceLibraryInfo.isKnownBook(bookToTransfer) )
            return Result.failure(Exception("Book is not known at from Source Library, bookId: " + bookToTransfer.id()))


        // Remove Book from Library Inventory of Books at `from` Source Library
        val removeBookResult: Result<UUID2<Book>> =
            fromSourceLibraryInfo.removeTransferringBookFromInventory(bookToTransfer)
        if (removeBookResult.isFailure)
            return Result.failure((removeBookResult.exceptionOrNull()
                ?: Exception("Unknown failure removing Book from Library Inventory of Books, bookId: " + bookToTransfer.id())))

        // Update `from` Source Library Info, bc data was modified for `from` Source Library
        val updateFromSourceLibraryInfoResult: Result<LibraryInfo> = fromSourceLibrary.updateInfo(fromSourceLibraryInfo)
        if (updateFromSourceLibraryInfoResult.isFailure) return Result.failure((updateFromSourceLibraryInfoResult.exceptionOrNull()
            ?: Exception("Unknown failure updating LibraryInfo, libraryId: " + fromSourceLibrary.id())))


        // Add Book to this Library's Inventory of Books
        val addBookResult: Result<UUID2<Book>> = this.info()?.addTransferringBookToInventory(bookToTransfer)
            ?: return Result.failure(Exception("LibraryInfo is null, libraryId: " + id()))
        if (addBookResult.isFailure) return Result.failure((addBookResult.exceptionOrNull()
            ?: Exception("Unknown failure adding Book to Library Inventory of Books, bookId: " + bookToTransfer.id())))


        // • Transfer Book's Source Library to this Library
        val transferredBookResult: Result<Book> =
            bookToTransfer.updateSourceLibrary(this) // note: this only modifies the Book Role object, not the BookInfo.
        if (transferredBookResult.isFailure) return Result.failure((transferredBookResult.exceptionOrNull()
            ?: Exception("Unknown failure updating Book's Source Library, bookId: " + bookToTransfer.id())))

        // Update Info, bc data was modified for this Library
        val updateInfoResult2: Result<LibraryInfo> = updateInfo(this.info()
            ?: return Result.failure(Exception("LibraryInfo is null, libraryId: " + id())))
        return if (updateInfoResult2.isFailure)
            Result.failure((updateInfoResult2.exceptionOrNull() ?: Exception("Unknown failure updating LibraryInfo, libraryId: " + id())))
        else
            Result.success(bookToTransfer)
    }

    /////////////////////////////////
    // Published Helper Methods    //
    /////////////////////////////////

    // Note: This Library Role Object enforces the rule:
    //   - if a User is not known, they are added as a new user.   // todo change to Result<> return type
    fun isUnableToFindOrRegisterUser(user: User): Boolean {
        context.log.d(this, "Library(${id()}) - userId ${user.id()}")
        if (fetchInfoFailureReason() != null) return true
        if (isKnownUser(user)) {
            return false
        }

        // Automatically register a new User entry in the Library (if not already known)
        // Note: todo - In a real system, this would be a separate step, and the User would have to confirm their registration.
        val addRegisteredUserResult: Result<UUID2<User>> = this.info()?.registerUser(user.id()) ?: return true
        if (addRegisteredUserResult.isFailure) return true

        // Update Info, bc data was modified for this Library
        val updateInfoResult: Result<LibraryInfo> = updateInfo(this.info() ?: return true)
        return updateInfoResult.isFailure
    }

    fun isKnownBook(book: Book): Boolean {
        context.log.d(this, "Library(${id()}) - bookId ${book.id()}")
        return if (fetchInfoFailureReason() != null)
            false
        else
            this.info()?.isKnownBook(book)
                ?: false
    }

    fun isUnknownBook(book: Book): Boolean {
        return !isKnownBook(book)
    }

    fun isKnownUser(user: User): Boolean {
        context.log.d(this, "Library(${id()}) - userId ${user.id()}")
        return if (fetchInfoFailureReason() != null)
            false
        else
            this.info()?.isKnownUser(user)
                ?: false
    }

    fun isBookAvailable(book: Book): Boolean {
        context.log.d(this, "Library(${id()}) - bookId ${book.id()}")
        return if (fetchInfoFailureReason() != null)
            false
        else
            this.info()?.isBookAvailableToCheckout(book)
                ?: false
    }

    fun isBookCheckedOutByAnyUser(book: Book): Boolean {  // todo return Result<>?
        context.log.d(this, "Library(${id()}) - bookId ${book.id()}")
        return if (fetchInfoFailureReason() != null)
            false
        else
            this.info()?.isBookIdCheckedOutByAnyUser(book.id())
                ?: false
    }

    fun getUserOfCheckedOutBook(book: Book): Result<User> {
        context.log.d(this, "Library(${id()}) - bookId ${book.id()}")
        if (fetchInfoFailureReason() != null) return Result.failure(Exception(fetchInfoFailureReason()))

        // get the User's id from the Book checkout record
        val userIdResult: Result<UUID2<User>> = this.info()?.findUserIdOfCheckedOutBook(book)
            ?: return Result.failure(Exception("LibraryInfo is null, libraryId: " + id()))
        if (userIdResult.isFailure) return Result.failure((userIdResult.exceptionOrNull()
            ?: Exception("Unknown failure finding User id of checked out Book, bookId: " + book.id())))
        val userId: UUID2<User> = userIdResult.getOrNull()
            ?: return Result.failure(Exception("User id is null, bookId: " + book.id()))
        val fetchUserResult: Result<User> = User.fetchUser(userId, context)
        if (fetchUserResult.isFailure) return Result.failure((fetchUserResult.exceptionOrNull()
            ?: Exception("Unknown failure fetching User, userId: $userId")))
        val user: User = fetchUserResult.exceptionOrNull()
            ?: return Result.failure(Exception("User is null, userId: $userId"))
        return Result.success(user)
    }

    /////////////////////////////////////////
    // Published Role Reporting Methods    //
    /////////////////////////////////////////

    fun findBooksCheckedOutByUser(user: User): Result<ArrayList<Book>> {
        context.log.d(this, "Library(${id()}) - userId ${user.id()}")
        if (fetchInfoFailureReason() != null) return Result.failure(Exception(fetchInfoFailureReason()))

        // Make sure User is Known
        if (isUnableToFindOrRegisterUser(user)) {
            return Result.failure(Exception("User is not known, userId: " + user.id()))
        }
        val entriesResult: Result<ArrayList<UUID2<Book>>> =
            this.info()?.findAllCheckedOutBookIdsByUserId(user.id())
                ?: return Result.failure(Exception("LibraryInfo is null, libraryId: " + id()))
        if (entriesResult.isFailure) {
            return Result.failure((entriesResult.exceptionOrNull()
                ?: Exception("Unknown failure finding checked out Book ids by User, userId: " + user.id())))
        }

        // Convert UUID2<Books to Books
        val bookIds: ArrayList<UUID2<Book>> = (entriesResult.getOrNull()
            ?: return Result.failure(Exception("Book ids is null, userId: " + user.id())))
        val books: ArrayList<Book> = ArrayList<Book>()
        for (entry in bookIds) {
            books.add(Book(entry, this, context))
        }
        return Result.success(books)
    }

    fun calculateAvailableBookIdToNumberAvailableList(): Result<Map<Book, Long>> {
        context.log.d(this, "Library (" + id() + ")")
        if (fetchInfoFailureReason() != null) return Result.failure(Exception(fetchInfoFailureReason()))

        val entriesResult: Result<MutableMap<UUID2<Book>, Long>> =
            this.info()?.calculateAvailableBookIdToCountOfAvailableBooksMap()
                ?: return Result.failure(Exception("LibraryInfo is null, libraryId: " + id()))
        if (entriesResult.isFailure) {
            return Result.failure((entriesResult.exceptionOrNull()
                ?: Exception("Unknown failure calculating available Book id to count of available Books map, libraryId: " + id())))
        }

        // Convert list of UUID2<Book> to list of Book
        // Note: the BookInfo is not fetched, so the Book only contains the id. This is by design.
        val bookIdToNumberAvailable: MutableMap<UUID2<Book>, Long> =
            (entriesResult.getOrNull()
                ?: return Result.failure(Exception("Book id to count of available Books map is null, libraryId: " + id())))
        val bookToNumberAvailable: MutableMap<Book, Long> = mutableMapOf()
        for ((key, value) in bookIdToNumberAvailable) {
            bookToNumberAvailable[Book(key, this, context)] = value
        }
        return Result.success(bookToNumberAvailable.toImmutableMap())
    }

    /////////////////////////////////////////
    // Published Testing Helper Methods    //
    /////////////////////////////////////////
    // Intention revealing method name
    fun addTestBookToLibrary(book: Book, count: Int): Result<Book> {
        context.log.d(this, "Library (" + id() + ") book: " + book + ", count: " + count)
        return addBookToLibrary(book, count)
    }

    fun addBookToLibrary(book: Book, count: Int): Result<Book> {
        context.log.d(this, "Library (" + id() + ") book: " + book + ", count: " + count)
        if (fetchInfoFailureReason() != null) return Result.failure(Exception(fetchInfoFailureReason()))

        val addBookResult: Result<UUID2<Book>> = this.info()?.addTestBook(book.id(), count)
            ?: return Result.failure(Exception("LibraryInfo is null, libraryId: " + id()))
        if (addBookResult.isFailure) return Result.failure((addBookResult.exceptionOrNull()
            ?: Exception("Unknown failure adding Book to Library, bookId: " + book.id())))

        // Update the Info
        val updateInfoResult: Result<LibraryInfo> = updateInfo(this.info()
            ?: return Result.failure(Exception("LibraryInfo is null, libraryId: " + id())))
        return if (updateInfoResult.isFailure)
            Result.failure((updateInfoResult.exceptionOrNull()
                ?: Exception("Unknown failure updating LibraryInfo, libraryId: " + id())))
        else
            Result.success(book)
    }

    fun DumpDB(context: Context) {
        context.log.d(this, "Dumping Library DB:")
        context.log.d(this, this.toJson())
        println()
    }

    fun transferBookAndCheckOutFromUserToUser(book: Book, fromUser: User, toUser: User): Result<Book> {
        context.log.d(this, "Library (" + id() + ") book: " + book + ", fromUser: " + fromUser + ", toUser: " + toUser)
        if (fetchInfoFailureReason() != null) return Result.failure(Exception(fetchInfoFailureReason()))

        val transferResult: Result<Book> = this.info()?.transferCheckedOutBookFromUserToUser(book, fromUser, toUser)
            ?: return Result.failure(Exception("LibraryInfo is null, libraryId: " + id()))
        if (transferResult.isFailure) return Result.failure((transferResult.exceptionOrNull()
            ?: Exception("Unknown failure transferring Book from User to User, bookId: " + book.id())))

        // Update the Info
        val updateInfoResult: Result<LibraryInfo> = updateInfo(this.info()
            ?: return Result.failure(Exception("LibraryInfo is null, libraryId: " + id())))
        return if (updateInfoResult.isFailure)
            Result.failure((updateInfoResult.exceptionOrNull()
                ?: Exception("Unknown failure updating LibraryInfo, libraryId: " + id())))
        else
            Result.success(book)
    }

    companion object {
        /////////////////////////
        // Static constructors //
        /////////////////////////

        fun fetchLibrary(
            uuid2: UUID2<Library>,
            context: Context
        ): Result<Library> {
            val repo: LibraryInfoRepo = context.libraryInfoRepo
            val fetchInfoResult: Result<LibraryInfo> = repo.fetchLibraryInfo(uuid2)
            if (fetchInfoResult.isFailure) {
                return Result.failure((fetchInfoResult.exceptionOrNull()
                    ?: Exception("Unknown failure fetching LibraryInfo, libraryId: $uuid2")))
            }

            val info: LibraryInfo = (fetchInfoResult.getOrNull()
                ?: return Result.failure(Exception("LibraryInfo is null, libraryId: $uuid2")))

            return Result.success(Library(info, context))
        }
    }
}