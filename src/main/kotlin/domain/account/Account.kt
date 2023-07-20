package domain.account

import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.Context
import domain.account.data.AccountInfo
import domain.account.data.AccountInfoRepo
import domain.common.Role

/**
 * Account Role Object<br></br>
 *
 * Only interacts with its own Repo, Context, and other Role Objects
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

class Account : Role<AccountInfo>, IUUID2 {
    override val id: UUID2<Account>

    override fun id(): UUID2<*> {
        return id
    }

    private val repo: AccountInfoRepo

    constructor(
        info: AccountInfo,
        context: Context
    ) : super(info, context) {
        repo = context.accountInfoRepo
        id = info.id()

        context.log.d(this, "Account ($id) created from Info")
    }

    constructor(
        json: String,
        clazz: Class<AccountInfo>,
        context: Context
    ) : super(json, clazz, context) {
        repo = context.accountInfoRepo
        id = this.info()?.id() ?: throw Exception("AccountInfo id not found in Json")

        context.log.d(this, "Account (" + id + ") created from Json with class: " + clazz.name)
    }

    constructor(
        id: UUID2<Account>,
        context: Context
    ) : super(id, context) {
        repo = context.accountInfoRepo
        this.id = id
        context.log.d(this, "Account(" + this.id + ") created using id with no Info")
    }

    constructor(json: String, context: Context) : this(json, AccountInfo::class.java, context)
    constructor(context: Context) : this(UUID2.randomUUID2(), context)

    /////////////////////////////////////
    // IRole/UUID2 Required Overrides  //
    /////////////////////////////////////

    override fun fetchInfoResult(): Result<AccountInfo> {
        // context.log.d(this, "Account(" + this.id.toString() + ") - fetchInfoResult"); // LEAVE for debugging
        return repo.fetchAccountInfo(id)
    }

    override fun updateInfo(updatedInfo: AccountInfo): Result<AccountInfo> {
        // context.log.d(this,"Account (" + this.id.toString() + ") - updateInfo);  // LEAVE for debugging

        // Optimistically Update the cached Info
        super.updateFetchInfoResult(Result.success(updatedInfo))

        // Update the Repo
        return repo.updateAccountInfo(updatedInfo)
    }

    override fun uuid2TypeStr(): String {
        return UUID2.calcUUID2TypeStr(this.javaClass)
    }

    ///////////////////////////////////////////
    // Account Role Business Logic Methods //
    // - Methods to modify it's LibraryInfo  //
    ///////////////////////////////////////////

    fun DumpDB(context: Context) {
        println()
        context.log.d(this, "Dumping Account DB:")
        context.log.d(this, this.toJson())
        println()
    }

    companion object {

        /////////////////////////
        // Static constructors //
        /////////////////////////

        fun fetchAccount(
            uuid2: UUID2<Account>,
            context: Context
        ): Result<Account> {
            val repo: AccountInfoRepo = context.accountInfoRepo
            val infoResult: Result<AccountInfo> = repo.fetchAccountInfo(uuid2)
            if (infoResult.isFailure) return Result.failure(infoResult.exceptionOrNull()
                ?: Exception("Account.fetchAccount, unknown failure"))

            val info: AccountInfo = infoResult.getOrNull()
                ?: return Result.failure(Exception("Account.fetchAccount, unknown failure"))

            return Result.success(Account(info, context))
        }
    }
}