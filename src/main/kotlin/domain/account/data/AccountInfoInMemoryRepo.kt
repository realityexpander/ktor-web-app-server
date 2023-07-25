package domain.account.data

import common.log.ILog
import common.uuid2.UUID2
import domain.account.Account
import domain.common.data.repo.Repo

/**
 * AccountInfoInMemoryRepo is a Repo for AccountInfo objects.
 *
 * Holds Account info for all the users in the system (simple CRUD operations)
 *
 * Simulates a database on a server via in-memory HashMap.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

class AccountInfoInMemoryRepo(log: ILog) : Repo(log), IAccountInfoRepo {
    // simulate a local database on server (UUID2<Account> is the key)
    private val database: MutableMap<UUID2<Account>, AccountInfo> = mutableMapOf()

    override fun fetchAccountInfo(id: UUID2<Account>): Result<AccountInfo> {
        log.d(this, "id: $id")

        // Simulate network/database
        return if (database.containsKey(id)) {
            Result.success(database[id] ?: return Result.failure(Exception("Repo.AccountInfo, account not found, id: $id")))
        } else
            Result.failure(Exception("Repo.AccountInfo, account not found, id: $id"))
    }

    override fun updateAccountInfo(accountInfo: AccountInfo): Result<AccountInfo> {
        log.d(this, "accountInfo.id: " + accountInfo.id())

        // Simulate network/database
        if (database.containsKey(accountInfo.id())) {
            database[accountInfo.id()] = accountInfo
            return Result.success(accountInfo)
        }
        return Result.failure(Exception("Repo.AccountInfo, account not found, id: " + accountInfo.id()))
    }

    override fun upsertAccountInfo(accountInfo: AccountInfo): Result<AccountInfo> {
        log.d(this, "accountInfo.id(): " + accountInfo.id())

        // Simulate network/database
        database[accountInfo.id()] = accountInfo
        return Result.success(accountInfo)
    }

    override suspend fun deleteDatabase(): Result<Unit> {
        log.d(this, "deleteDatabase")

        // Simulate network/database
        database.clear()
        return Result.success(Unit)
    }
}