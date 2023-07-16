package domain.library.data

import common.uuid2.UUID2
import domain.book.Book
import domain.common.data.Model
import domain.common.data.info.DomainInfo
import domain.library.Library
import domain.user.User
import java.util.*

/**
 * LibraryInfo is a DomainInfo class for the Library domain object.
 *
 * LibraryInfo is a mutable class that contains information about the Library domain object.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */

class LibraryInfo(
    val id: UUID2<Library>,
    val name: String,
    registeredUserIdToCheckedOutBookIdMap: MutableMap<UUID2<User>, ArrayList<UUID2<Book>>>,
    bookIdToNumBooksAvailableMap: MutableMap<UUID2<Book>, Long>
) : DomainInfo(id.toDomainUUID2()),
    Model.ToDomainInfo<LibraryInfo>
{
    // registered users of this library
    private val registeredUserIdToCheckedOutBookIdMap: MutableMap<UUID2<User>, ArrayList<UUID2<Book>>>

    // known books & number available in this library (inventory)
    private val bookIdToNumBooksAvailableMap: MutableMap<UUID2<Book>, Long>

    init {
        this.registeredUserIdToCheckedOutBookIdMap = registeredUserIdToCheckedOutBookIdMap
        this.bookIdToNumBooksAvailableMap = bookIdToNumBooksAvailableMap
    }

    constructor(id: UUID2<Library>, name: String) : this(id, name, mutableMapOf(), mutableMapOf())
    internal constructor(libraryInfo: LibraryInfo) : this(
        libraryInfo.id(),
        libraryInfo.name,
        libraryInfo.registeredUserIdToCheckedOutBookIdMap,
        libraryInfo.bookIdToNumBooksAvailableMap
    )
    constructor(uuid: UUID, name: String) : this(UUID2(uuid, Library::class.java), name)
    constructor(id: String, name: String) : this(UUID.fromString(id), name)

    ///////////////////////////////
    // Published Simple Getters  //
    ///////////////////////////////

    // Convenience method to get the Type-safe id from the Class
    override fun id(): UUID2<Library> {
//        return super.id() as UUID2<Library>
        return this.id
    }

    override fun toString(): String {
        return this.toPrettyJson()
    }

    ////////////////////////
    // Creational Methods //
    ////////////////////////
    fun withName(name: String): LibraryInfo {
        return LibraryInfo(id(), name, registeredUserIdToCheckedOutBookIdMap, bookIdToNumBooksAvailableMap)
    }

    /////////////////////////////////////////////
    // Published Domain Business Logic Methods //
    /////////////////////////////////////////////

    fun checkOutPublicLibraryBookToUser(book: Book, user: User): Result<Book> {
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

    fun checkOutPrivateLibraryBookToUser(book: Book, user: User): Result<Book> {
        // Private library book check-outs skip Account checks.
        val checkOutBookResult = _checkOutBookIdToUserId(book.id(), user.id())
        if (checkOutBookResult.isFailure) return Result.failure(checkOutBookResult.exceptionOrNull()
            ?: Exception("Error checking out book, bookId: " + book.id()))

        val addBookResult: Result<UUID2<Book>> = addBookIdToRegisteredUser(book.id(), user.id())
        if (addBookResult.isFailure) return Result.failure(addBookResult.exceptionOrNull()
            ?: Exception("Error adding book to user, bookId: " + book.id()))

        val acceptBookResult: Result<ArrayList<Book>> = user.acceptBook(book)
        return if (acceptBookResult.isFailure) Result.failure(acceptBookResult.exceptionOrNull()
            ?: Exception("Error accepting book, bookId: " + book.id()))
        else
            Result.success(book)
    }

    fun checkOutPublicLibraryBookIdToUserId(bookId: UUID2<Book>, userId: UUID2<User>): Result<UUID2<Book>> {
        if (!isKnownBookId(bookId))
            return Result.failure(IllegalArgumentException("BookId is not known. bookId: $bookId"))
        if (!isKnownUserId(userId))
            return Result.failure(IllegalArgumentException("UserId is not known, userId: $userId"))
        if (!isBookIdAvailableToCheckout(bookId))
            return Result.failure(IllegalArgumentException("Book is not in inventory, bookId: $bookId"))
        if (isBookIdCheckedOutByUserId(bookId, userId))
            return Result.failure(IllegalArgumentException("Book is already checked out by User, bookId: $bookId, userId: $userId"))

        val checkOutBookResult = _checkOutBookIdToUserId(bookId, userId)
        if (checkOutBookResult.isFailure) return Result.failure(checkOutBookResult.exceptionOrNull()
            ?: Exception("Error checking out book, bookId: $bookId"))

        val addBookResult: Result<UUID2<Book>> = addBookIdToRegisteredUser(bookId, userId)
        return if (addBookResult.isFailure)
            Result.failure(addBookResult.exceptionOrNull() ?: Exception("Error adding book to user, bookId: $bookId"))
        else
            Result.success(bookId)
    }

    fun checkInPublicLibraryBookFromUser(book: Book, user: User): Result<Book> {
        //    if(!book.isBookFromPublicLibrary()) // todo - should only allow public library books to be checked in?
        //        return new Result.Failure<>(new IllegalArgumentException("Book is not from a public library, bookId: " + book.id()));
        val returnedBookIdResult: Result<UUID2<Book>> = checkInPublicLibraryBookIdFromUserId(book.id(), user.id())
        if (returnedBookIdResult.isFailure) return Result.failure(returnedBookIdResult.exceptionOrNull()
            ?: Exception("Error checking in book, bookId: " + book.id()))

        val unacceptBookResult: Result<ArrayList<UUID2<Book>?>> = user.unacceptBook(book)
        if (unacceptBookResult.isFailure) return Result.failure(unacceptBookResult.exceptionOrNull()
            ?: Exception("Error unaccepting book, bookId: " + book.id()))

        val removeBookResult: Result<UUID2<Book>> = removeBookIdFromRegisteredUserId(book.id(), user.id())
        return if (removeBookResult.isFailure) Result.failure(removeBookResult.exceptionOrNull()
            ?: Exception("Error removing book from user, bookId: " + book.id()))
        else
            Result.success(book)
    }

    fun checkInPrivateLibraryBookFromUser(book: Book, user: User): Result<Book> {
        //    if(!book.isBookFromPrivateLibrary()) // todo - should not allow private library books to be checked in from public library?
        //        return new Result.Failure<>(new IllegalArgumentException("Book is not from private library, bookId: " + book.id()));

        // Private Library Book check-ins skip all Public Library User Account checks.
        val checkInBookResult = _checkInBookIdFromUserId(book.id(), user.id())
        if (checkInBookResult.isFailure)
            return Result.failure(checkInBookResult.exceptionOrNull()
                ?: Exception("Error checking in book, bookId: " + book.id()))

        val unacceptBookResult: Result<ArrayList<UUID2<Book>?>> = user.unacceptBook(book)
        if (unacceptBookResult.isFailure)
            return Result.failure(unacceptBookResult.exceptionOrNull()
                ?: Exception("Error unaccepting book, bookId: " + book.id()))
        val removeBookResult: Result<UUID2<Book>> = removeBookIdFromRegisteredUserId(book.id(), user.id())

        return if (removeBookResult.isFailure)
            Result.failure(removeBookResult.exceptionOrNull() ?: Exception("Error removing book from user, bookId: " + book.id()))
        else
            Result.success(book)
    }

    fun checkInPublicLibraryBookIdFromUserId(bookId: UUID2<Book>, userId: UUID2<User>): Result<UUID2<Book>> {
        if (!isKnownBookId(bookId))
            return Result.failure(IllegalArgumentException("BookId is not known, bookId: $bookId")) // todo - do we allow unknown books to be checked in, and just add them to the list?
        if (!isKnownUserId(userId))
            return Result.failure(IllegalArgumentException("UserId is not known, userId: $userId"))
        if (!isBookIdCheckedOutByUserId(bookId, userId))
            return Result.failure(IllegalArgumentException("Book is not checked out by User, bookId: $bookId, userId: $userId"))
        val checkInBookResult = _checkInBookIdFromUserId(bookId, userId)

        return if (checkInBookResult.isFailure)
            Result.failure((checkInBookResult.exceptionOrNull() ?: Exception("Error checking in book, bookId: $bookId"))
        ) else
            Result.success(bookId)
    }

    fun transferCheckedOutBookFromUserToUser(
        book: Book,
        fromUser: User,
        toUser: User
    ): Result<Book> {
        if (toUser.fetchInfoFailureReason() != null)
            return Result.failure(Exception(toUser.fetchInfoFailureReason()))
        if (fromUser.fetchInfoFailureReason() != null)
            return Result.failure(Exception(fromUser.fetchInfoFailureReason()))
        if (book.isBookFromPublicLibrary) {
            // Check if the fromUser can transfer the Book
            if (!isKnownUserId(fromUser.id()))
                return Result.failure(IllegalArgumentException("fromUser is not known, fromUserId: " + fromUser.id()))
            if (!fromUser.accountInfo()
                    .isAccountInGoodStanding()
            ) return Result.failure(IllegalArgumentException("fromUser Account is not in good standing, fromUserId: " + fromUser.id()))

            // Check if receiving User can check out Book
            if (!isKnownUserId(toUser.id()))
                return Result.failure(IllegalArgumentException("toUser is not known, toUser: " + toUser.id()))
            if (!toUser.accountInfo().isAccountInGoodStanding())
                return Result.failure(IllegalArgumentException("toUser Account is not in good standing, toUser: " + toUser.id()))
            if (toUser.hasReachedMaxAmountOfAcceptedPublicLibraryBooks())
                return Result.failure(IllegalArgumentException("toUser has reached max number of accepted Public Library Books, toUser: " + toUser.id()))
        }
        val returnedBookResult = _checkInBookIdFromUserId(book.id(), fromUser.id())
        if (returnedBookResult.isFailure) return Result.failure(returnedBookResult.exceptionOrNull()
            ?: Exception("Error checking in book, bookId: " + book.id()))
        val checkedOutBookIdResult = _checkOutBookIdToUserId(book.id(), toUser.id())
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
            Result.success(registeredUserIdToCheckedOutBookIdMap[userId] ?: ArrayList<UUID2<Book>>())
    }

    fun calculateAvailableBookIdToCountOfAvailableBooksMap(): Result<java.util.HashMap<UUID2<Book>, Long>> { // todo change to MutableMap
        val availableBookIdToNumBooksAvailableMap: java.util.HashMap<UUID2<Book>, Long> =
            java.util.HashMap<UUID2<Book>, Long>()

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
    fun registerUser(userId: UUID2<User>): Result<UUID2<User>> {
        return upsertUserId(userId)
    }

    fun isKnownBook(book: Book): Boolean {
        return isKnownBookId(book.id())
    }

    fun isKnownBookId(bookId: UUID2<Book>): Boolean {
        return bookIdToNumBooksAvailableMap.containsKey(bookId)
    }

    fun isKnownUser(user: User): Boolean {
        return isKnownUserId(user.id())
    }

    fun isKnownUserId(userId: UUID2<User>?): Boolean {
        return registeredUserIdToCheckedOutBookIdMap.containsKey(userId)
    }

    fun isBookAvailableToCheckout(book: Book): Boolean {
        return isBookIdAvailableToCheckout(book.id())
    }

    fun isBookIdAvailableToCheckout(bookId: UUID2<Book>): Boolean {
        return bookIdToNumBooksAvailableMap[bookId]?.let { numBooksAvailable ->
            numBooksAvailable > 0
        } ?: false
    }

    fun isBookCheckedOutByUser(book: Book, user: User): Boolean {
        return isBookIdCheckedOutByUserId(book.id(), user.id())
    }

    fun isBookIdCheckedOutByUserId(bookId: UUID2<Book>, userId: UUID2<User>): Boolean {
        return registeredUserIdToCheckedOutBookIdMap[userId]?.contains(bookId)
            ?: false
    }

    fun isBookCheckedOutByAnyUser(book: Book): Boolean {
        return isBookIdCheckedOutByAnyUser(book.id())
    }

    fun isBookIdCheckedOutByAnyUser(bookId: UUID2<Book>): Boolean {
        return registeredUserIdToCheckedOutBookIdMap.values
            .stream()
            .anyMatch { bookIds -> bookIds.contains(bookId) }
    }

    fun findUserIdOfCheckedOutBookId(bookId: UUID2<Book>): Result<UUID2<User>> {
        if (!isBookIdCheckedOutByAnyUser(bookId)) return Result.failure(IllegalArgumentException("Book is not checked out by any User, bookId: $bookId"))

        for (userId in registeredUserIdToCheckedOutBookIdMap.keys) {
            if (isBookIdCheckedOutByUserId(bookId, userId)) return Result.success(userId)
        }

        return Result.failure(IllegalArgumentException("Book is not checked out by any User, bookId: $bookId"))
    }

    fun findUserIdOfCheckedOutBook(book: Book): Result<UUID2<User>> {
        return findUserIdOfCheckedOutBookId(book.id())
    }

    // Convenience method - Called from PrivateLibrary class ONLY
    fun addPrivateBookIdToInventory(bookId: UUID2<Book>, quantity: Int): Result<UUID2<Book>> {
        return addBookIdToInventory(bookId, quantity)
    }

    fun removeTransferringBookFromInventory(transferringBook: Book): Result<UUID2<Book>> {
        return removeBookIdFromInventory(transferringBook.id(), 1)
    }

    fun addTransferringBookToInventory(transferringBook: Book): Result<UUID2<Book>> {
        return addBookIdToInventory(transferringBook.id(), 1)
    }

    /////////////////////////////////////////
    // Published Testing Helper Methods    //
    /////////////////////////////////////////
    // Intention revealing method
    fun addTestBook(bookId: UUID2<Book>, quantity: Int): Result<UUID2<Book>> {
        return addBookIdToInventory(bookId, quantity)
    }

    protected fun upsertTestUser(userId: UUID2<User>): Result<UUID2<User>> {
        return upsertUserId(userId)
    }

    //////////////////////////////
    // Private Helper Functions //
    //////////////////////////////

    private fun _checkInBookIdFromUserId(bookId: UUID2<Book>, userId: UUID2<User>): Result<Unit> {
        return try {
            addBookIdToInventory(bookId, 1)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun _checkOutBookIdToUserId(bookId: UUID2<Book>, userId: UUID2<User>): Result<Unit> {
        return if (!isBookIdAvailableToCheckout(bookId)) Result.failure(IllegalArgumentException("Book is not in inventory, bookId: $bookId")) else try {
            removeBookIdFromInventory(bookId, 1)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun addBookIdToInventory(bookId: UUID2<Book>, quantityToAdd: Int): Result<UUID2<Book>> { // todo change quantity to Long
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

    private fun addBookToInventory(book: Book, quantity: Int): Result<Book> {
        val addedUUID2Book: Result<UUID2<Book>> = addBookIdToInventory(book.id(), quantity)
        return if (addedUUID2Book.isFailure) {
            Result.failure(addedUUID2Book.exceptionOrNull() ?: Exception("Error adding book to inventory, book: $book"))
        } else Result.success(book)
    }

    private fun removeBookIdFromInventory(bookId: UUID2<Book>, quantityToRemove: Int): Result<UUID2<Book>> {
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

    private fun removeBookFromInventory(book: Book, quantity: Int): Result<Book> {
        val removedUUID2Book: Result<UUID2<Book>> = removeBookIdFromInventory(book.id(), quantity)

        return if (removedUUID2Book.isFailure) Result.failure(removedUUID2Book.exceptionOrNull()
            ?: Exception("Error removing book from inventory, book: $book"))
        else
            Result.success(book)
    }

    private fun addBookIdToRegisteredUser(bookId: UUID2<Book>, userId: UUID2<User>): Result<UUID2<Book>> {
        if (!isKnownBookId(bookId))
            return Result.failure(IllegalArgumentException("bookId is not known, id: $bookId"))
        if (!isKnownUserId(userId))
            return Result.failure(IllegalArgumentException("userId is not known, id: $userId"))
        if (isBookIdCheckedOutByUserId(bookId, userId))
            return Result.failure(IllegalArgumentException("Book is already checked out by user, bookId: $bookId, userId: $userId"))

        try {
            if (registeredUserIdToCheckedOutBookIdMap.containsKey(userId)) {
                registeredUserIdToCheckedOutBookIdMap[userId]?.add(bookId)
            } else {
                registeredUserIdToCheckedOutBookIdMap[userId] = arrayListOf(bookId)
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(bookId)
    }

    private fun addBookToUser(book: Book, user: User): Result<Book> {
        val addBookToUserResult: Result<UUID2<Book>> = addBookIdToRegisteredUser(book.id(), user.id())

        return if (addBookToUserResult.isFailure) Result.failure(addBookToUserResult.exceptionOrNull()
                ?: Exception("Error adding book to user, book: $book, user: $user"))
        else
            Result.success(book)
    }

    private fun removeBookIdFromRegisteredUserId(bookId: UUID2<Book>, userId: UUID2<User>): Result<UUID2<Book>> {
        if (!isKnownBookId(bookId))
            return Result.failure(IllegalArgumentException("bookId is not known, bookId: $bookId"))
        if (!isKnownUserId(userId))
            return Result.failure(IllegalArgumentException("userId is not known, userId: $userId"))
        if (!isBookIdCheckedOutByUserId(bookId, userId))
            return Result.failure(IllegalArgumentException("Book is not checked out by User, bookId: $bookId, userId: $userId"))

        try {
            //todo reduce count instead of remove? Can someone check out multiple copies of the same book?
            registeredUserIdToCheckedOutBookIdMap[userId]
                ?.remove(bookId)
                ?: return Result.failure(Exception("Error removing book from user, bookId: $bookId, userId: $userId"))
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(bookId)
    }

    private fun removeBookFromUser(book: Book, user: User): Result<Book> {
        val removedBookResult: Result<UUID2<Book>> = removeBookIdFromRegisteredUserId(book.id(), user.id())

        return if (removedBookResult.isFailure) Result.failure(removedBookResult.exceptionOrNull()
            ?: Exception("Error removing book from user, book: $book, user: $user"))
        else
            Result.success(book)
    }

    private fun insertUserId(userId: UUID2<User>): Result<UUID2<User>> {
        if (isKnownUserId(userId)) return Result.failure(IllegalArgumentException("userId is already known"))

        try {
            registeredUserIdToCheckedOutBookIdMap[userId] = ArrayList<UUID2<Book>>()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(userId)
    }

    private fun upsertUserId(userId: UUID2<User>): Result<UUID2<User>> {
        return if (isKnownUserId(userId))
            Result.success(userId)
        else
            insertUserId(userId)
    }

    private fun removeUserId(userId: UUID2<User>): Result<UUID2<User>> {
        if (!isKnownUserId(userId)) return Result.failure(IllegalArgumentException("userId is not known, userId: $userId"))

        try {
            registeredUserIdToCheckedOutBookIdMap.remove(userId)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(userId)
    }

    ///////////////////////////
    // ToInfo implementation //
    ///////////////////////////
    // note: currently no DB or API for UserInfo (so no .ToInfoEntity() or .ToInfoDTO())
    override fun toDeepCopyDomainInfo(): LibraryInfo {
        // Note: *MUST* return a deep copy
        val libraryInfoDeepCopy = LibraryInfo(id(), name)

        // Deep copy the bookIdToNumBooksAvailableMap
        libraryInfoDeepCopy.bookIdToNumBooksAvailableMap.putAll(bookIdToNumBooksAvailableMap)

        // Deep copy the userIdToCheckedOutBookMap
        for ((key, value) in registeredUserIdToCheckedOutBookIdMap.entries) {
            libraryInfoDeepCopy.registeredUserIdToCheckedOutBookIdMap[key] = ArrayList<UUID2<Book>>(value)
        }

        return libraryInfoDeepCopy
    }
}
