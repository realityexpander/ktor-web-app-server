package domain.book.data.network

import common.HumanDate
import common.uuid2.UUID2
import domain.Context
import domain.book.Book
import domain.book.data.BookInfo
import domain.common.data.Model
import domain.common.data.info.Info
import domain.common.data.info.network.DTOInfo

/**
 * DTOBookInfo
 *
 * "Dumb" Data Transfer Object for BookInfo
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

class DTOBookInfo(
    id: UUID2<Book>,
    val title: String,
    val author: String,
    val description: String,
    val extraFieldToShowThisIsADTO: String = "This is a DTO",
    val creationTimeMillis: Long = 0,
    val lastModifiedTimeMillis: Long = 0,
    val isDeleted: Boolean = false
) : DTOInfo(id),
    Model.ToDomainInfo<BookInfo>,
    Model.ToDomainInfo.HasToDeepCopyDomainInfo<BookInfo>,
    Info.ToInfo<DTOBookInfo>,
    Info.HasToDeepCopyInfo<DTOBookInfo>
{
    constructor(json: String, context: Context) : this(context.gson.fromJson(json, DTOBookInfo::class.java))

    /////////////////////////////////////////////////////////////////////
    // EntityInfo <-> DomainInfo conversion                            //
    // Note: Intentionally DON'T accept `EntityInfo.EntityBookInfo`    //
    //   - to keep DB layer separate from API layer                    //
    /////////////////////////////////////////////////////////////////////

    constructor(bookInfo: DTOBookInfo) : this(
        UUID2.fromUUID2(bookInfo.id().toDomainUUID2(), Book::class.java),  // change `UUID2Type` to UUID2<Book>
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

    ///////////////////////////////////////////
    // DTOs don't have any business logic    //
    // - All "Info" changes are done in the  //
    //   domain layer.                       //
    ///////////////////////////////////////////

    /////////////////////////////////
    // ToDomainInfo implementation //
    /////////////////////////////////

    override fun toDeepCopyDomainInfo(): BookInfo {
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
