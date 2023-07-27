package domain.book.data.local

import com.realityexpander.jsonConfig
import common.HumanDate
import common.uuid2.UUID2
import domain.book.Book
import domain.book.data.BookInfo
import domain.common.data.Model
import domain.common.data.info.Info
import domain.common.data.info.local.InfoEntity
import kotlinx.serialization.Serializable

/**
 * BookInfoEntity
 *
 * Represents a database row for a Book from a local database.
 *
 * A "Dumb" Entity that is used to transfer data between the Domain Layer and the Data Layer.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

@Serializable
class BookInfoEntity(
    override val id: UUID2<Book> = UUID2.randomUUID2<Book>(),
    val title: String,
    val author: String,
    val description: String,
    val extraFieldToShowThisIsAnEntity: String? = "This is an BookInfoEntity", // default value
    val creationTimeMillis: Long = 0,
    val lastModifiedTimeMillis: Long = 0,
    val isDeleted: Boolean = false
) : InfoEntity(id),
    Model.ToDomainInfoDeepCopy<BookInfo>,
    Info.ToInfoDeepCopy<BookInfoEntity>
{
    constructor(json: String) : this(jsonConfig.decodeFromString<BookInfoEntity>(json))

    ////////////////////////////////////////////////////////////
    // InfoDTO <-> DomainInfo conversion                      //
    // Note: Intentionally DON'T accept `InfoDTO.BookInfoDTO` //
    //   - to keep DB layer separate from API layer)          //
    ////////////////////////////////////////////////////////////

    constructor(bookInfo: BookInfoEntity) : this(
        bookInfo.id(),
        bookInfo.title,
        bookInfo.author,
        bookInfo.description,
        bookInfo.extraFieldToShowThisIsAnEntity,
        bookInfo.creationTimeMillis,
        bookInfo.lastModifiedTimeMillis,
        bookInfo.isDeleted
    )
    constructor(bookInfo: BookInfo) : this(
        bookInfo.id(),
        bookInfo.title,
        bookInfo.author,
        bookInfo.description,
        "Extra info added during creation of InfoEntity.BookInfoEntity",
        bookInfo.creationTimeMillis,
        bookInfo.lastModifiedTimeMillis,
        false
    )

    override fun toString(): String {
        return "Book (" + this.id() + ") : " +
                title + " by " + author + ", created=" +
                HumanDate(creationTimeMillis).toDateStr() + ", " +
                "modified=" + HumanDate(lastModifiedTimeMillis).toTimeAgoStr() + ", " +
                "isDeleted=" + isDeleted + ", " +
                description
    }

    // Convenience method to get the Type-safe id from the Class
    override fun id(): UUID2<Book> {
        return this.id
    }

    /**
     * Entities don't have any business logic
     *
     * * All "Info" changes are done in the domain layer using BookInfo published methods.
    **/

    ////////////////////////////////////////////
    // ToDomainInfoDeepCopy implementation //
    ////////////////////////////////////////////

    override fun toDomainInfoDeepCopy(): BookInfo {
        // implement deep copy (if structure is not flat.)
        return BookInfo(this)
    }

    ////////////////////////////////////////
    // ToInfoDeepCopy implementation   //
    ////////////////////////////////////////

    override fun toInfoDeepCopy(): BookInfoEntity {
        // note: implement deep copy (if structure is not flat.)
        return BookInfoEntity(this)
    }
}
