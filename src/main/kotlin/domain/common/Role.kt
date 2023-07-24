package domain.common

import com.google.gson.JsonSyntaxException
import com.realityexpander.jsonConfig
import common.uuid2.IUUID2
import common.uuid2.UUID2
import common.uuid2.UUID2.Companion.fromUUID
import domain.Context
import domain.common.data.info.DomainInfo
import domain.common.data.info.Info
import domain.library.data.LibraryInfo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import java.lang.reflect.ParameterizedType
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

/**
 * Role - Domain Role Abstract class
 *
 *  * The corresponding Repository only accepts/returns {Domain}Info objects, and internally converts
 *    to/from DTOs/Entity/Domain objects.
 *  * Works with the network API & local database to perform CRUD operations
 *  * Performs basic validation.
 *
 * The Repo can easily use fake API & Database for testing purposes.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

abstract class Role<TDomainInfo : DomainInfo> (
    @Transient            // prevent kotlinx serialization
    @kotlin.jvm.Transient // prevent gson serialization
    open val id: UUID2<*> = UUID2.randomUUID2<IUUID2>(),
    override val info: AtomicReference<TDomainInfo> = AtomicReference<TDomainInfo>(null),
    protected val context: Context
) : Info<TDomainInfo>,
    IUUID2
{
    // Convenience for Repo Debugging and future UI use
    private val infoResult: AtomicReference<Result<TDomainInfo>> = AtomicReference<Result<TDomainInfo>>(null)

    // For Gson serialization - this finds the Class of the Info<TDomain> object for this `Role`
    private val infoClazz =
        if (this.javaClass.genericSuperclass is ParameterizedType) (
            this.javaClass
                .genericSuperclass as ParameterizedType)  // 1. Get `TDomainInfo` concrete clazz for "Info"
                .actualTypeArguments[0] as Class<*>       //      from `Role<TDomainInfo>`... ⬇
        else (((
            this.javaClass
                .genericSuperclass as Class<*>)
                .genericSuperclass as ParameterizedType)  // 2. ⬆︎...or superclass generic-type
                .actualTypeArguments[0] as Class<*>       //      from `Info<TDomainInfo>`.
        )

    // For kotlinx serialization - Find the class of the `Info<TDomain>` object for this `Role`
    @Suppress("UNCHECKED_CAST")
    @OptIn(InternalSerializationApi::class)
    val kotlinxSerializer = runBlocking {
        // find the serializer for the info class
        infoClazz.kotlin.serializer() as KSerializer<TDomainInfo>
    }

    ///////////////////
    // Constructors  //
    ///////////////////

    constructor(
        id: UUID2<*>,
        info: TDomainInfo?,
        context: Context
    ) : this(id, AtomicReference<TDomainInfo>(info), context) {
        // this.id = id // intentionally NOT validating `id==info.id` bc need to be able to pass in `info` as null.
    }
    protected
    constructor(
        id: UUID2<*>,
        context: Context
    ) : this(id, AtomicReference<TDomainInfo>(null), context)
    protected
    constructor(
        domainInfoJson: String?,
        domainInfoClazz: Class<TDomainInfo>,
        context: Context
    ) : this(
        createInfoFromJson(domainInfoJson, domainInfoClazz, context)
            ?: throw Exception("Failed to create Info from JSON"),
        context
    )
    protected
    constructor(
        info: TDomainInfo?,
        context: Context
    ) : this(
        info?.id()?.toDomainUUID2() ?: UUID2.randomUUID2<IUUID2>(),  // if info is null, then user a random UUID // todo is there a better way to do this?
        AtomicReference<TDomainInfo>(info),
        context
    )
    private
    constructor(
        id: UUID,
        info: TDomainInfo?,
        context: Context
    ) : this(id.fromUUID<IUUID2>(), AtomicReference<TDomainInfo>(info), context)

    /////////////////////
    // Simple getters  //
    /////////////////////

    override fun cachedInfo(): AtomicReference<TDomainInfo> {
        return info
    }

    fun infoResult(): Result<TDomainInfo> {
        return infoResult.get()
    }

    suspend fun updateInfoFromJson(json: String): Result<TDomainInfo> {
        context.log.d(
            this, "Updating Info from JSON for " +
                "class: " + this.javaClass.name + ", " +
                "id: " + id()
        )

        return try {
            val infoFromJson = jsonConfig.decodeFromString(kotlinxSerializer, json)

            // Validate id of imported Info matches the id of this Info
            val checkResult: Result<TDomainInfo> = checkJsonInfoIdMatchesThisInfoId(infoFromJson, infoClazz)
            if (checkResult.isFailure) {
                return checkResult
            }

            // Update the info object with the new info
            updateInfo(infoFromJson)
        } catch (e: JsonSyntaxException) {
            Result.failure(Exception("Failed to parse JSON: " + e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open fun toJson(): String {
        if (!isInfoFetched) {
            context.log.w(this,
                "called on un-fetched info for " +
                        "class: " + this.javaClass.name + ", " +
                        "id: " + id()
            )

            return "{}"
        }

        return context.gson.toJson(info.get() as TDomainInfo)
    }

    /////////////////////////////////////////////////////
    // Methods REQUIRED to be overridden in subclasses //
    /////////////////////////////////////////////////////

    /**
     * Defines how to fetch the `{Domain}Info` object from server
     *
     * * **REQUIRED** - **MUST** be overridden & implemented in subclasses
     **/
    override suspend fun fetchInfoResult(): Result<TDomainInfo> {
        return Result.failure(Exception("Not Implemented, should be implemented in subclass."))
    }

    /**
     * Updates the `{Domain}Info` object with new data, and returns the updated `{Domain}Info` object.
     *
     * * **REQUIRED** - **MUST** be overridden & implemented in subclasses.
     *
     * * Call **`super.updateFetchInfoResult(newInfo)`** to update the **`info<TDomainInfo>`** object
     * * The caller decides when appropriate, ie: optimistic updates, or after server confirms update.
     **/
    abstract override suspend fun updateInfo(updatedInfo: TDomainInfo): Result<TDomainInfo>

    // NOTE: Should be implemented by subclasses but not required
    override fun toString(): String {

        // default toString() implementation
        return runBlocking {
            val infoString = if (info() == null) "null" else info().toString()
            val nameOfClass = this.javaClass.name

            nameOfClass + ": " + id() + ", info=" + infoString
        }
    }

    ///////////////////////////////////////////
    // Info<TDomainInfo> interface methods   //
    ///////////////////////////////////////////

    // Shorter named wrapper for fetchInfo()
    suspend fun info(): TDomainInfo? {
        return fetchInfo()
    }

    /**
    * Returns the Info<T> object if it has been fetched, otherwise fetches and returns Result.
    *
    * * Used to access the Info object without having to handle the Result<T> object.
    * * NOTE: A cached Info<T> object is returned if it has been fetched, otherwise a new Info<T> object is fetched.
    **/
    override suspend fun fetchInfo(): TDomainInfo? {
        if (isInfoFetched) {
            return cachedInfo().get()
        }

        // Attempt to fetch info, since it hasn't been successfully fetched yet.
        val fetchResult = fetchInfoResult()
        updateFetchInfoResult(fetchResult)
        if (fetchResult.isFailure) {
            context.log.d(
                this, "fetchInfoResult() FAILED for " +
                        "class: " + this.javaClass.name + ", " +
                        "id: " + id.toString()
            )
            return null
        }

        // Fetch was successful, so set info and return it.
        return super.updateCachedInfo(
            fetchResult.getOrNull()
        )
    }

    /**
     * Returns reason for failure of most recent `fetchInfo()` call, or `null` if was successful.
     *
     * * Used as a convenient error guard for methods that require a hydrated **`{Domain}Info`**.
     * * If `Info` is not fetched, it attempts to fetch it.
     *
     * * Note: BOOP Exception: The "returning null" behavior is to make the call site error handling code smaller.
     **/
    override suspend fun fetchInfoFailureReason(): String? {
        if (!isInfoFetched) {
            if (fetchInfoResult().isFailure) {
                return fetchInfoResult().exceptionOrNull()?.message
            }
        }
        return null // Returns `null` if the info has been fetched successfully. This makes the call site smaller.
    }

    override val isInfoFetched: Boolean
        get() = cachedInfo().get() != null

    // Forces refresh of Info from server
    override suspend fun refreshInfo(): Result<TDomainInfo> {
        context.log.d(
            this, "Refreshing info for " +
                    "class: " + this.javaClass.name + ", " +
                    "id: " + id.toString()
        )

        // Exception to the no-null rule. This is only done when forcing a re-fetch of the info.
        // todo should we leave the data stale?
        super.updateCachedInfo(null)
        return fetchInfoResult()
    }

    fun updateFetchInfoResult(updatedInfoResult: Result<TDomainInfo>) {
        // Save the result of the fetch for later use
        infoResult.getAndSet(updatedInfoResult)

        // Update the cached info if the fetch was successful
        if (updatedInfoResult.isSuccess) {
            super.updateCachedInfo(
                updatedInfoResult.getOrNull()
            )
        }
    }

    @Suppress("FunctionName")
    fun _overrideFetchResultToIsSuccess() {
        // Reset the fetch result to `isSuccess`, for use in testing.
        // This is solely used to prevent warnings when running the test code.
        println("this.info=" + this.info)
        println("this.infoResult=" + this.infoResult)
        infoResult.set(Result.success(this.info.get()))
    }

    companion object {

        ///////////////////////////
        // Static Constructors   //
        ///////////////////////////

        /**
         * Creates new **`Domain.{Domain}Info`** object with id from JSON string of **`Domain.{Domain}Info`** object.
         *
         * Kotlinx serialization version
         *
         * * Implemented as a static method, so it can be called from a constructor.
         * * Note: Type definitions are to make sure constrained to Domain subtypes and subclasses.
         * * ie: The `Library` Role object has a **`DomainInfo.LibraryInfo`** object which requires
         *   **`ToDomain<DomainInfo.LibraryInfo>`** to be implemented.
         *
         * * The `Domain.EntityInfo` and `Domain.DTOInfo` layer are intentionally restricted to accept only `Domain` objects.
        **/
        inline fun <reified TDomainInfo : DomainInfo>  // restrict to Domain subclasses, ie: Model.DomainInfo.*
        createInfoFromJson(
            json: String?,
            serializer: KSerializer<TDomainInfo>,
            context: Context,
        ): TDomainInfo? {
            json ?: return null

            return try {
                val domainInfoFromJson: TDomainInfo = jsonConfig.decodeFromString(serializer, json)
                context.log.d("Role:createInfoFromJson(kotlinx serializer)", "domainInfoFromJson = $domainInfoFromJson")

                domainInfoFromJson
            } catch (e: Exception) {
                context.log.d(
                    "Role:createInfoFromJson(kotlinx serializer)", "Failed to createInfoFromJson() for " +
                            "class: " + TDomainInfo::class.java.name + ", " +
                            "json: " + json + ", " +
                            "exception: " + e
                )

                null
            }
        }

        /**
         * Creates new **`Domain.{Domain}Info`** object with id from JSON string of **`Domain.{Domain}Info`** object.
         *
         * Gson serialization Version
         *
         * Same as above `createInfoFromJson(...)`, except uses **Gson** instead of kotlinx serialization.
         *
         **/
        fun <TDomainInfo : DomainInfo>  // restrict to Domain subclasses, ie: Model.DomainInfo.*
        createInfoFromJson(
            json: String?,
            domainInfoClazz: Class<TDomainInfo>,  // Type of `DomainInfo.TDomainInfo` object to create
            context: Context,
        ): TDomainInfo? {
            json ?: return null

            return try {
                val domainInfoFromJson: TDomainInfo = context.gson.fromJson(json, domainInfoClazz)
                context.log.d("Role:createInfoFromJson(Gson serializer)", "domainInfoFromJson = $domainInfoFromJson")

                domainInfoFromJson
            } catch (e: Exception) {
                context.log.d(
                    "Role:createInfoFromJson(Gson serializer)", "Failed to createInfoFromJson() for " +
                            "class: " + domainInfoClazz.name + ", " +
                            "json: " + json + ", " +
                            "exception: " + e
                )

                null
            }
        }
    }
}
