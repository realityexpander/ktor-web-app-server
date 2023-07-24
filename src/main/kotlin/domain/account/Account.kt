package domain.account

import com.google.gson.Gson
import common.uuid2.IUUID2
import common.uuid2.UUID2
import domain.Context
import domain.Context.Companion.gsonConfig
import domain.account.data.AccountInfo
import domain.account.data.AccountInfoRepo
import domain.common.Role
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Account Role Object
 *
 * Only interacts with its own Repo, Context, and other Role Objects
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

@Serializable(with = AccountSerializer::class)  // for kotlinx.serialization
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
        accountInfoJson: String,
        clazz: Class<AccountInfo>,
        context: Context
    ) : super(accountInfoJson, clazz, context) {
        repo = context.accountInfoRepo
        id = this.info.get()?.id() ?: throw Exception("AccountInfo id not found in Json")

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
    constructor(context: Context) : this(UUID2.randomUUID2<Account>(), context)

    /////////////////////////////////////
    // IRole/UUID2 Required Overrides  //
    /////////////////////////////////////

    override suspend fun fetchInfoResult(): Result<AccountInfo> {
        // context.log.d(this, "Account(" + this.id.toString() + ") - fetchInfoResult"); // LEAVE for debugging
        return repo.fetchAccountInfo(id)
    }

    override suspend fun updateInfo(updatedInfo: AccountInfo): Result<AccountInfo> {
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

object AccountSerializer : KSerializer<Account> {
    override val descriptor = PrimitiveSerialDescriptor("Account", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Account {
        return gsonConfig.fromJson(decoder.decodeString(), Account::class.java)  // todo use kotlinx serialization instead of gson
    }

    override fun serialize(encoder: Encoder, value: Account) {
        encoder.encodeString(value.toString())
    }
}