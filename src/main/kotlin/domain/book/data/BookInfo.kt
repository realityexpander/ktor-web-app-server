package domain.book.data

import common.HumanDate
import common.uuid2.UUID2
import common.uuid2.UUID2.Companion.fromUUIDStrToUUID2
import domain.book.Book
import domain.book.data.local.BookInfoEntity
import domain.book.data.network.BookInfoDTO
import domain.common.data.Model
import domain.common.data.info.DomainInfo
import kotlinx.serialization.Serializable
import java.util.*

/**
 * BookInfo
 *
 * BookInfo is a mutable class that holds the information represented by the Book Role object.
 * Contains information about a book like title, author, description, etc.
 *
 * This class uses domain-specific language to modify information for the BookInfo class.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

@Serializable // for kotlinx.serialization
class BookInfo(
    override val id: UUID2<Book> = UUID2.randomUUID2<Book>(),  // Note: This is a UUID2<Book> not a UUID2<BookInfo>
    val title: String,
    val author: String,
    val description: String,
    val creationTimeMillis: Long = System.currentTimeMillis(),
    val lastModifiedTimeMillis: Long = creationTimeMillis,
    val isDeleted: Boolean = false
) : DomainInfo(id),
    Model.ToEntityInfo<BookInfoEntity>,
    Model.ToDTOInfo<BookInfoDTO>,
    Model.ToDomainInfoDeepCopy<BookInfo>
{
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
    constructor(uuidStr: String, title: String, author: String, description: String) : this(
        uuidStr.fromUUIDStrToUUID2(),
        title,
        author,
        description,
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

    ////////////////////////////////////////////////////////
    // DomainInfo objects Must:                           //
    // - Accept both `DTO.BookInfo` and `Entity.BookInfo` //
    // - Convert to Domain.BookInfo                       //
    ////////////////////////////////////////////////////////

    constructor(bookInfoDTO: BookInfoDTO) : this(
        UUID2<Book>(bookInfoDTO.id().uuid(), Book::class.java),  // change id to domain UUID2<Book> type
        bookInfoDTO.title,
        bookInfoDTO.author,
        bookInfoDTO.description,
        bookInfoDTO.creationTimeMillis,
        bookInfoDTO.lastModifiedTimeMillis,
        bookInfoDTO.isDeleted
    ) {
        // Converts from InfoDTO to DomainInfo

        // Basic Validation = Domain decides what to include from the DTO
        // - must be done after construction
        validateBookInfo()
    }

    constructor(bookInfoEntity: BookInfoEntity) : this(
        UUID2<Book>(bookInfoEntity.id().uuid(), Book::class.java),  // change id to domain UUID2<Book> type
        bookInfoEntity.title,
        bookInfoEntity.author,
        bookInfoEntity.description,
        bookInfoEntity.creationTimeMillis,
        bookInfoEntity.lastModifiedTimeMillis,
        bookInfoEntity.isDeleted
    ) {
        // Converts from InfoEntity to DomainInfo

        // Basic Validation - Domain decides what to include from the Entities
        // - must be done after construction
        validateBookInfo()
    }

    private fun validateBookInfo() {
        require(title.length <= 100) { "BookInfo.title cannot be longer than 100 characters" }
        require(author.length <= 100) { "BookInfo.author cannot be longer than 100 characters" }
        require(description.length <= 1000) { "BookInfo.description cannot be longer than 1000 characters" }

        // todo add enhanced validation here, or in the application layer
    }

    ////////////////////////////////
    // Published Getters          //  // note: no setters, all changes are made through business logic methods.
    ////////////////////////////////

    // Convenience method to get the Type-safe id from the Class
    override fun id(): UUID2<Book> {
        return this.id
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

    override fun toInfoDTO(): BookInfoDTO {
        return BookInfoDTO(this)
    }

    override fun toInfoEntity(): BookInfoEntity {
        return BookInfoEntity(this)
    }

    /////////////////////////////
    // ToDomain implementation //
    /////////////////////////////

    override fun toDomainInfoDeepCopy(): BookInfo {
        // shallow copy OK here bc its flat
        return BookInfo(this)
    }
}
