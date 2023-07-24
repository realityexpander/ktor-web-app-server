package domain.account.data

import common.uuid2.UUID2
import domain.account.Account
import domain.common.data.repo.IRepo

/**
 * IAccountInfoRepo is an interface for the AccountInfoRepo class.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

interface IAccountInfoRepo : IRepo {
    fun fetchAccountInfo(id: UUID2<Account>): Result<AccountInfo>
    fun updateAccountInfo(accountInfo: AccountInfo): Result<AccountInfo>
    fun upsertAccountInfo(accountInfo: AccountInfo): Result<AccountInfo>
    suspend fun deleteDatabase(): Result<Unit>
}
