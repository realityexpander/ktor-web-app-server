package domain.library.data

import common.uuid2.UUID2
import domain.book.Book
import domain.common.data.Model
import domain.common.data.info.DomainInfo
import domain.library.Library
import domain.user.User
import kotlinx.serialization.Serializable
import java.util.*

/**
 * LibraryInfo is a DomainInfo class for the Library Role object.
 *
 * LibraryInfo is a mutable class that holds the information represented by the Library Role object.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

@Serializable
class LibraryInfo(
    override val id: UUID2<Library> = UUID2.randomUUID2<Library>(),
    val name: String,

    // Registered users of this library
    private val registeredUserIdToCheckedOutBookIdsMap:
        MutableMap<UUID2<User>, ArrayList<UUID2<Book>>> = mutableMapOf(),

    // Known books & number available in this library (inventory)
    private val bookIdToNumBooksAvailableMap:
        MutableMap<UUID2<Book>, Long> = mutableMapOf()

) : DomainInfo(id),
    Model.ToDomainInfoDeepCopy<LibraryInfo>
{
    constructor(
        id: UUID2<Library>,
        name: String
    ) : this(id, name, mutableMapOf(), mutableMapOf())
    constructor(libraryInfo: LibraryInfo) : this(
        libraryInfo.id(),
        libraryInfo.name,
        libraryInfo.registeredUserIdToCheckedOutBookIdsMap,
        libraryInfo.bookIdToNumBooksAvailableMap
    )
    constructor(uuid: UUID, name: String) : this(UUID2(uuid, Library::class.java), name)
    constructor(id: String, name: String) : this(UUID.fromString(id), name)

    ///////////////////////////////
    // Published Simple Getters  //  // note: no setters, all changes are made through business logic methods.
    ///////////////////////////////

    // Convenience method to get the Type-safe id from the Class
    override fun id(): UUID2<Library> {
        return this.id
    }

    override fun toString(): String {
        return this.toPrettyJson()
        // return jsonConfig.encodeToString<LibraryInfo>(this)  // causes NPE's

        // LEAVE FOR DEBUGGING
        //        return "LibraryInfo{" +
        //                "id=" + id() +
        //                ", name='" + name + '\'' +
        //                ", registeredUserIdToCheckedOutBookIdsMap=" + registeredUserIdToCheckedOutBookIdsMap +
        //                ", bookIdToNumBooksAvailableMap=" + bookIdToNumBooksAvailableMap +
        //                '}'
    }

    ////////////////////////
    // Creational Methods //
    ////////////////////////

    fun withName(name: String): LibraryInfo {
        return LibraryInfo(id(), name, registeredUserIdToCheckedOutBookIdsMap, bookIdToNumBooksAvailableMap)
    }

    /////////////////////////////////////////////
    // Published Domain Business Logic Methods //
    /////////////////////////////////////////////

    suspend fun registerUser(userId: UUID2<User>): Result<UUID2<User>> {
        return upsertUserIdIntoRegisteredUserCheckedOutBookMap(userId)
    }

    suspend fun checkOutPublicLibraryBookToUser(book: Book, user: User): Result<Book> {
        //  if(!book.isBookFromPublicLibrary())   // todo - should only allow public library books to be checked out from public libraries?
        //    return new Result.Failure<>(new IllegalArgumentException("Book is not from a public library, bookId: " + book.id()));

        val checkedOutUUID2Book: Result<UUID2<Book>> = checkOutPublicLibraryBookIdToUserId(book.id(), user.id())
        if (checkedOutUUID2Book.isFailure) 
            return Result.failure(checkedOutUUID2Book.exceptionOrNull() ?: Exception("Error checking out book, bookId: " + book.id()))

        val acceptBookResult: Result<ArrayList<Book>> = user.acceptBook(book)

        return if (acceptBookResult.isFailure)
            Result.failure(acceptBookResult.exceptionOrNull() ?: Exception("Error accepting book, bookId: " + book.id()))
        else
            Result.success(book)
    }
    private fun checkOutPublicLibraryBookIdToUserId(bookId: UUID2<Book>, userId: UUID2<User>): Result<UUID2<Book>> {
        if (!isKnownBookId(bookId))
            return Result.failure(IllegalArgumentException("BookId is not known. bookId: $bookId"))
        if (!isKnownUserId(userId))
            return Result.failure(IllegalArgumentException("UserId is not known, userId: $userId"))
        if (!isBookIdAvailableToCheckout(bookId))
            return Result.failure(IllegalArgumentException("Book is not in inventory, bookId: $bookId"))
        if (isBookIdCheckedOutByUserId(bookId, userId))
            return Result.failure(IllegalArgumentException("Book is already checked out by User, bookId: $bookId, userId: $userId"))

        val checkOutBookResult = checkOutBookIdToUserId(bookId, userId)
        if (checkOutBookResult.isFailure) return Result.failure(checkOutBookResult.exceptionOrNull()
            ?: Exception("Error checking out book, bookId: $bookId"))

        return Result.success(bookId)
    }

    suspend fun checkOutPrivateLibraryBookToUser(book: Book, user: User): Result<Book> {
        // Private library book check-outs skip Account checks.
        val checkOutBookResult = checkOutBookIdToUserId(book.id(), user.id())
        if (checkOutBookResult.isFailure) return Result.failure(checkOutBookResult.exceptionOrNull()
            ?: Exception("Error checking out book, bookId: " + book.id()))

        val acceptBookResult: Result<ArrayList<Book>> = user.acceptBook(book)
        return if (acceptBookResult.isFailure) Result.failure(acceptBookResult.exceptionOrNull()
            ?: Exception("Error accepting book, bookId: " + book.id()))
        else
            Result.success(book)
    }

    suspend fun checkInPublicLibraryBookFromUser(book: Book, user: User): Result<Book> {
        //    if(!book.isBookFromPublicLibrary()) // todo - should only allow public library books to be checked in?
        //        return new Result.Failure<>(new IllegalArgumentException("Book is not from a public library, bookId: " + book.id()));

        val returnedBookIdResult: Result<UUID2<Book>> = checkInPublicLibraryBookIdFromUserId(book.id(), user.id())
        if (returnedBookIdResult.isFailure) return Result.failure(returnedBookIdResult.exceptionOrNull()
            ?: Exception("Error checking in book, bookId: " + book.id()))

        val unacceptBookResult: Result<ArrayList<UUID2<Book>>> = user.unacceptBook(book)
        return if (unacceptBookResult.isFailure) return Result.failure(unacceptBookResult.exceptionOrNull()
            ?: Exception("Error unaccepting book, bookId: " + book.id()))
        else
            Result.success(book)
    }

    suspend fun checkInPrivateLibraryBookFromUser(book: Book, user: User): Result<Book> {
        //    if(!book.isBookFromPrivateLibrary()) // todo - should not allow private library books to be checked in from public library?
        //        return new Result.Failure<>(new IllegalArgumentException("Book is not from private library, bookId: " + book.id()));

        // Automatically register any user that is checking in book.
        if(!isKnownUserId(user.id()))
            registerUser(user.id())

        // Private Library Book check-ins skip all Public Library User Account checks.
        val checkInBookResult = checkInBookIdFromUserId(book.id(), user.id())
        if (checkInBookResult.isFailure)
            return Result.failure(checkInBookResult.exceptionOrNull()
                ?: Exception("Error checking in book, bookId: " + book.id()))

        val unacceptBookResult: Result<ArrayList<UUID2<Book>>> = user.unacceptBook(book)
        return if (unacceptBookResult.isFailure)
            return Result.failure(unacceptBookResult.exceptionOrNull()
                ?: Exception("Error unaccepting book, bookId: " + book.id()))
        else
            Result.success(book)
    }
    private fun checkInPublicLibraryBookIdFromUserId(bookId: UUID2<Book>, userId: UUID2<User>): Result<UUID2<Book>> {
        if (!isKnownBookId(bookId))
            return Result.failure(IllegalArgumentException("BookId is not known, bookId: $bookId")) // todo - do we allow unknown books to be checked in, and just add them to the list?
        if (!isKnownUserId(userId))
            return Result.failure(IllegalArgumentException("UserId is not known, userId: $userId"))
        if (!isBookIdCheckedOutByUserId(bookId, userId))
            return Result.failure(IllegalArgumentException("Book is not checked out by User, bookId: $bookId, userId: $userId"))

        val checkInBookResult = checkInBookIdFromUserId(bookId, userId)
        return if (checkInBookResult.isFailure)
            Result.failure((checkInBookResult.exceptionOrNull() ?: Exception("Error checking in book, bookId: $bookId"))
        ) else
            Result.success(bookId)
    }

    suspend fun transferCheckedOutBookFromUserToUser(
        book: Book,
        fromUser: User,
        toUser: User
    ): Result<Book> {
        toUser.fetchInfoFailureReason()?.let { return Result.failure(Exception(it)) }
        fromUser.fetchInfoFailureReason()?.let { return Result.failure(Exception(it)) }

        if (book.isBookFromPublicLibrary) {
            // Check if the fromUser can transfer the Book
            if (!isKnownUserId(fromUser.id()))
                return Result.failure(IllegalArgumentException("fromUser is not known, fromUserId: " + fromUser.id()))

            fromUser.accountInfo()?.let { accountInfo ->
                if (!accountInfo.isAccountInGoodStanding)
                    return Result.failure(IllegalArgumentException("fromUser Account is not in good standing, fromUserId: " + fromUser.id()))
            }

            // Check if receiving User can check out Book
            if (!isKnownUserId(toUser.id()))
                return Result.failure(IllegalArgumentException("toUser is not known, toUser: " + toUser.id()))

            toUser.accountInfo()?.let { accountInfo ->
                if (!accountInfo.isAccountInGoodStanding)
                    return Result.failure(IllegalArgumentException("toUser Account is not in good standing, toUser: " + toUser.id()))
            }

            if (toUser.hasReachedMaxAmountOfAcceptedPublicLibraryBooks())
                return Result.failure(IllegalArgumentException("toUser has reached max number of accepted Public Library Books, toUser: " + toUser.id()))
        }

        val returnedBookResult = checkInBookIdFromUserId(book.id(), fromUser.id())
        if (returnedBookResult.isFailure) return Result.failure(returnedBookResult.exceptionOrNull()
            ?: Exception("Error checking in book, bookId: " + book.id()))

        val checkedOutBookIdResult = checkOutBookIdToUserId(book.id(), toUser.id())
        return if (checkedOutBookIdResult.isFailure)
            Result.failure(checkedOutBookIdResult.exceptionOrNull() ?: Exception("Error checking out book, bookId: " + book.id())
        )
        else
            Result.success(book)
    }

    /////////////////////////////////////////
    // Published Domain Reporting Methods  //
    /////////////////////////////////////////

    fun findAllCheckedOutBookIdsByUserId(userId: UUID2<User>): Result<ArrayList<UUID2<Book>>> {
        return if (!isKnownUserId(userId))
            Result.failure(IllegalArgumentException("userId is not known, id: $userId"))
        else
            Result.success(registeredUserIdToCheckedOutBookIdsMap[userId] ?: ArrayList<UUID2<Book>>())
    }

    fun calculateAvailableBookIdToCountOfAvailableBooksMap(): Result<MutableMap<UUID2<Book>, Long>> {
        val availableBookIdToNumBooksAvailableMap: MutableMap<UUID2<Book>, Long> = mutableMapOf()

        val bookSet: Set<UUID2<Book>> = bookIdToNumBooksAvailableMap.keys
        for (bookId in bookSet) {
            if (isKnownBookId(bookId)) {
                val numBooksAvail: Long = bookIdToNumBooksAvailableMap[bookId] ?: 0
                availableBookIdToNumBooksAvailableMap[bookId] = numBooksAvail
            }
        }

        return Result.success(availableBookIdToNumBooksAvailableMap)
    }

    fun findAllKnownBookIds(): Set<UUID2<Book>> {
        return bookIdToNumBooksAvailableMap.keys
    }

    /////////////////////////////////
    // Published Helper Methods    //
    /////////////////////////////////

    fun isKnownBook(book: Book): Boolean {
        return isKnownBookId(book.id())
    }

    fun isKnownUser(user: User): Boolean {
        return isKnownUserId(user.id())
    }

    fun isBookAvailableToCheckout(book: Book): Boolean {
        return isBookIdAvailableToCheckout(book.id())
    }

    fun isBookCheckedOutByUser(book: Book, user: User): Boolean {
        return isBookIdCheckedOutByUserId(book.id(), user.id())
    }

    fun isBookCheckedOutByAnyUser(book: Book): Boolean {
        return isBookIdCheckedOutByAnyUser(book.id())
    }

    fun findUserIdOfCheckedOutBook(book: Book): Result<UUID2<User>> {
        return findUserIdOfCheckedOutBookId(book.id())
    }

    // Convenience method - Called from PrivateLibrary class ONLY
    fun addPrivateBookIdToInventory(bookId: UUID2<Book>, quantity: Int): Result<UUID2<Book>> {
        return addBookIdToBooksAvailableMap(bookId, quantity)
    }

    fun removeTransferringBookFromBooksAvailableMap(transferringBook: Book): Result<UUID2<Book>> {
        return removeBookIdFromBooksAvailableMap(transferringBook.id(), 1)
    }

    fun addTransferringBookToBooksAvailableMap(transferringBook: Book): Result<UUID2<Book>> {
        return addBookIdToBooksAvailableMap(transferringBook.id(), 1)
    }

    //////////////////////////////
    // Private Helper Methods   //
    //////////////////////////////

    private fun isKnownBookId(bookId: UUID2<Book>): Boolean {
        return bookIdToNumBooksAvailableMap.containsKey(bookId)
    }

    private fun isKnownUserId(userId: UUID2<User>?): Boolean {
        return registeredUserIdToCheckedOutBookIdsMap.containsKey(userId)
    }

    private fun isBookIdAvailableToCheckout(bookId: UUID2<Book>): Boolean {
        return bookIdToNumBooksAvailableMap[bookId]?.let { numBooksAvailable ->
            numBooksAvailable > 0
        } ?: false
    }

    private fun isBookIdCheckedOutByUserId(bookId: UUID2<Book>, userId: UUID2<User>): Boolean {
        return registeredUserIdToCheckedOutBookIdsMap[userId]?.contains(bookId)
            ?: false
    }

    fun isBookIdCheckedOutByAnyUser(bookId: UUID2<Book>): Boolean {
        return registeredUserIdToCheckedOutBookIdsMap.values
            .stream()
            .anyMatch { bookIds ->
                bookIds.contains(bookId)
            }
    }

    private fun findUserIdOfCheckedOutBookId(bookId: UUID2<Book>): Result<UUID2<User>> {
        if (!isBookIdCheckedOutByAnyUser(bookId))
            return Result.failure(IllegalArgumentException("Book is not checked out by any User, bookId: $bookId"))

        for (userId in registeredUserIdToCheckedOutBookIdsMap.keys) {
            if (isBookIdCheckedOutByUserId(bookId, userId)) return Result.success(userId)
        }

        return Result.failure(IllegalArgumentException("Book is not checked out by any User, bookId: $bookId"))
    }

    /////////////////////////////////////////
    // Published Testing Helper Methods    //
    /////////////////////////////////////////

    // Intention revealing method name
    fun addTestBook(bookId: UUID2<Book>, quantity: Int): Result<UUID2<Book>> {
        return addBookIdToBooksAvailableMap(bookId, quantity)
    }

    fun upsertTestUser(userId: UUID2<User>): Result<UUID2<User>> {
        return upsertUserIdIntoRegisteredUserCheckedOutBookMap(userId)
    }

    //////////////////////////////
    // Private Helper Functions //
    //////////////////////////////

    private fun checkInBookIdFromUserId(bookId: UUID2<Book>, userId: UUID2<User>): Result<Unit> {
        return try {
            addBookIdToBooksAvailableMap(bookId, 1)
            removeBookIdFromRegisteredUserCheckedOutBookMap(bookId, userId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun checkOutBookIdToUserId(bookId: UUID2<Book>, userId: UUID2<User>): Result<Unit> {
        return if (!isBookIdAvailableToCheckout(bookId))
            Result.failure(IllegalArgumentException("Book is not in inventory, bookId: $bookId"))
        else try {
                removeBookIdFromBooksAvailableMap(bookId, 1)
                addBookIdToRegisteredUserCheckedOutBookMap(bookId, userId)

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
    }

    private fun addBookIdToBooksAvailableMap(bookId: UUID2<Book>, quantityToAdd: Int): Result<UUID2<Book>> {
        if (quantityToAdd <= 0) return Result.failure(IllegalArgumentException("quantity must be > 0, quantity: $quantityToAdd"))

        try {
            if (bookIdToNumBooksAvailableMap.containsKey(bookId)) {
                bookIdToNumBooksAvailableMap[bookId]?.let { numBooksAvailable ->
                    bookIdToNumBooksAvailableMap[bookId] = numBooksAvailable + quantityToAdd
                }
            } else {
                bookIdToNumBooksAvailableMap[bookId] = quantityToAdd.toLong()
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(bookId)
    }

    private fun addBookToBooksAvailableMap(book: Book, quantity: Int): Result<Book> {
        val addedUUID2Book: Result<UUID2<Book>> = addBookIdToBooksAvailableMap(book.id(), quantity)
        return if (addedUUID2Book.isFailure) {
            Result.failure(addedUUID2Book.exceptionOrNull() ?: Exception("Error adding book to inventory, book: $book"))
        } else Result.success(book)
    }

    private fun removeBookIdFromBooksAvailableMap(bookId: UUID2<Book>, quantityToRemove: Int): Result<UUID2<Book>> {
        if (quantityToRemove <= 0) return Result.failure(IllegalArgumentException("quantity must be > 0"))

        // Check if book is in inventory first
        if (!bookIdToNumBooksAvailableMap.containsKey(bookId))
            return Result.failure(IllegalArgumentException("Book is not in inventory, bookId: $bookId"))

        // Check if there are enough books in inventory
        if (bookIdToNumBooksAvailableMap[bookId]!! < quantityToRemove)
            return Result.failure(IllegalArgumentException("Not enough books in inventory, bookId: $bookId, quantityToRemove: $quantityToRemove, numBooksAvailable: ${bookIdToNumBooksAvailableMap[bookId]}"))

        // Simulate network/database call
        try {
            if (bookIdToNumBooksAvailableMap.containsKey(bookId)) {
                bookIdToNumBooksAvailableMap[bookId]?.let{ numBooksAvailable ->
                    bookIdToNumBooksAvailableMap[bookId] = numBooksAvailable - quantityToRemove
                }
            } else {
                return Result.failure(Exception("Book not in inventory, id: $bookId"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(bookId)
    }

    private fun removeBookFromBooksAvailableMap(book: Book, quantity: Int): Result<Book> {
        val removedUUID2Book: Result<UUID2<Book>> = removeBookIdFromBooksAvailableMap(book.id(), quantity)

        return if (removedUUID2Book.isFailure) Result.failure(removedUUID2Book.exceptionOrNull()
            ?: Exception("Error removing book from inventory, book: $book"))
        else
            Result.success(book)
    }

    private fun addBookIdToRegisteredUserCheckedOutBookMap(bookId: UUID2<Book>, userId: UUID2<User>): Result<UUID2<Book>> {
        if (!isKnownBookId(bookId))
            return Result.failure(IllegalArgumentException("bookId is not known, id: $bookId"))
        if (!isKnownUserId(userId))
            return Result.failure(IllegalArgumentException("userId is not known, id: $userId"))
        if (isBookIdCheckedOutByUserId(bookId, userId))
            return Result.failure(IllegalArgumentException("Book is already checked out by user, bookId: $bookId, userId: $userId"))

        try {
            if (registeredUserIdToCheckedOutBookIdsMap.containsKey(userId)) {
                registeredUserIdToCheckedOutBookIdsMap[userId]?.add(bookId)
            } else {
                registeredUserIdToCheckedOutBookIdsMap[userId] = arrayListOf(bookId)
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(bookId)
    }

    private fun addBookToRegisteredUserCheckedOutBookMap(book: Book, user: User): Result<Book> {
        val addBookToUserResult: Result<UUID2<Book>> = addBookIdToRegisteredUserCheckedOutBookMap(book.id(), user.id())

        return if (addBookToUserResult.isFailure) Result.failure(addBookToUserResult.exceptionOrNull()
                ?: Exception("Error adding book to user, book: $book, user: $user"))
        else
            Result.success(book)
    }

    private fun removeBookIdFromRegisteredUserCheckedOutBookMap(bookId: UUID2<Book>, userId: UUID2<User>): Result<UUID2<Book>> {
        if (!isKnownBookId(bookId))
            return Result.failure(IllegalArgumentException("bookId is not known, bookId: $bookId"))
        if (!isKnownUserId(userId))
            return Result.failure(IllegalArgumentException("userId is not known, userId: $userId"))
        if (!isBookIdCheckedOutByUserId(bookId, userId))
            return Result.failure(IllegalArgumentException("Book is not checked out by User, bookId: $bookId, userId: $userId"))

        try {
            // todo reduce count instead of remove? Allow User to check out multiple copies of the same book?
            registeredUserIdToCheckedOutBookIdsMap[userId]
                ?.remove(bookId)
                ?: return Result.failure(Exception("Error removing book from user, bookId: $bookId, userId: $userId"))
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(bookId)
    }

    private fun removeBookFromRegisteredUserCheckedOutMap(book: Book, user: User): Result<Book> {
        val removedBookResult: Result<UUID2<Book>> = removeBookIdFromRegisteredUserCheckedOutBookMap(book.id(), user.id())

        return if (removedBookResult.isFailure) Result.failure(removedBookResult.exceptionOrNull()
            ?: Exception("Error removing book from user, book: $book, user: $user"))
        else
            Result.success(book)
    }

    private fun insertUserIdIntoRegisteredUserCheckedOutBookMap(userId: UUID2<User>): Result<UUID2<User>> {
        if (isKnownUserId(userId)) return Result.failure(IllegalArgumentException("userId is already known"))

        try {
            registeredUserIdToCheckedOutBookIdsMap[userId] = ArrayList<UUID2<Book>>()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(userId)
    }

    private fun upsertUserIdIntoRegisteredUserCheckedOutBookMap(userId: UUID2<User>): Result<UUID2<User>> {
        return if (isKnownUserId(userId))
            Result.success(userId)
        else
            insertUserIdIntoRegisteredUserCheckedOutBookMap(userId)
    }

    private fun removeUserIdFromRegisteredUserCheckedOutBookMap(userId: UUID2<User>): Result<UUID2<User>> {
        if (!isKnownUserId(userId)) return Result.failure(IllegalArgumentException("userId is not known, userId: $userId"))

        try {
            registeredUserIdToCheckedOutBookIdsMap.remove(userId)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(userId)
    }

    /////////////////////////////////
    // ToDomainInfoDeepCopy implementation //
    /////////////////////////////////

    // note: currently no DB or API for UserInfo (so no .ToInfoEntity() or .ToInfoDTO())
    override fun toDomainInfoDeepCopy(): LibraryInfo {
        // Note: *MUST* return a deep copy
        val libraryInfoDeepCopy = LibraryInfo(id(), name)

        // Deep copy the bookIdToNumBooksAvailableMap
        libraryInfoDeepCopy.bookIdToNumBooksAvailableMap.putAll(bookIdToNumBooksAvailableMap)

        // Deep copy the userIdToCheckedOutBookMap
        for ((key, value) in registeredUserIdToCheckedOutBookIdsMap.entries) {
            libraryInfoDeepCopy.registeredUserIdToCheckedOutBookIdsMap[key] = ArrayList<UUID2<Book>>(value)
        }

        return libraryInfoDeepCopy
    }
}
