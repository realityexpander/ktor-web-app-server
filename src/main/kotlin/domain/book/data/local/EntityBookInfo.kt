package domain.book.data.local

import common.HumanDate
import common.uuid2.UUID2
import domain.Context
import domain.book.Book
import domain.book.data.BookInfo
import domain.common.data.Model
import domain.common.data.info.Info
import domain.common.data.info.local.EntityInfo
import domain.library.Library
import kotlinx.serialization.Contextual

/**
 * EntityBookInfo
 *
 * A Data Transfer Object (DTO) that is used to transfer data between the Domain Layer and the Data Layer.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

class EntityBookInfo(
    override val id: UUID2<@Contextual Book>,
    val title: String,
    val author: String,
    val description: String,
    val extraFieldToShowThisIsAnEntity: String? = "This is an EntityBookInfo", // default value
    val creationTimeMillis: Long,
    val lastModifiedTimeMillis: Long,
    val isDeleted: Boolean
) : EntityInfo(id),
    Model.ToDomainInfo<BookInfo>,
    Model.ToDomainInfo.HasToDeepCopyDomainInfo<BookInfo>,
    Info.ToInfo<EntityBookInfo>,
    Info.HasToDeepCopyInfo<EntityBookInfo>
{

    constructor(json: String, context: Context) : this(
        context.gson.fromJson<EntityBookInfo>(
            json,
            EntityBookInfo::class.java
        )
    )

    ////////////////////////////////////////////////////////////
    // DTOInfo <-> DomainInfo conversion                      //
    // Note: Intentionally DON'T accept `DTOInfo.DTOBookInfo` //
    //   - to keep DB layer separate from API layer)          //
    ////////////////////////////////////////////////////////////

    constructor(bookInfo: EntityBookInfo) : this(
        UUID2.fromUUID2(bookInfo.id().toDomainUUID2(), Book::class.java),  // change `UUID2Type` to UUID2<Book>
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
        "Extra info added during creation of EntityInfo.EntityBookInfo",
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

    ////////////////////////////////////////////
    // Entities don't have any business logic //
    // - All "Info" changes are done in the   //
    //   domain layer.                        //
    ////////////////////////////////////////////

    /////////////////////////////////
    // ToDomainInfo implementation //
    /////////////////////////////////

    override fun toDeepCopyDomainInfo(): BookInfo {
        // implement deep copy (if structure is not flat.)
        return BookInfo(this)
    }

    /////////////////////////////
    // ToInfo implementation   //
    /////////////////////////////

    override fun toDeepCopyInfo(): EntityBookInfo {
        // note: implement deep copy (if structure is not flat.)
        return EntityBookInfo(this)
    }
}
