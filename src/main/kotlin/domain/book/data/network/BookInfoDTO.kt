package domain.book.data.network

import com.realityexpander.jsonConfig
import common.HumanDate
import common.uuid2.UUID2
import domain.book.Book
import domain.book.data.BookInfo
import domain.common.data.Model
import domain.common.data.info.Info
import domain.common.data.info.network.InfoDTO
import kotlinx.serialization.Serializable

/**
 * BookInfoDTO
 *
 * Represents a database row for a Book from an API.
 *
 * A Data Transfer Object (DTO) that is used to transfer data between the API and the Domain layer.
 *
 * A "Dumb" Data Transfer Object for BookInfo used to transfer data between the Domain Layer
 * and the Data Layer.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

@Serializable
class BookInfoDTO(
    override val id: UUID2<Book> = UUID2.randomUUID2<Book>(),
    val title: String,
    val author: String,
    val description: String,
    val extraFieldToShowThisIsADTO: String = "This is a DTO",
    val creationTimeMillis: Long = 0,
    val lastModifiedTimeMillis: Long = 0,
    val isDeleted: Boolean = false
) : InfoDTO(id),
    Model.ToDomainInfoDeepCopy<BookInfo>,
    Info.ToInfoDeepCopy<BookInfoDTO>
{
    constructor(json: String) : this(jsonConfig.decodeFromString<BookInfoDTO>(json))
    constructor() : this(UUID2.randomUUID2<Book>(), "default", "default", "default")

    /////////////////////////////////////////////////////////////////////
    // InfoEntity <-> DomainInfo conversion                            //
    // Note: Intentionally DON'T accept `InfoEntity.BookInfoEntity`    //
    //   - to keep DB layer separate from API layer                    //
    /////////////////////////////////////////////////////////////////////

    constructor(bookInfo: BookInfoDTO) : this(
        bookInfo.id(),
        bookInfo.title,
        bookInfo.author,
        bookInfo.description,
        bookInfo.extraFieldToShowThisIsADTO,
        bookInfo.creationTimeMillis,
        bookInfo.lastModifiedTimeMillis,
        bookInfo.isDeleted
    )
    constructor(bookInfo: BookInfo) : this(
        bookInfo.id(),
        bookInfo.title,
        bookInfo.author,
        bookInfo.description,
        "Extra info added during creation of InfoDTO.BookInfoDTO",
        bookInfo.creationTimeMillis,
        bookInfo.lastModifiedTimeMillis,
        bookInfo.isDeleted
    )

    ////////////////////////
    // Published Getters  //
    ////////////////////////

    override fun toString(): String {
        return "Book (" + this.id() + ") : " +
                title + " by " + author + ", created=" +
                HumanDate(creationTimeMillis).toDateStr() + ", " +
                "modified=" + HumanDate(lastModifiedTimeMillis).toTimeAgoStr() + ", " +
                "isDeleted=" + isDeleted + ", " +
                extraFieldToShowThisIsADTO + ", " +
                description
    }

    override fun id(): UUID2<Book> {
        return this.id
    }

    /**
     * DTO's don't have any business logic
     *
     * * All "Info" changes are done in the domain layer using BookInfo published methods.
     **/

    ////////////////////////////////////////////
    // ToDomainInfoDeepCopy implementation //
    ////////////////////////////////////////////

    override fun toDomainInfoDeepCopy(): BookInfo {
        // note: implement deep copy (if class is not flat.)
        return BookInfo(this)
    }

    ////////////////////////////////////////
    // ToInfoDeepCopy implementation   //
    ////////////////////////////////////////

    override fun toInfoDeepCopy(): BookInfoDTO {
        // note: implement deep copy (if class is not flat.)
        return BookInfoDTO(this)
    }
}
