package domain.common.data.info

import com.google.gson.Gson
import com.realityexpander.jsonConfig
import common.uuid2.UUID2
import domain.Context
import domain.common.data.Model
import java.lang.reflect.Type
import java.util.concurrent.atomic.AtomicReference

/**
 * **`Info`** is an interface for smart "data holder" implementing class. It is used for transferring data
 * to/from the Domain to/from Database/Api.
 *
 *  * The **`Info`** interface defines the logic to update and fetch the {Domain}Info object.
 *  * It is the "single source of truth" for the Domain object's mutable data.
 *
 *  * **`TInfo`** - A required {Domain}Info subclass that contains all business logic to mutate
 *    the 'Info' object, ie: **`BookInfo`** or **`EntityLibraryInfo`**.
 *  * **`info: AtomicReference<TInfo>`** - Is a required thread-safe cache object for the Role's "Info"
 *    and is usually defined in the Role superclass.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

interface Info<TInfo : Model> {
    fun id(): UUID2<*>                         // Return the UUID2 of the Role object that this Info object represents.
    abstract val info: AtomicReference<TInfo>  // Holds the `Info` object for the Role.
    val isInfoFetched: Boolean                 // Return true if Info has been successfully fetched from server/DB.

    suspend fun fetchInfo(): TInfo?                           // Fetch data for the Info from server/DB.
    suspend fun fetchInfoResult(): Result<TInfo>              // Fetch Result<T> for the Info from server/DB.
    suspend fun updateInfo(updatedInfo: TInfo): Result<TInfo> // Update Info to server/DB.
    suspend fun refreshInfo(): Result<TInfo>                  // Set Info data to `null` and fetches Info from server/DB.
    suspend fun fetchInfoFailureReason(): String?             // Performs fetch for Info and returns failure reason, or `null` if successful.
    fun cachedInfo(): AtomicReference<TInfo>          // Return thread-safe Info from cache.

    // Performs Atomic update of cachedInfo
    fun updateCachedInfo(updatedInfo: TInfo?): TInfo? {
        return cachedInfo().updateAndGet { updatedInfo }
    }

    // Default naive implementation, returns a deep copy of the Info object.
    // - Should be overloaded in the Info subclass to return a deep copy of the Info object.
    fun deepCopyInfo(): TInfo {
        val gson = Gson()

        // hacky but works for flat/simple objects, but should be overridden in the subclass.
        @Suppress("UNCHECKED_CAST")
        return gson.fromJson(
            gson.toJson(this),
            this.javaClass
        ) as TInfo
    }

    /////////////////////////////
    // Interfaces for Info     //
    /////////////////////////////

    interface ToInfo<TInfo : Model> {
//        fun id(): UUID2<*>    // Returns the UUID2 of the Info object

        fun info(): TInfo? {           // Fetches (if necessary) and Returns the Info object
            @Suppress("UNCHECKED_CAST")
            return this as TInfo
        }

        // Should be overridden in the `{Domain}Info` subclass to return a deep copy of the Info object.
        fun toDeepCopyInfo(): TInfo {
            @Suppress("UNCHECKED_CAST")
            return (this as Info<TInfo>).deepCopyInfo()
        }
    }

    // This interface used to enforce all {Domain}Info objects has a `deepCopy()` method.
    // - Just add `implements ToInfo.hasDeepCopyInfo<ToInfo<{InfoClass}>>` to the class
    //   definition, and the toDeepCopyInfo() method will be added.
    interface HasToDeepCopyInfo<TInfo : ToInfo<*>> {
        fun deepCopyInfo(): TInfo {
            // This default implementation for deepCopyInfo() simply calls the toDeepCopyInfo() implemented in the subclass.
            @Suppress("UNCHECKED_CAST")
            return (this as TInfo).toDeepCopyInfo() as TInfo
        }
    }

    //////////////////////////////
    // Helper methods for Info  //
    //////////////////////////////

    fun checkJsonInfoIdMatchesThisInfoId(infoFromJson: Any, infoClazz: Class<*>): Result<TInfo> {
        try {
            // Ensure JSON Info object has an `_id` field
            val rootInfoClazz = findRootClazz(infoClazz)
            rootInfoClazz.getDeclaredField("id")
                ?: return Result.failure(Exception("checkJsonInfoIdMatchesThisInfoId(): Info class does not have an _id field"))

            @Suppress("UNCHECKED_CAST")
            if ((infoFromJson as TInfo).id != id()) {
                return Result.failure(
                    Exception(
                        "checkJsonInfoIdMatchesThisInfoId(): Info id does not match imported json id, " +
                                "info id: " + id() + ", " +
                                "json id: " + infoFromJson.id
                    )
                )
            }
        } catch (e: NoSuchFieldException) {
            return Result.failure(Exception("checkJsonInfoIdMatchesThisInfoId(): Info class does not have an id field"))
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(infoFromJson as TInfo)
    }

    fun findRootClazz(infoClazz: Class<*>): Class<*> {
        var rootClazz = infoClazz

        while (rootClazz.superclass.simpleName != "Object") {
            rootClazz = rootClazz.superclass
        }

        return rootClazz
    }

    companion object {

        // Create an Info object from a JSON string using Gson serialization
        fun <TToInfo : ToInfo<*>?> createInfoFromJson(
            json: String,
            infoClazz: Class<TToInfo>,  // type of `Info` object to create
            context: Context
        ): TToInfo? {
            return try {
                 val infoFromJson: TToInfo? = context.gson.fromJson(json, infoClazz as Type) // Gson version
                context.log.d("Info:createInfoFromJson(Gson)", "obj = $infoFromJson")
                if(infoFromJson == null) throw Exception("Info:createInfoFromJson(Gson): infoFromJson is null")

                infoFromJson
            } catch (e: Exception) {
                context.log.d(
                    "Info:createInfoFromJson()", "Failed to createInfoFromJson() for " +
                            "class: " + infoClazz.name + ", " +
                            "json: " + json + ", " +
                            "exception: " + e
                )

                null
            }
        }

        // Create an Info object from a JSON string using Kotlinx serialization
        inline fun <reified TToInfo : ToInfo<*>?> createInfoFromJson(
            json: String,
            context: Context
        ): TToInfo? {
            return try {
                val infoFromJson: TToInfo? = jsonConfig.decodeFromString(json) // kotlinx.serialization version
                context.log.d("Info:createInfoFromJson(Kotlinx)", "infoFromJson = $infoFromJson")
                if(infoFromJson == null) throw Exception("Info:createInfoFromJson(Kotlinx): infoFromJson is null")

                infoFromJson
            } catch (e: Exception) {
                context.log.d(
                    "Info:createInfoFromJson()", "Failed to createInfoFromJson() for " +
                            "class: " + ToInfo::class.java.name + ", " +
                            "json: " + json + ", " +
                            "exception: " + e
                )

                null
            }
        }
    }
}
