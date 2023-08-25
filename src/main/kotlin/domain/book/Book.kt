package domain.book


import com.realityexpander.authRepo
import com.realityexpander.common.data.network.PairSerializer
import com.realityexpander.common.data.network.ResultSerializer
import com.realityexpander.common.data.network.SerializedResult
import com.realityexpander.jsonConfig
import com.realityexpander.libraryAppContext
import common.log.Log
import common.uuid2.IUUID2
import common.uuid2.UUID2
import common.uuid2.UUID2Result
import domain.Context
import domain.Context.Companion.createDefaultTestInMemoryContext
import domain.Context.Companion.gsonConfig
import domain.book.data.BookInfo
import domain.book.data.IBookInfoRepo
import domain.book.data.local.BookInfoEntity
import domain.book.data.network.BookInfoDTO
import domain.common.Role
import domain.library.Library
import domain.library.PrivateLibrary
import domain.library.data.LibraryInfo
import domain.user.User
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import util.JsonString

/**
 * Book Role
 *
 * Book is a Role Object that represents a Book in the LibraryApp domain.
 *
 * * Only interacts with its own repository, Context, and other Role Objects in the Domain layer.
 *
 * Note: Use of **@Nullable** for **sourceLibrary** indicates to *"use default value."*
 *
 * See **`Book.pickSourceLibrary()`** for more information.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

@Serializable(with = BookSerializer::class)  // for kotlinx.serialization
class Book : Role<BookInfo>, IUUID2 {
    private val repo: IBookInfoRepo
    private val sourceLibrary: Library // Book's source Library Role Object - owns this Book.

    constructor(
        info: BookInfo?,
        sourceLibrary: Library?,
        context: Context
    ) : super(info, context) {
        repo = context.bookInfoRepo
        this.sourceLibrary = pickSourceLibrary(sourceLibrary, id(), context)

        context.log.d("Book<init>", "Book (" + id() + ") created from Info")
    }
    constructor(
        id: UUID2<Book>,
        info: BookInfo?,
        sourceLibrary: Library?,
        context: Context
    ) : super(id, info, context) {
        repo = context.bookInfoRepo
        this.sourceLibrary = pickSourceLibrary(sourceLibrary, id, context)

        context.log.d("Book<init>", "Book (" + id() + ") created using id with no Info")
    }
    constructor(
        bookInfoJson: JsonString,
        clazz: Class<BookInfo>,
        sourceLibrary: Library?,
        context: Context
    ) : super(bookInfoJson, clazz, context) {
        repo = context.bookInfoRepo
        this.sourceLibrary = pickSourceLibrary(sourceLibrary, id(), context)

        context.log.d("Book<init>", "Book (" + id() + ") created from JSON using class:" + clazz.name)
    }
    constructor(
        id: UUID2<Book>,
        sourceLibrary: Library?,
        context: Context
    ) : super(id, context) {
        repo = context.bookInfoRepo
        this.sourceLibrary = pickSourceLibrary(sourceLibrary, id, context)

        context.log.d("Book<init>", "Book (" + id() + ") created using id with no Info")
    }
    constructor(bookInfoJson: JsonString, sourceLibrary: Library?, context: Context) : this(
        bookInfoJson,
        BookInfo::class.java,
        sourceLibrary,
        context
    )
    constructor(bookInfoJson: JsonString, context: Context) : this(bookInfoJson, BookInfo::class.java, null, context)
    constructor(id: UUID2<Book>, context: Context) : this(id, null, context)

    /////////////////////////////////////////
    // Entity 🡒 Domain 🡐 DTO        //
    // - Converters to keep DB/API layer   //
    //   separate from Domain layer        //
    /////////////////////////////////////////

    constructor(bookInfoDTO: BookInfoDTO, sourceLibrary: Library?, context: Context) : this(  // sourceLibrary is nullable
        BookInfo(bookInfoDTO),
        sourceLibrary,
        context
    )

    constructor(bookInfoEntity: BookInfoEntity, sourceLibrary: Library, context: Context) : this(
        BookInfo(bookInfoEntity),
        sourceLibrary,
        context
    )

    ////////////////////////////////
    // Published Simple Getters   //
    ////////////////////////////////

    // Convenience method to get the Type-safe id from the Class
    override fun id(): UUID2<Book> {
        @Suppress("UNCHECKED_CAST")
        return super.id as UUID2<Book>
    }

    fun sourceLibrary(): Library {
        return sourceLibrary
    }

    override fun toString(): String {
        return this.info.get()?.toPrettyJson(this.context)
            ?: ("Book (id: " + this.id.toString() + ") : info is null")
    }

    /////////////////////////////////////
    // Role/UUID2 Required Overrides   //
    /////////////////////////////////////

    override suspend fun fetchInfoResult(): Result<BookInfo> {
        // context.log.d(this,"Book (" + this.id.toString() + ") - fetchInfoResult"); // LEAVE for debugging
        @Suppress("UNCHECKED_CAST")
        return repo.fetchBookInfo(id as UUID2<Book>)
    }

    override suspend fun updateInfo(updatedInfo: BookInfo): Result<BookInfo> {
        // context.log.d(this,"Book (" + this.id.toString() + ") - updateInfo"); // LEAVE for debugging

        // Optimistically Update the Cached Book
        super.updateFetchInfoResult(Result.success(updatedInfo))

        // Update the Repo
        return repo.updateBookInfo(updatedInfo)
    }

    override fun uuid2TypeStr(): String {
        return UUID2.calcUUID2TypeStr(this.javaClass)
    }

    ////////////////////////
    // Business Logic     //
    ////////////////////////

    val isBookFromPrivateLibrary: Boolean
        get() = sourceLibrary is PrivateLibrary

    val isBookFromPublicLibrary: Boolean
        get() = !isBookFromPrivateLibrary

    suspend fun transferToLibrary(library: Library): Result<Book> {
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

    suspend fun updateAuthor(author: String): Result<BookInfo> {
        val updatedInfo: BookInfo = this.info()?.withAuthor(author)
            ?: return Result.failure(Exception("BookInfo is null"))
        return updateInfo(updatedInfo) // delegate to Info Object
    }

    suspend fun updateTitle(title: String): Result<BookInfo> {
        val updatedInfo: BookInfo = this.info()?.withTitle(title)
            ?: return Result.failure(Exception("BookInfo is null"))
        return updateInfo(updatedInfo) // delegate to Info Object
    }

    suspend fun updateDescription(description: String): Result<BookInfo> {
        val updatedInfo: BookInfo = this.info()?.withDescription(description)
            ?: return Result.failure(Exception("BookInfo is null"))
        return updateInfo(updatedInfo) // delegate to Info Object
    }

    /**
     * NOTE: `updateSourceLibrary()` is primarily used by the `Library` Role when transferring a `Book`'s source
     *       library from one `Library` to another `Library`.
     *
     *  This info is *NOT* saved with the Book's BookInfo, as it only applies to the Book Role Object.
     *
     *  * Illustrates example of Role-specific business logic in that is not saved in it's **`Info`** Object.
     *  * ie: **`sourceLibrary`** only exists in this `Book` Role object because **`BookInfo`** does _NOT_ have
     *    a **`sourceLibrary`** field, and this relationship only exists in the Domain layer.
     */
    suspend fun updateSourceLibrary(sourceLibrary: Library): Result<Book> {
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

            return runBlocking {
                // Create a new ORPHAN PrivateLibrary just for this one Book
                val privateLibrary: Library = PrivateLibrary(bookId, true, context)

                // Create a new ORPHAN PrivateLibrary in the Repo for this one Book
                val upsertOrphanLibraryInfoResult = context.libraryInfoRepo
                    .upsertLibraryInfo(
                        LibraryInfo(
                            privateLibrary.id(),
                            "ORPHAN Private Library only for one Book, BookId: " + bookId.uuid()
                        )
                    )
                if (upsertOrphanLibraryInfoResult.isFailure) throw Exception("Error adding Book to PrivateLibrary")
                val orphanLibraryInfo = upsertOrphanLibraryInfoResult.getOrNull()
                    ?: throw Exception("Error adding Book to PrivateLibrary")

                val result = orphanLibraryInfo.addPrivateBookIdToInventory(bookId, 1)
                if(result.isFailure) throw Exception("Error adding Book to PrivateLibrary")

                privateLibrary
            }
        }

        return sourceLibrary
    }

    companion object {

        /////////////////////////
        // Static constructors //
        /////////////////////////

        suspend fun fetchBook(
            uuid2: UUID2<Book>,
            sourceLibrary: Library?,  // `null` means use default (ie: Orphan PrivateLibrary)
            context: Context
        ): Result<Book> {
            val bookInfoResult = context.bookInfoRepo.fetchBookInfo(uuid2)
            if (bookInfoResult.isFailure) return Result.failure(bookInfoResult.exceptionOrNull()
                ?: Exception("Error fetching BookInfo, BookId: " + uuid2.uuid()))

            val bookInfo = bookInfoResult.getOrNull()
                ?: return Result.failure(Exception("Error fetching BookInfo, BookId: " + uuid2.uuid()))

            return Result.success(Book(bookInfo, sourceLibrary, context))
        }

        suspend fun fetchBook(
            uuid2: UUID2<Book>,
            context: Context
        ): Result<Book> {
            return fetchBook(uuid2, null, context)
        }
    }
}

object BookSerializer : KSerializer<Book> {
    override val descriptor = PrimitiveSerialDescriptor("Book", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Book {
        libraryAppContext.log.e(this,"WARNING: BookSerializer.deserialize() called - DO NOT USE IN PRODUCTION." +
                "Should only be used for debugging/testing. Use the Book constructor instead.")

        @Suppress("UNCHECKED_CAST")
        return Book(UUID2Result.serializer().deserialize(decoder).uuid2 as UUID2<Book>,
            null,
            libraryAppContext
        )
    }

    override fun serialize(encoder: Encoder, value: Book) {
        encoder.encodeSerializableValue( // unlike `encodeString`, this will NOT add quotes around the string
            UUID2Result.serializer(),
            UUID2Result(value.id())
        )
    }
}