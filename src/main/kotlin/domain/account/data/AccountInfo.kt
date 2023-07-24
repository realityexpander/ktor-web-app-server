package domain.account.data

import common.uuid2.UUID2
import domain.account.Account
import domain.book.Book
import domain.common.data.Model
import domain.common.data.info.DomainInfo
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * AccountInfo is a DomainInfo class for the Account domain object.
 *
 * * Contains data about a single User's account status in the LibraryApp system.
 *
 *  * Status of Account (active, inactive, suspended, etc.)
 *  * Current Fine Amount
 *  * Max books allowed to be checked out
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

@Serializable
class AccountInfo private constructor(
    override val id: UUID2<Account>,  // UUID should match User's UUID
    val name: String,
    val accountStatus: AccountStatus,
    val currentFinePennies: Int,
    val maxAcceptedBooks: Int,
    val maxFinePennies: Int,
    private val timeStampToAccountAuditLogItemMap: MutableMap<Long, AccountAuditLogItem>
) : DomainInfo(id),
    Model.ToDomainInfo<AccountInfo>
{
    // Showing the object can use internal ways to track its own data that will not be directly exposed to the outside world.
    // Ie: we could have used a "Log" role here, but instead we just use a Simple Map.

    // LEAVE for future use
    // final int maxDays;               // max number of days a book can be checked out
    // final int maxRenewals;           // max number of renewals (per book)
    // final int maxRenewalDays;        // max number days for each renewal (per book)
    // final int maxFineAmountPennies;  // max dollar amount of all fines allowed before account is suspended
    // final int maxFineDays;           // max number of days to pay fine before account is suspended

    constructor(id: UUID2<Account>, name: String) : this(
        id,
        name,
        AccountStatus.ACTIVE,
        0,
        5,
        1000,
        mutableMapOf<Long, AccountAuditLogItem>()
    )

    constructor(accountInfo: AccountInfo) : this(
        accountInfo.id(),
        accountInfo.name,
        accountInfo.accountStatus,
        accountInfo.currentFinePennies,
        accountInfo.maxAcceptedBooks,
        accountInfo.maxFinePennies,
        accountInfo.timeStampToAccountAuditLogItemMap
    )

    constructor(uuid: UUID, name: String) : this(UUID2<Account>(uuid, Account::class.java), name)
    constructor(id: String, name: String) : this(UUID.fromString(id), name)

    enum class AccountStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        CLOSED
    }

    @Serializable
    internal class AccountAuditLogItem(
        val timeStampLongMillis: Long = System.currentTimeMillis(),
        val operation: String = "Default Operation",
        private val entries: Map<String, String> = mutableMapOf()
    )

    ///////////////////////////////
    // Published Simple Getters  //  // note: no setters, all changes are made through business logic methods.
    ///////////////////////////////

    override fun id(): UUID2<Account> {
        return this.id
    }

    fun name(): String {
        return name
    }

    /////////////////////////////////////////////
    // Published Role Business Logic Methods   //
    /////////////////////////////////////////////
    
    fun activateAccountByStaff(reason: String, staffMemberName: String): Result<AccountInfo> {
        if (reason.isEmpty()) return Result.failure(IllegalArgumentException("reason is null or empty"))
        if (staffMemberName.isEmpty()) return Result.failure(IllegalArgumentException("staffMemberName is null or empty"))
        addAuditLogEntry("activateAccountByStaff", "reason", reason, "staffMemberName", staffMemberName)
        return Result.success(
            AccountInfo(
                id(),
                name,
                AccountStatus.ACTIVE,
                currentFinePennies,
                maxAcceptedBooks,
                maxFinePennies,
                timeStampToAccountAuditLogItemMap
            )
        )
    }

    fun deactivateAccountByStaff(reason: String, staffMemberName: String): Result<AccountInfo> {
        if (reason.isEmpty()) return Result.failure(IllegalArgumentException("reason is null or empty"))
        if (staffMemberName.isEmpty()) return Result.failure(IllegalArgumentException("staffMemberName is null or empty"))
        addAuditLogEntry("deactivateAccountByStaff", "reason", reason, "staffMemberName", staffMemberName)
        return Result.success(
            AccountInfo(
                id(),
                name,
                AccountStatus.INACTIVE,
                currentFinePennies,
                maxAcceptedBooks,
                maxFinePennies,
                timeStampToAccountAuditLogItemMap
            )
        )
    }

    fun suspendAccountByStaff(reason: String, staffMemberName: String): Result<AccountInfo> {
        if (reason.isEmpty()) return Result.failure(IllegalArgumentException("reason is empty"))
        if (staffMemberName.isEmpty()) return Result.failure(IllegalArgumentException("staffMemberName is empty"))
        addAuditLogEntry("suspendAccountByStaff", "reason", reason, "staffMemberName", staffMemberName)

        return Result.success(
            AccountInfo(
                id(),
                name,
                AccountStatus.SUSPENDED,
                currentFinePennies,
                maxAcceptedBooks,
                maxFinePennies,
                timeStampToAccountAuditLogItemMap
            )
        )
    }

    fun closeAccountByStaff(reason: String, staffMemberName: String): Result<AccountInfo> {
        if (reason.isEmpty()) return Result.failure(IllegalArgumentException("reason is empty"))
        if (staffMemberName.isEmpty()) return Result.failure(IllegalArgumentException("staffMemberName is empty"))
        addAuditLogEntry("closeAccountByStaff", "reason", reason, "staffMemberName", staffMemberName)

        return  Result.success(
            AccountInfo(
                id(),
                name,
                AccountStatus.CLOSED,
                currentFinePennies,
                maxAcceptedBooks,
                maxFinePennies,
                timeStampToAccountAuditLogItemMap
            )
        )
    }

    fun addFineForBook(fineAmountPennies: Int, bookId: UUID2<Book>): Result<AccountInfo> {
        if (fineAmountPennies < 0) return Result.failure(IllegalArgumentException("fineAmountPennies is negative"))
        addAuditLogEntry("addFine", "fineAmountPennies", fineAmountPennies, "bookId", bookId)

        var updatedAccountStatus = accountStatus
        if (calculateAccountStatus() != accountStatus) updatedAccountStatus = calculateAccountStatus()

        return  Result.success(
            AccountInfo(
                id(),
                name,
                updatedAccountStatus,
                currentFinePennies + fineAmountPennies,
                maxAcceptedBooks,
                maxFinePennies,
                timeStampToAccountAuditLogItemMap
            )
        )
    }

    fun payFine(fineAmountPennies: Int): Result<AccountInfo> {
        if (fineAmountPennies < 0) return Result.failure(IllegalArgumentException("fineAmountPennies is negative"))
        addAuditLogEntry("payFine", fineAmountPennies)

        var updatedAccountStatus = accountStatus
        if (calculateAccountStatus() != accountStatus) updatedAccountStatus = calculateAccountStatus()

        return  Result.success(
            AccountInfo(
                id(),
                name,
                updatedAccountStatus,
                currentFinePennies - fineAmountPennies,
                maxAcceptedBooks,
                maxFinePennies,
                timeStampToAccountAuditLogItemMap
            )
        )
    }

    fun adjustFineByStaff(
        newCurrentFineAmount: Int,
        reason: String,
        staffMemberName: String
    ): Result<AccountInfo> { // todo make staffMemberName a User.Staff object
        if (newCurrentFineAmount < 0) return Result.failure(IllegalArgumentException("newCurrentFineAmount is negative"))
        addAuditLogEntry("adjustFineByStaff", "reason", reason, "staffMember", staffMemberName)

        var updatedAccountStatus = accountStatus
        if (calculateAccountStatus() != accountStatus) updatedAccountStatus = calculateAccountStatus()

        return  Result.success(
            AccountInfo(
                id(),
                name,
                updatedAccountStatus,
                newCurrentFineAmount,
                maxAcceptedBooks,
                maxFinePennies,
                timeStampToAccountAuditLogItemMap
            )
        )
    }

    fun changeMaxBooksByStaff(
        maxBooks: Int,
        reason: String,
        staffMemberName: String
    ): Result<AccountInfo> { // todo make staffMemberName a User.Staff object
        if (maxBooks < 0) return Result.failure(IllegalArgumentException("maxBooks is negative"))
        addAuditLogEntry("changeMaxBooksByStaff", "reason", reason, "staffMember", staffMemberName)

        return  Result.success(
            AccountInfo(
                id(),
                name,
                accountStatus,
                currentFinePennies,
                maxBooks,
                maxFinePennies,
                timeStampToAccountAuditLogItemMap
            )
        )
    }

    fun changeMaxFineByStaff(
        maxFine: Int,
        reason: String,
        staffMemberName: String
    ): Result<AccountInfo> { // todo make staffMemberName a User.Staff object
        if (maxFine < 0) return Result.failure(IllegalArgumentException("maxFine is negative"))
        addAuditLogEntry("changeMaxFineByStaff", "reason", reason, "staffMember", staffMemberName)

        return  Result.success(
            AccountInfo(
                id(),
                name,
                accountStatus,
                currentFinePennies,
                maxAcceptedBooks,
                maxFine,
                timeStampToAccountAuditLogItemMap
            )
        )
    }

    ///////////////////////////////////////
    // Published Role Reporting Methods  //
    ///////////////////////////////////////

    fun calculateFineAmountPennies(): Int {
        return currentFinePennies
    }

    val auditLogStrings: Array<String>
        get() = timeStampToAccountAuditLogItemMap
            .entries
            .stream()
            .map<String> { (key, value): Map.Entry<Long, AccountAuditLogItem> ->  // Convert audit log to array of `timestamp:{action:"data"}` strings
                convertTimeStampLongMillisToIsoDateTimeString(key) +
                        ": " +
                        value
            }
            .toArray { size: Int -> arrayOfNulls(size) }

    private fun convertTimeStampLongMillisToIsoDateTimeString(timeStampMillis: Long): String {
        return DateTimeFormatter.ISO_DATE_TIME.format(
            Instant.ofEpochMilli(timeStampMillis)
        )
    }

    val isAccountActive: Boolean
        get() = accountStatus == AccountStatus.ACTIVE
    val isAccountInactive: Boolean
        get() = accountStatus == AccountStatus.INACTIVE
    val isAccountSuspended: Boolean
        get() = accountStatus == AccountStatus.SUSPENDED
    val isAccountClosed: Boolean
        get() = accountStatus == AccountStatus.CLOSED
    val isAccountInGoodStanding: Boolean
        get() = (accountStatus == AccountStatus.ACTIVE
                && !isMaxFineExceeded)
    val isAccountInBadStanding: Boolean
        get() = accountStatus == AccountStatus.SUSPENDED || accountStatus == AccountStatus.CLOSED || accountStatus == AccountStatus.INACTIVE || isMaxFineExceeded
    val isAccountInGoodStandingWithNoFines: Boolean
        get() = (accountStatus == AccountStatus.ACTIVE
                && hasNoFines())
    val isAccountInGoodStandingWithFines: Boolean
        get() = (accountStatus == AccountStatus.ACTIVE
                || accountStatus == AccountStatus.INACTIVE && currentFinePennies > 0 && !isMaxFineExceeded)

    fun isAccountInGoodStandingAndAbleToBorrowBooks(numberOfBooksInPossession: Int): Boolean {
        return (accountStatus == AccountStatus.ACTIVE && !isMaxFineExceeded
                && !hasReachedMaxAmountOfAcceptedLibraryBooks(numberOfBooksInPossession))
    }

    fun hasFines(): Boolean {
        return currentFinePennies > 0
    }

    fun hasNoFines(): Boolean {
        return currentFinePennies <= 0
    }

    val isMaxFineExceeded: Boolean
        get() = currentFinePennies >= maxFinePennies

    fun hasReachedMaxAmountOfAcceptedLibraryBooks(numberOfBooksInPossession: Int): Boolean {
        return numberOfBooksInPossession >= maxAcceptedBooks
    }

    // todo - calculate fines based on time passed since book was due, etc.

    /////////////////////////////////////////
    // Published Testing Helper Methods    //
    /////////////////////////////////////////

    fun addTestAuditLogMessage(message: String) {
        addAuditLogEntry("addTestAuditLogMessage", message)
    }

    //////////////////////////////
    // Private Helper Functions //
    //////////////////////////////

    fun activateAccount(): Result<AccountInfo> {
        // Note: this status will be overridden if `User` pays a fine or `Library` adds a fine
        return withAccountStatus(AccountStatus.ACTIVE)
    }

    fun deactivateAccount(): Result<AccountInfo> {
        return withAccountStatus(AccountStatus.INACTIVE)
    }

    fun suspendAccount(): Result<AccountInfo> {
        // Note: this status will be overridden if `User` pays a fine or `Library` adds a fine
        return withAccountStatus(AccountStatus.SUSPENDED)
    }

    fun closeAccount(): Result<AccountInfo> {
        return withAccountStatus(AccountStatus.CLOSED)
    }

    private fun calculateAccountStatus(): AccountStatus {
        if (accountStatus == AccountStatus.CLOSED) return AccountStatus.CLOSED
        if (accountStatus == AccountStatus.INACTIVE) return AccountStatus.INACTIVE
        return if (currentFinePennies > maxFinePennies) {
            AccountStatus.SUSPENDED
        } else AccountStatus.ACTIVE
    }

    private fun addAuditLogEntry(
        timeStampMillis: Long,
        operation: String,
        key1: String, value1: Any,
        key2: String, value2: Any
    ) {
        val keyValMap = HashMap<String, String>()
        keyValMap[key1] = value1.toString()
        keyValMap[key2] = value2.toString()
        timeStampToAccountAuditLogItemMap[timeStampMillis] = AccountAuditLogItem(timeStampMillis, operation, keyValMap)
    }

    private fun addAuditLogEntry(timeStampMillis: Long, operation: String, value: Any) {
        val keyValMap = HashMap<String, String>()
        keyValMap["value"] = value.toString()
        timeStampToAccountAuditLogItemMap[timeStampMillis] = AccountAuditLogItem(timeStampMillis, operation, keyValMap)
    }

    private fun addAuditLogEntry(timeStampMillis: Long, operation: String) {
        timeStampToAccountAuditLogItemMap[timeStampMillis] =
            AccountAuditLogItem(timeStampMillis, operation, HashMap())
    }

    private fun addAuditLogEntry(operation: String, key1: String, value1: Any, key2: String, value2: Any) {
        addAuditLogEntry(System.currentTimeMillis(), operation, key1, value1, key2, value2)
    }

    private fun addAuditLogEntry(operation: String, value: Any) {
        addAuditLogEntry(System.currentTimeMillis(), operation, value)
    }

    private fun addAuditLogEntry(operation: String) {
        addAuditLogEntry(System.currentTimeMillis(), operation)
    }

    private fun withName(newName: String): Result<AccountInfo> {
        return if (newName.isEmpty()) Result.failure(IllegalArgumentException("newName is null or empty")) else  Result.success(
            AccountInfo(
                id(), newName
            )
        )
    }

    private fun withAccountStatus(newAccountStatus: AccountStatus): Result<AccountInfo> {
        return  Result.success(
            AccountInfo(
                id(),
                name,
                newAccountStatus,
                currentFinePennies,
                maxAcceptedBooks,
                maxFinePennies,
                timeStampToAccountAuditLogItemMap
            )
        )
    }

    private fun withCurrentFineAmountPennies(newCurrentFineAmountPennies: Int): Result<AccountInfo> {
        return if (newCurrentFineAmountPennies < 0) Result.failure(IllegalArgumentException("newCurrentFineAmountPennies is negative")) else  Result.success(
            AccountInfo(
                id(),
                name,
                accountStatus,
                newCurrentFineAmountPennies,
                maxAcceptedBooks,
                maxFinePennies,
                timeStampToAccountAuditLogItemMap
            )
        ) // todo - allow credits for Fines?
    }

    private fun withMaxBooks(newMaxBooks: Int): Result<AccountInfo> {
        return if (newMaxBooks < 0) Result.failure(IllegalArgumentException("newMaxBooks is negative")) else  Result.success(
            AccountInfo(
                id(),
                name,
                accountStatus,
                currentFinePennies,
                newMaxBooks,
                maxFinePennies,
                timeStampToAccountAuditLogItemMap
            )
        )
    }

    /////////////////////////////////
    // ToDomainInfo implementation //
    /////////////////////////////////

    // note: currently no DB or API for UserInfo (so no .ToEntity() or .ToDTO())
    override fun toDomainInfoDeepCopy(): AccountInfo {
        // Note: *MUST* return a deep copy
        return AccountInfo(
            id(),
            name,
            accountStatus,
            currentFinePennies,
            maxAcceptedBooks,
            maxFinePennies,
            HashMap(timeStampToAccountAuditLogItemMap) // deep copy
        )
    }
}
