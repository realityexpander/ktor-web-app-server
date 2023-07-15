package domain.book.data

import common.HumanDate
import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.book.Book
import domain.book.data.local.EntityBookInfo
import domain.book.data.network.DTOBookInfo
import domain.common.data.Model
import domain.common.data.info.DomainInfo
import java.util.*

/**
 * BookInfo - Contains information about a book like title, author, description, etc.
 *
 * This class uses domain-specific language to modify information for the BookInfo class.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */
class BookInfo(
    id: UUID2<Book>,  // Note: This is a UUID2<Book> not a UUID2<BookInfo>
    val title: String,
    val author: String,
    val description: String,
    creationTimeMillis: Long,
    lastModifiedTimeMillis: Long,
    isDeleted: Boolean
) : DomainInfo(id.toDomainUUID2()), Model.ToEntityInfo<EntityBookInfo>, Model.ToDTOInfo<DTOBookInfo>, Model.ToDomainInfo<BookInfo> {
    var creationTimeMillis: Long = 0
    var lastModifiedTimeMillis: Long = 0
    var isDeleted = false

    init {
        this.creationTimeMillis = creationTimeMillis
        this.lastModifiedTimeMillis = lastModifiedTimeMillis
        this.isDeleted = isDeleted
    }

    constructor(
        uuid: UUID,
        title: String,
        author: String,
        description: String,
        creationTimeMillis: Long,
        lastModifiedTimeMillis: Long,
        isDeleted: Boolean
    ) : this(
        UUID2<Book>(uuid, Book::class.java),
        title,
        author,
        description,
        creationTimeMillis,
        lastModifiedTimeMillis,
        isDeleted
    )

    constructor(id: String, title: String, author: String, description: String) : this(
        UUID.fromString(id),
        title,
        author,
        description,
        0,
        0,
        false
    )

    constructor(bookInfo: BookInfo) : this(
        bookInfo.id(),
        bookInfo.title,
        bookInfo.author,
        bookInfo.description,
        bookInfo.creationTimeMillis,
        bookInfo.lastModifiedTimeMillis,
        bookInfo.isDeleted
    )

    constructor(id: UUID) : this(id, "", "", "", 0, 0, false)
    constructor(uuid2: UUID2<IUUID2>) : this(uuid2.uuid(), "", "", "", 0, 0, false)
    constructor(uuid2: UUID2<Book>) : this(uuid2.uuid(), "", "", "", 0, 0, false)

    ////////////////////////////////////////////////////////
    // DomainInfo objects Must:                           //
    // - Accept both `DTO.BookInfo` and `Entity.BookInfo` //
    // - Convert to Domain.BookInfo                       //
    ////////////////////////////////////////////////////////
    constructor(dtoBookInfo: DTOBookInfo) : this(
        UUID2<Book>(dtoBookInfo.id().uuid(), Book::class.java),  // change id to domain UUID2<Book> type
        dtoBookInfo.title,
        dtoBookInfo.author,
        dtoBookInfo.description,
        dtoBookInfo.creationTimeMillis,
        dtoBookInfo.lastModifiedTimeMillis,
        dtoBookInfo.isDeleted
    ) {
        // Converts from DTOInfo to DomainInfo

        // Basic Validation = Domain decides what to include from the DTO
        // - must be done after construction
        validateBookInfo()
    }

    constructor(entityBookInfo: EntityBookInfo) : this(
//        UUID2<Book>(entityBookInfo.id()),  // change to the Domain UUID2 type
        UUID2<Book>(entityBookInfo.id().uuid(), Book::class.java),  // change id to domain UUID2<Book> type
        entityBookInfo.title,
        entityBookInfo.author,
        entityBookInfo.description,
        entityBookInfo.creationTimeMillis,
        entityBookInfo.lastModifiedTimeMillis,
        entityBookInfo.isDeleted
    ) {
        // Converts from EntityInfo to DomainInfo

        // Basic Validation - Domain decides what to include from the Entities
        // - must be done after contruction
        validateBookInfo()
    }

    private fun validateBookInfo() {
        require(title.length <= 100) { "BookInfo.title cannot be longer than 100 characters" }
        require(author.length <= 100) { "BookInfo.author cannot be longer than 100 characters" }
        require(description.length <= 1000) { "BookInfo.description cannot be longer than 1000 characters" }

        // todo add enhanced validation here, or in the application layer
    }

    ////////////////////////////////
    // Published Getters          //
    ////////////////////////////////

    // Convenience method to get the Type-safe id from the Class
    override fun id(): UUID2<Book> {
        return super.id() as UUID2<Book>
    }

    override fun toString(): String {
        return "Book (" + id() + ") : " +
                title + " by " + author + ", created=" +
                HumanDate(creationTimeMillis).toDateStr() + ", " +
                "modified=" + HumanDate(lastModifiedTimeMillis).toTimeAgoStr() + ", " +
                "isDeleted=" + isDeleted + ", " +
                description
    }

    /////////////////////////////////////////////////
    // BookInfo Business Logic Methods             //
    // - All Info manipulation logic is done here. //
    /////////////////////////////////////////////////
    fun withTitle(title: String): BookInfo {
        return BookInfo(id(), title, author, description, creationTimeMillis, System.currentTimeMillis(), isDeleted)
    }

    fun withAuthor(authorName: String): BookInfo {
        return BookInfo(id(), title, authorName, description, creationTimeMillis, System.currentTimeMillis(), isDeleted)
    }

    fun withDescription(description: String): BookInfo {
        return BookInfo(id(), title, author, description, creationTimeMillis, System.currentTimeMillis(), isDeleted)
    }

    /////////////////////////////////////
    // ToEntity / ToDTO implementation //
    /////////////////////////////////////
    override fun toInfoDTO(): DTOBookInfo {
        return DTOBookInfo(this)
    }

    override fun toInfoEntity(): EntityBookInfo {
        return EntityBookInfo(this)
    }

    /////////////////////////////
    // ToDomain implementation //
    /////////////////////////////

    override fun toDeepCopyDomainInfo(): BookInfo {
        // shallow copy OK here bc its flat
        return BookInfo(this)
    }
}
