package domain.common.data

import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.Context
import domain.Context.Companion.gsonConfig
import domain.common.data.info.DomainInfo
import domain.common.data.info.local.InfoEntity
import domain.common.data.info.network.InfoDTO
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * **Model** - Top of data "Info" hierarchy
 *
 * * Defines interface for data `Info` conversion of **`DomainInfo`** *to/from* **`InfoDTO/InfoEntity`**.<br></br>
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
 * The **`Role`** does not know (or care) where it's data comes from, it only knows the "data shapes"
 * that it accepts, ie: `DomainInfo` objects.
 *
 * **DTO/Entity Info Classes**
 *
 *  * `{InfoDTO}Info` hold the API transfer "dumb" objects that transport info to/from their service/api/db.
 *  * `{InfoEntity}Info` hold the Database transfer "dumb" objects that transport info to/from their service/db.
 *  * Minimal validation occurs in the Domain layer when a InfoDTO/InfoEntity object is converted into
 *    a DomainInfo object.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

// This interface is for all classes that have an id
interface HasId<TKey : UUID2<*>> {
    abstract val id: TKey
    abstract fun id(): TKey
}

@Serializable
open class Model(
    @Transient            // prevent kotlinx serialization
    @kotlin.jvm.Transient // prevent gson serialization
    override val id: UUID2<*> = UUID2.randomUUID2<IUUID2>()
) : HasId<UUID2<*>> {

    ////////////////////////
    // Simple getters     //
    ////////////////////////

    override fun id(): UUID2<*> {
        return id;
    }

    fun toPrettyJson(): String {
        return gsonConfig.toJson(this)
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

    interface ToDomainInfoDeepCopy<TDomainInfo : DomainInfo> {
        /**
         * **MUST** override in subclass.
         *
         * * Overridden method must return `id` with the correct type of `UUID2` for the Role.
         * * ie: `UUID2<User>` for a `User`, `UUID2<Book>` for a `Book`, etc.
        **/

        fun domainInfo(): TDomainInfo {
            @Suppress("UNCHECKED_CAST")
            return this as TDomainInfo
        }

        /**
         * **MUST** override, method in subclass.
         * * Should return a DEEP copy (& no original references)
        **/
        open fun toDomainInfoDeepCopy(): TDomainInfo {

            // This method is a lazy convenience using a Gson hack, and should really be overridden in each class.
            @Suppress("UNCHECKED_CAST")
            return gsonConfig.toJson(this.domainInfo()).let {
                    gsonConfig.fromJson(it, this::class.java)
                } as TDomainInfo

            // LEAVE FOR REFERENCE
            //throw RuntimeException("DomainInfo:ToDomainInfoDeepCopy:toDeepCopyDomainInfo(): Must override this method")
        }
    }

    interface ToEntityInfo<T : InfoEntity> {
        fun toInfoEntity(): T // Should return a deep copy (no original references)
    }

    interface ToDTOInfo<T : InfoDTO> {
        fun toInfoDTO(): T // Should return a deep copy (no original references)
    }
}
