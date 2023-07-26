package com.realityexpander.domain.account.data

import com.realityexpander.common.data.local.JsonFileDatabase
import common.log.ILog
import common.uuid2.UUID2
import domain.account.Account
import domain.account.data.AccountInfo
import domain.account.data.IAccountInfoRepo
import domain.common.data.repo.Repo
import kotlinx.coroutines.runBlocking


/**
 * AccountInfoRepo
 *
 * Simulates a persistent database using a local json file for the AccountInfo.
 *
 * Note: Use Domain-specific language to define the API methods.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12
 */

class AccountInfoRepo(
    log: ILog,
    accountInfoRepoDatabaseFilename: String = DEFAULT_LIBRARYINFO_REPO_DATABASE_FILENAME,

    // Use a file database to persist the account info
    private val database: JsonFileDatabase<Account, AccountInfo> = object :
        JsonFileDatabase<Account, AccountInfo>(
            accountInfoRepoDatabaseFilename,
            AccountInfo.serializer()
        ) {
        init {
            runBlocking {
                super.loadFileDatabase()
            }
        }

        override suspend fun updateLookupTables() { /* no-op */ }
    }

) : Repo(log), IAccountInfoRepo {

    override suspend fun fetchAccountInfo(id: UUID2<Account>): Result<AccountInfo> {
        return try {
            Result.success(database.findEntityById(id)
                ?: throw Exception("Account not found, id: $id")
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun fetchAllAccountInfo(): Result<List<AccountInfo>> {
        return try {
            Result.success(database.findAllEntities())
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun updateAccountInfo(accountInfo: AccountInfo): Result<AccountInfo> {
        return try {
            Result.success(database.updateEntity(accountInfo)
                ?: throw Exception("Account not found, id: ${accountInfo.id()}"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun upsertAccountInfo(accountInfo: AccountInfo): Result<AccountInfo> {
        return try {
            Result.success(database.upsertEntity(accountInfo)
                ?: throw Exception("Account upsert error, id: ${accountInfo.id()}"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun deleteAccountInfo(accountInfo: AccountInfo): Result<Unit> {
        return try {
            Result.success(database.deleteEntity(accountInfo))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun deleteDatabase(): Result<Unit> {
        return try {
            Result.success(database.deleteDatabaseFile())
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    companion object {
        const val DEFAULT_LIBRARYINFO_REPO_DATABASE_FILENAME = "accountInfoRepoDB.json"
    }
}