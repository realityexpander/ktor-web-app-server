package domain.common.data

import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.Context
import domain.Context.Companion.gsonConfig
import domain.common.data.info.DomainInfo
import domain.common.data.info.local.EntityInfo
import domain.common.data.info.network.DTOInfo
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.*

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
 * The **`Role`** does not know (or care) where it's data comes from, it only knows the "data shapes"
 * that it accepts, ie: `DomainInfo` objects.
 *
 * **DTO/Entity Info Classes**
 *
 *  * `{DTOInfo}Info` hold the API transfer "dumb" objects that transport info to/from their service/api/db.
 *  * `{EntityInfo}Info` hold the Database transfer "dumb" objects that transport info to/from their service/db.
 *  * Minimal validation occurs in the Domain layer when a DTOInfo/EntityInfo object is converted into
 *    a DomainInfo object.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

// This interface enforces all Model objects to include an id() method
interface HasId<TKey : UUID2<*>> {
    abstract val id: TKey  // todo is there a way to enforce this?
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

    interface ToDomainInfo<TDomainInfo : DomainInfo> {
        /**
        * **MUST** override in subclass.
         *
        * * Overridden method must return `id` with the correct type of `UUID2` for the Role.
        * * ie: `UUID2<User>` for a `User`, `UUID2<Book>` for a `Book`, etc.
        **/
        fun id(): UUID2<*>

        fun domainInfo(): TDomainInfo {
            @Suppress("UNCHECKED_CAST")
            return this as TDomainInfo
        }

        /**
        * **MUST** override, method in subclass.
        * * Should return a DEEP copy (& no original references)
        **/
        @OptIn(InternalSerializationApi::class)
        open fun toDomainInfoDeepCopy(): TDomainInfo {

            // This method is a lazy convenience using a Gson hack, and should really be overridden in each class.
            @Suppress("UNCHECKED_CAST")
            return gsonConfig.toJson(this.domainInfo()).let {
                    gsonConfig.fromJson(it, this::class.java)
                } as TDomainInfo

            // todo - should this be an abstract method without a default implementation?
            //throw RuntimeException("DomainInfo:ToDomainInfo:toDeepCopyDomainInfo(): Must override this method")
        }

        /**
        * This interface enforces all `{Domain}Info` objects to include a `deepDomainInfoCopy()` method
        * * Simply add implements **`ToDomainInfo.deepCopyDomainInfo<ToDomainInfo<{Domain}>>`** to the class
        *   definition, and the default `deepCopy()` method will be added.
        **/
        interface HasToDomainInfoDeepCopy<TToInfo : ToDomainInfo<out DomainInfo>> {

            // Should be overridden in subclass
            // Here as a default flat-copy implementation.
            // Should return a deep copy (no original references)
            open fun deepCopyDomainInfo(): DomainInfo
            {
                // This method is a lazy convenience, and should really be overridden in each class.
                @Suppress("UNCHECKED_CAST")
                return (this as TToInfo).toDomainInfoDeepCopy()
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
