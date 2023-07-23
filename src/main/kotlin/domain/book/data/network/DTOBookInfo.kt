package domain.book.data.network

import com.realityexpander.common.data.local.FileDatabase
import com.realityexpander.jsonConfig
import common.HumanDate
import common.uuid2.UUID2
import domain.Context
import domain.book.Book
import domain.book.data.BookInfo
import domain.common.data.Model
import domain.common.data.info.Info
import domain.common.data.info.network.DTOInfo
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.*

/**
 * DTOBookInfo
 *
 * A Data Transfer Object (DTO) that is used to transfer data between the
 *
 * "Dumb" Data Transfer Object for BookInfo
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

@Serializable
class DTOBookInfo(
    override val id: UUID2<Book> = UUID2(UUID.randomUUID()),
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
    Info.HasToDeepCopyInfo<DTOBookInfo>,
    FileDatabase.HasId<UUID2<Book>>
{

    // todo use kotlinx serialization instead of gson
//    constructor(json: String, context: Context) : this(context.gson.fromJson(json, DTOBookInfo::class.java))
    constructor(json: String, context: Context) : this(jsonConfig.decodeFromString<DTOBookInfo>(json))
    constructor() : this(UUID2<Book>(UUID.randomUUID(), Book::class.java), "", "", "")

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
