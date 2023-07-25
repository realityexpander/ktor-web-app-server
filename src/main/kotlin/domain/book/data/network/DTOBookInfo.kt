package domain.book.data.network

import com.realityexpander.common.data.local.JsonFileDatabase
import com.realityexpander.jsonConfig
import common.HumanDate
import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.Context
import domain.book.Book
import domain.book.data.BookInfo
import domain.common.data.HasId
import domain.common.data.Model
import domain.common.data.info.Info
import domain.common.data.info.network.DTOInfo
import kotlinx.serialization.Serializable

/**
 * DTOBookInfo
 *
 * A Data Transfer Object (DTO) that is used to transfer data between the API and the Domain layer.
 *
 * It's a "Dumb" Data Transfer Object for BookInfo
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

@Serializable
class DTOBookInfo(
    override val id: UUID2<Book> = UUID2.randomUUID2<Book>(),
    val title: String,
    val author: String,
    val description: String,
    val extraFieldToShowThisIsADTO: String = "This is a DTO",
    val creationTimeMillis: Long = 0,
    val lastModifiedTimeMillis: Long = 0,
    val isDeleted: Boolean = false
) : DTOInfo(id),
    Model.ToDomainInfo<BookInfo>,
    Model.ToDomainInfo.HasToDomainInfoDeepCopy<BookInfo>,
    Info.ToInfo<DTOBookInfo>,
    Info.HasToDeepCopyInfo<DTOBookInfo>
{
    constructor(json: String, context: Context) : this(jsonConfig.decodeFromString<DTOBookInfo>(json))
    constructor() : this(UUID2.randomUUID2<Book>(), "default", "default", "default")

    /////////////////////////////////////////////////////////////////////
    // EntityInfo <-> DomainInfo conversion                            //
    // Note: Intentionally DON'T accept `EntityInfo.EntityBookInfo`    //
    //   - to keep DB layer separate from API layer                    //
    /////////////////////////////////////////////////////////////////////

    constructor(bookInfo: DTOBookInfo) : this(
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
        "Extra info added during creation of DTOInfo.DTOBookInfo",
        bookInfo.creationTimeMillis,
        bookInfo.lastModifiedTimeMillis,
        bookInfo.isDeleted
    )
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

    ///////////////////////////////////////////
    // DTOs don't have any business logic    //
    // - All "Info" changes are done in the  //
    //   domain layer.                       //
    ///////////////////////////////////////////

    /////////////////////////////////
    // ToDomainInfo implementation //
    /////////////////////////////////

    override fun toDomainInfoDeepCopy(): BookInfo {
        // note: implement deep copy (if class is not flat.)
        return BookInfo(this)
    }

    /////////////////////////////
    // ToInfo implementation   //
    /////////////////////////////

    override fun toDeepCopyInfo(): DTOBookInfo {
        // note: implement deep copy (if class is not flat.)
        return DTOBookInfo(this)
    }
}
