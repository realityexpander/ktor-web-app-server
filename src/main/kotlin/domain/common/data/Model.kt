package domain.common.data

import com.google.gson.GsonBuilder
import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.Context
import domain.common.data.info.DomainInfo
import domain.common.data.info.local.EntityInfo
import domain.common.data.info.network.DTOInfo

/**
 * **Model** - Top of data "Info" hierarchy
 *
 * * Defines interface for data `Info` conversion of **`DomainInfo`** *to/from* **`DTOInfo/EntityInfo`**.<br></br>
 *
 *
 * **Domain Info Classes**
 *
 *  * The `{Domain}Info` "Data Holder" class is kept inside each Domain `Role` Object.
 *  * Similar to an Entity for a database row or a DTO for a REST API endpoint, these objects
 *    contain the "data" or `Info` that is accessed by the `Role` object.
 *  * These are the "source of truth" for the Domain object's "information" in the application.
 *  * `{Domain}Info` hold the `Role Info` that resides *elsewhere*, usually on a server/db/api.
 *
 * The `Role` does not know (or care) where the data comes from, it only knows the "data shapes"
 * that it accepts.
 *
 * **DTO/Entity Info Classes**
 *
 *  * `{DTOInfo}Info` hold the API transfer "dumb" objects that transport info to/from their service/api/db.
 *  * `{EntityInfo}Info` hold the Database transfer "dumb" objects that transport info to/from their service/db.
 *  * Minimal validation occurs in the Domain layer when a DTOInfo/EntityInfo object is converted into
 *    a DomainInfo object.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */
open class Model protected constructor(id: UUID2<IUUID2>) {
    var _id: UUID2<IUUID2> // Can't make final due to need to set it during JSON deserialization. 🫤

    // Also can't make it private due to Gson's need to access it during deserialization. 🫤
    // todo Is there a better way to do this? (maybe another JSON library?)
    init {
        _id = UUID2(id)
    }

    ////////////////////////
    // Simple getters     //
    ////////////////////////
    fun id(): UUID2<IUUID2> {
        return _id
    }

    // EXCEPTIONAL CASE:
    // - This method is for JSON deserialization purposes & should only be used for such.
    // - It is not intended to be used for any other purpose.
    // - todo Is there a better way to do this?
    fun _setIdFromImportedJson(_id: UUID2<IUUID2>) {
        this._id = _id
    }

    fun toPrettyJson(): String {
        return GsonBuilder().setPrettyPrinting().create().toJson(this)
    }

    fun toPrettyJson(context: Context): String {
        return context.gson.toJson(this)
    }

    ///////////////////////////
    // Converters between    //
    // - Domain.{Domain}Info //
    // - Entity.{Domain}Info //
    // - DTO.{Domain}Info    //
    ///////////////////////////
    interface ToDomainInfo<TDomainInfo : DomainInfo> {
        // *MUST* override
        // - Overridden method should return `id` with the correct type of UUID2 for the domain
        //   ie: `UUID2<User>` for the `User`, `UUID2<UserInfo>` for the UserInfo, etc.
        fun id(): UUID2<IUUID2>

        val domainInfo: TDomainInfo  // Return reference to TDomainInfo, used when importing JSON
            get() =
                this as TDomainInfo

        fun toDeepCopyDomainInfo(): TDomainInfo {    // **MUST** override, method should return a DEEP copy (& no original references)
            throw RuntimeException("DomainInfo:ToDomainInfo:toDeepCopyDomainInfo(): Must override this method")
        }

        // This interface enforces all DomainInfo objects to include a deepCopyDomainInfo() method
        // - Just add "implements ToDomainInfo.deepCopyDomainInfo<ToDomainInfo<Domain>>" to the class
        //   definition, and the deepCopy() method will be added.
        interface hasToDeepCopyDomainInfo<TToInfo : ToDomainInfo<out DomainInfo>> {
            fun deepCopyDomainInfo(): DomainInfo // Requires method override, should return a deep copy (no original references)
            {
                // This method is a lazy convenience, and should really be overridden in each class.
                // This is a hack to get around the fact that Java doesn't allow you to call a generic method from a generic class
                return (this as TToInfo).toDeepCopyDomainInfo()
            }
        }
    }

    interface ToEntityInfo<T : EntityInfo> {
        fun toInfoEntity(): T // Should return a deep copy (no original references)
    }

    interface ToDTOInfo<T : DTOInfo> {
        fun toInfoDTO(): T // Should return a deep copy (no original references)
    }
}
