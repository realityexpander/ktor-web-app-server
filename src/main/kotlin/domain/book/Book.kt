package domain.book


import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.Context
import domain.book.data.BookInfo
import domain.book.data.BookInfoRepo
import domain.book.data.local.EntityBookInfo
import domain.book.data.network.DTOBookInfo
import domain.common.Role
import domain.library.Library
import domain.library.PrivateLibrary
import domain.library.data.LibraryInfo
import domain.user.User

/**
 * Book Role Object - Only interacts with its own repository, Context, and other Role Objects<br></br>
 * <br></br>
 * Note: Use of **@Nullable** for **sourceLibrary** indicates to *"use default value."*<br></br>
 * Look at **`Book.pickSourceLibrary()`** for more information.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

class Book : Role<BookInfo>, IUUID2 {
    private val repo: BookInfoRepo
    private val sourceLibrary: Library // Book's source Library Role Object - owns this Book.

    constructor(
        info: BookInfo?,
        sourceLibrary: Library?,
        context: Context
    ) : super(info, context) {
        repo = context.bookInfoRepo
        this.sourceLibrary = pickSourceLibrary(sourceLibrary, id(), context)

        context.log.d(this, "Book (" + id() + ") created from Info")
    }

    constructor(
        id: UUID2<Book>,
        info: BookInfo?,
        sourceLibrary: Library?,
        context: Context
    ) : super(id, info, context) {
        repo = context.bookInfoRepo
        this.sourceLibrary = pickSourceLibrary(sourceLibrary, id, context)

        context.log.d(this, "Book (" + id() + ") created using id with no Info")
    }

    constructor(
        bookInfoJson: String,
        clazz: Class<BookInfo>,
        sourceLibrary: Library?,
        context: Context
    ) : super(bookInfoJson, clazz, context) {
        repo = context.bookInfoRepo
        this.sourceLibrary = pickSourceLibrary(sourceLibrary, id(), context)

        context.log.d(this, "Book (" + id() + ") created from JSON using class:" + clazz.name)
    }

    constructor(
        id: UUID2<Book>,
        sourceLibrary: Library?,
        context: Context
    ) : super(id, context) {
        repo = context.bookInfoRepo
        this.sourceLibrary = pickSourceLibrary(sourceLibrary, id, context)

        context.log.d(this, "Book (" + id() + ") created using id with no Info")
    }

    constructor(bookInfoJson: String, sourceLibrary: Library?, context: Context) : this(
        bookInfoJson,
        BookInfo::class.java,
        sourceLibrary,
        context
    )

    constructor(bookInfoJson: String, context: Context) : this(bookInfoJson, BookInfo::class.java, null, context)

    /////////////////////////////////////////
    // Entity 🡒 Domain 🡐 DTO        //
    // - Converters to keep DB/API layer   //
    //   separate from Domain layer        //
    /////////////////////////////////////////

    constructor(bookInfoDTO: DTOBookInfo, sourceLibrary: Library?, context: Context) : this(
        BookInfo(bookInfoDTO),
        sourceLibrary,
        context
    )

    constructor(bookInfoEntity: EntityBookInfo, sourceLibrary: Library, context: Context) : this(
        BookInfo(bookInfoEntity),
        sourceLibrary,
        context
    )

    ////////////////////////
    // Published Getters  //
    ////////////////////////

    // Convenience method to get the Type-safe id from the Class
    override fun id(): UUID2<Book> {
        @Suppress("UNCHECKED_CAST")
        return super.id as UUID2<Book>
    }

    fun sourceLibrary(): Library {
        return sourceLibrary
    }

    override fun toString(): String {
        return this.info()?.toPrettyJson(this.context) ?: "Book (null)"
    }

    /////////////////////////////////////
    // IRole/UUID2 Required Overrides  //
    /////////////////////////////////////

    override fun fetchInfoResult(): Result<BookInfo> {
        // context.log.d(this,"Book (" + this.id.toString() + ") - fetchInfoResult"); // LEAVE for debugging
        @Suppress("UNCHECKED_CAST")
        return repo.fetchBookInfo(id as UUID2<Book>)
    }

    override fun updateInfo(updatedInfo: BookInfo): Result<BookInfo> {
        // context.log.d(this,"Book (" + this.id.toString() + ") - updateInfo"); // LEAVE for debugging

        // Optimistically Update the Cached Book
        super.updateFetchInfoResult(Result.success(updatedInfo))

        // Update the Repo
        return repo.updateBookInfo(updatedInfo)
    }

    override fun uuid2TypeStr(): String {
        return UUID2.calcUUID2TypeStr(this.javaClass)
    }

    val isBookFromPrivateLibrary: Boolean
        get() = sourceLibrary is PrivateLibrary

    val isBookFromPublicLibrary: Boolean
        get() = !isBookFromPrivateLibrary

    fun transferToLibrary(library: Library): Result<Book> {
        if (sourceLibrary === library) {
            return Result.success(this)
        }

        if (sourceLibrary.isBookCheckedOutByAnyUser(this)) {
            val userResult: Result<User> = sourceLibrary.findUserOfCheckedOutBook(this)
            if (userResult.isFailure) return Result.failure(userResult.exceptionOrNull() ?: Exception("Unknown Error"))
            val user: User = userResult.getOrNull()
                ?: return Result.failure(Exception("Unknown Error"))

            return library.transferCheckedOutBookSourceLibraryToThisLibrary(this, user)
        }

        // Default to attempting to transfer the Book
        return library.transferBookSourceLibraryToThisLibrary(this)
    }

    fun updateAuthor(author: String): Result<BookInfo> {
        val updatedInfo: BookInfo = this.info()?.withAuthor(author)
            ?: return Result.failure(Exception("BookInfo is null"))
        return updateInfo(updatedInfo) // delegate to Info Object
    }

    fun updateTitle(title: String): Result<BookInfo> {
        val updatedInfo: BookInfo = this.info()?.withTitle(title)
            ?: return Result.failure(Exception("BookInfo is null"))
        return updateInfo(updatedInfo) // delegate to Info Object
    }

    fun updateDescription(description: String): Result<BookInfo> {
        val updatedInfo: BookInfo = this.info()?.withDescription(description)
            ?: return Result.failure(Exception("BookInfo is null"))
        return updateInfo(updatedInfo) // delegate to Info Object
    }

    // Role Role-specific business logic in a Role Object.
    fun updateSourceLibrary(sourceLibrary: Library): Result<Book> {
        // NOTE: This method is primarily used by the Library Role Object when moving a Book from one Library
        //   to another Library.
        // This info is *NOT* saved with the Book's BookInfo, as it only applies to the Book Role Object.
        // - Shows example of Role-specific business logic in a Role Object that is not saved in the Info Object.
        // - ie: sourceLibrary only exists in this Book Role Object as `BookInfo` does NOT have a `sourceLibrary` field.
        val updatedBook: Book = Book(id(), this.info(), sourceLibrary, this.context)
        return Result.success(updatedBook)
    }

    ////////////////////////////
    // Private Helper Methods //
    ////////////////////////////

    private fun pickSourceLibrary(
        sourceLibrary: Library?,
        bookId: UUID2<Book>,
        context: Context
    ): Library {
        // If a sourceLibrary was not provided, create a new ORPHAN PrivateLibrary for this Book.
        if (sourceLibrary == null) {
            // Create a new ORPHAN PrivateLibrary just for this one Book
            val privateLibrary: Library = PrivateLibrary(bookId, true, context)
            context.libraryInfoRepo
                .upsertLibraryInfo(
                    LibraryInfo(
                        privateLibrary.id(),
                        "ORPHAN Private Library only for one Book, BookId: " + bookId.uuid()
                    )
                )

            // Add this Book to the new ORPHAN PrivateLibrary
            @Suppress("UNUSED_VARIABLE")
            val ignoreThisResult: Result<UUID2<Book>> =
                privateLibrary.info()?.addPrivateBookIdToInventory(bookId, 1)
                    ?: throw Exception("Error adding Book to PrivateLibrary")

            return privateLibrary
        }

        return sourceLibrary
    }

    companion object {

        /////////////////////////
        // Static constructors //
        /////////////////////////

        fun fetchBook(
            uuid2: UUID2<Book>,
            sourceLibrary: Library?,  // `null` means use default (ie: Orphan PrivateLibrary)
            context: Context
        ): Result<Book> {
            val repo: BookInfoRepo = context.bookInfoRepo
            val infoResult: Result<BookInfo> = repo.fetchBookInfo(uuid2)
            if (infoResult.isFailure) return Result.failure(infoResult.exceptionOrNull()
                ?: Exception("Error fetching BookInfo, BookId: " + uuid2.uuid()))

            val info: BookInfo = infoResult.getOrNull()
                ?: return Result.failure(Exception("Error fetching BookInfo, BookId: " + uuid2.uuid()))

            return Result.success(Book(info, sourceLibrary, context))
        }

        fun fetchBook(
            uuid2: UUID2<Book>,
            context: Context
        ): Result<Book> {
            return fetchBook(uuid2, null, context)
        }
    }
}