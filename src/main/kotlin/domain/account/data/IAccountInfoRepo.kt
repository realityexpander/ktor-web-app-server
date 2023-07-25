package domain.account.data

import common.uuid2.UUID2
import domain.account.Account
import domain.common.data.repo.IRepo

/**
 * IAccountInfoRepo is an interface for the AccountInfoInMemoryRepo class.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

interface IAccountInfoRepo : IRepo {
    suspend fun fetchAccountInfo(id: UUID2<Account>): Result<AccountInfo>
    suspend fun fetchAllAccountInfo(): Result<List<AccountInfo>>
    suspend fun updateAccountInfo(accountInfo: AccountInfo): Result<AccountInfo>
    suspend fun upsertAccountInfo(accountInfo: AccountInfo): Result<AccountInfo>
    suspend fun deleteAccountInfo(accountInfo: AccountInfo): Result<Unit>
    suspend fun deleteDatabase(): Result<Unit>
}
