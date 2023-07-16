package domain.common

import com.google.gson.JsonSyntaxException
import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.Context
import domain.common.data.info.DomainInfo
import domain.common.data.info.Info
import domain.common.data.info.Info.Companion.createInfoFromJson
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Domain Role - Common Domain Role Abstract class<br></br>
 *
 *  * Repo only accepts/returns Domain Models, and internally converts to/from DTOs/Entities/Domains
 *  * Works with the network API & local database to perform CRUD operations, and also performs validation.
 *  * Can also be used to implement caching.
 *
 * The Repo can easily accept fake APIs & Database for testing.
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */

abstract class Role<TDomainInfo : DomainInfo> (
    @Transient
    open val id: UUID2<*> = UUID2.randomUUID2<IUUID2>(),
    private val info: AtomicReference<TDomainInfo> = AtomicReference<TDomainInfo>(null),
    protected val context: Context
) : Info<TDomainInfo>, IUUID2 {

//    val id: UUID2<IUUID2> // the UUID of the Domain object matches the UUID of the Info object
//    @Transient
//    open val id: UUID2<*> // the UUID of the Domain object matches the UUID of the Info object

    // It is final and private in the Domain layer.
//    private val info // Cached Information object for Info<Domain.{Domain}Info> interface
//            : AtomicReference<TDomainInfo>
    private val infoResult: AtomicReference<Result<TDomainInfo>> =
        AtomicReference<Result<TDomainInfo>>(null) // Convenience for Repo Debugging and future UI use

    // Singletons
//    val context: Context // All roles have access the Context singleton

    // Class of the Info<TDomain> info object (for Gson serialization)
    private val infoClazz =
        if (javaClass.genericSuperclass is ParameterizedType) (
            javaClass
                .genericSuperclass as ParameterizedType)         // 1. Get clazz for "Info" from this class... ⬇︎
                .actualTypeArguments[0] as Class<TDomainInfo>
        else (((
            javaClass
                .genericSuperclass as Class<*>)
                .genericSuperclass as ParameterizedType)         // 2. ⬆︎ ...or from the super class generic type.
                .actualTypeArguments[0] as Class<TDomainInfo>
        )

    ///////////////////
    // Constructors  //
    ///////////////////

    constructor(
        id: UUID2<*>,
        info: TDomainInfo?,
        context: Context
    ) : this(id, AtomicReference<TDomainInfo>(info), context) {
//        this.id = id // intentionally NOT validating `id==info.id` bc need to be able to pass in `info` as null.
//        this.context = context
//        this.info = AtomicReference<TDomainInfo>(info)
    }

//    private constructor(
//        id: UUID,
//        info: TDomainInfo?,
//        context: Context
//    ) {
////        this.id = UUID2.fromUUID(id) // Note: NOT validating "id==info.id" due to need to pass in `info` as `null`
////        this.id = UUID2.fromUUID<IUUID2>(id) // Note: NOT validating "id==info.id" due to need to pass in `info` as `null`
//        this.id = UUID2.fromUUID<IUUID2>(id) // Note: NOT validating "id==info.id" due to need to pass in `info` as `null`
//        this.context = context
//        this.info = AtomicReference<TDomainInfo>(info)
//    }

    protected
    constructor(
        id: UUID2<*>,
        context: Context
    ) : this(id, AtomicReference<TDomainInfo>(null), context)

    protected
    constructor(
        domainInfoJson: String,
        domainInfoClazz: Class<TDomainInfo>,
        context: Context
    ) : this(
        createInfoFromJson(domainInfoJson, domainInfoClazz, context) as TDomainInfo?
            ?: throw Exception("Failed to create Info from JSON"),
        context
    )

    protected
    constructor(
        info: TDomainInfo?,
        context: Context
//    ) : this(info.id().toDomainUUID2(), info, context)
    ) : this(
        info?.id()?.toDomainUUID2() ?: UUID2.randomUUID2<IUUID2>(),  // if info is null, then user a random UUID // todo is there a better way to do this?
        AtomicReference<TDomainInfo>(info),
        context
    )

    private constructor(
        id: UUID,
        info: TDomainInfo?,
        context: Context
    ) : this(UUID2.fromUUID<IUUID2>(id), AtomicReference<TDomainInfo>(info), context)


//    internal
//    constructor(
//        context: Context
//    ) : this(UUID2.randomUUID2<IUUID2>(), AtomicReference(null) as AtomicReference<TDomainInfo>, context)

    /////////////////////
    // Simple getters  //
    /////////////////////

//    override fun id(): UUID2<IUUID2> {
//    fun id(): UUID2<*> {
//        return id
//    }

    override fun cachedInfo(): AtomicReference<TDomainInfo> {
        return info
    }

    fun infoResult(): Result<TDomainInfo> {
        return infoResult.get()
    }

    fun updateInfoFromJson(json: String): Result<TDomainInfo> {
        context.log.d(
            this, "Updating Info from JSON for " +
                    "class: " + this.javaClass.name + ", " +
                    "id: " + infoId()
        )
        return try {
            val domainInfoClazz = infoClazz
            val infoFromJson: TDomainInfo = context.gson.fromJson(json, domainInfoClazz)
            val checkResult: Result<TDomainInfo> = checkJsonInfoIdMatchesThisInfoId(infoFromJson, domainInfoClazz)
            if (checkResult.isFailure) {
                return checkResult
            }

            // Set Domain "Model" id to match id of imported Info // todo maybe use just one id?
            infoFromJson._setIdFromImportedJson(
                UUID2(infoFromJson.id(), UUID2.calcUUID2TypeStr(domainInfoClazz))
            )

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
            context.log.w(
                this, "called on un-fetched info for " +
                        "class: " + this.javaClass.name + ", " +
                        "id: " + infoId()
            )
            return "{}"
        }
        return context.gson.toJson(fetchInfo())
    }

    /////////////////////////////////////////////////////
    // Methods REQUIRED to be overridden in subclasses //
    /////////////////////////////////////////////////////

    // Defines how to fetch info from server
    // - REQUIRED - *MUST* be overridden/implemented in subclasses
    override fun fetchInfoResult(): Result<TDomainInfo> {
        return Result.failure(Exception("Not Implemented, should be implemented in subclass."))
    }

    // Updates the `Info` object with new data
    // - REQUIRED - *MUST* be overridden/implemented in subclasses
    // - Call `super.updateFetchInfoResult(newInfo)` to update the info<TDomainInfo> object
    //   (caller decides when appropriate, ie: optimistic updates, or after server confirms update)
    abstract override fun updateInfo(updatedInfo: TDomainInfo): Result<TDomainInfo> // **MUST** Override in subclasses

    // NOTE: Should be Implemented by subclasses but not required
    override fun toString(): String {

        // default toString() implementation
        val infoString = if (info() == null) "null" else info().toString()
        val nameOfClass = this.javaClass.name
        return nameOfClass + ": " + infoId() + ", info=" + infoString
    }

    /////////////////////////////////
    // Info<T> interface methods   //
    /////////////////////////////////

    // Shorter named wrapper for fetchInfo()
    fun info(): TDomainInfo? {
        return fetchInfo()
    }

    // Returns the Info<T> object if it has been fetched, otherwise fetches and returns Result.
    // Used to access the Info object without having to handle the Result<T> object.
    // NOTE: A cached Info<T> object is returned if it has been fetched, otherwise a new Info<T> object is fetched.
    override fun fetchInfo(): TDomainInfo? {
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

    // Returns reason for failure of most recent `fetchInfo()` call, or `null` if was successful.
    // - Used as a convenient error guard for methods that require the {Domain}Info to be loaded.
    // - If `Info` is not fetched, it attempts to fetch it.
    // - BOOP Exception: The "returning null" behavior is to make the call site error handling code smaller.
    override fun fetchInfoFailureReason(): String? {
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
    override fun refreshInfo(): Result<TDomainInfo> {
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

    companion object {

        ///////////////////////////
        // Static Constructors   //
        ///////////////////////////

        // Creates new `Domain.{Domain}Info` object with id from JSON string of `Domain.{Domain}Info` object
        // - Implemented as a static method bc it can be called from a constructor.
        //   (Can't call instance methods from constructor in java.)
        // - Note: Type definitions are to make sure constrained to Domain subtypes and subclasses.
        // - ie: The Library domain object has a Domain.LibraryInfo object which requires ToDomain<Domain.LibraryInfo>
        //   to be implemented.
        // - Only imports JSON to Domain objects.
        //   The Domain.EntityInfo and Domain.DTOInfo layer are intentionally restricted to accept only Domain objects.
        // - todo : Should change to a marker interface instead of a constraining to the ToDomain<TDomain> interface?
        // for _setIdFromImportedJson() call

        fun <TDomainInfo : DomainInfo>  // restrict to Domain subclasses, ie: Domain.BookInfo
        createInfoFromJson(
            json: String?,
            domainInfoClazz: Class<TDomainInfo>,  // type of `Domain.TDomainInfo` object to create
            context: Context
        ): TDomainInfo? {
            json ?: return null

            return try {
                val obj: TDomainInfo = context.gson.fromJson(json, domainInfoClazz as Type)
                context.log.d("Role:createInfoFromJson()", "obj = $obj")

                // Set UUID2Type to match the type of TDomainInfo object
                val domainInfoClazzName: String = UUID2.calcUUID2TypeStr(domainInfoClazz)
                domainInfoClazz.cast(obj)
                    ?.id()
                    ?._setUUID2TypeStr(domainInfoClazzName)

                // Set `id` to match `id` of the Info
                (obj as TDomainInfo)._setIdFromImportedJson(
                    UUID2((obj as TDomainInfo).id() ?: throw Exception("id is null"),
                        domainInfoClazzName
                    )
                )

                obj
            } catch (e: Exception) {
                context.log.d(
                    "Role:createInfoFromJson()", "Failed to createInfoFromJson() for " +
                            "class: " + domainInfoClazz.name + ", " +
                            "json: " + json + ", " +
                            "exception: " + e
                )

                null
            }
        }
    }
}
