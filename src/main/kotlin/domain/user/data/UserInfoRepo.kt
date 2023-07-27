package domain.user.data

import com.realityexpander.common.data.local.JsonFileDatabase
import common.log.ILog
import common.uuid2.UUID2
import domain.common.data.repo.Repo
import domain.user.User
import kotlinx.coroutines.runBlocking

/**
 * UserInfoRepo
 *
 * Simulates a persistent database using a local json file for the DTOUserInfo.
 *
 * Note: Use Domain-specific language to define the API methods.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12
 */

class UserInfoRepo(
    log: ILog,
    userRepoDatabaseFilename: String = UserInfoRepo.DEFAULT_USERINFO_REPO_DATABASE_FILENAME,

    // Use a file database to persist the user info
    private val database: JsonFileDatabase<User, UserInfo> = object :
        JsonFileDatabase<User, UserInfo>(
            userRepoDatabaseFilename,
            UserInfo.serializer()
        ) {
        init {
            runBlocking {
                super.loadFileDatabase()
            }
        }

        override suspend fun updateLookupTables() { /* no-op */ }
    }
) : Repo(log), IUserInfoRepo {

    override suspend fun fetchUserInfo(id: UUID2<User>): Result<UserInfo> {
        return try {
            Result.success(database.findEntityById(id)
                ?: throw Exception("User not found, id: $id")
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun fetchAllUserInfo(): Result<List<UserInfo>> {
        return try {
            Result.success(database.findAllEntities())
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun updateUserInfo(userInfo: UserInfo): Result<UserInfo> {
        return try {
            Result.success(database.updateEntity(userInfo)
                ?: throw Exception("User not found, id: ${userInfo.id()}"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun upsertUserInfo(userInfo: UserInfo): Result<UserInfo> {
        return try {
            Result.success(database.upsertEntity(userInfo)
                ?: throw Exception("User upsert error, id: ${userInfo.id()}"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun deleteUserInfo(userInfo: UserInfo): Result<Unit> {
        return try {
            if(database.findEntityById(userInfo.id()) == null)
                throw Exception("User not found, id: ${userInfo.id()}")

            Result.success(database.deleteEntity(userInfo))
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
        const val DEFAULT_USERINFO_REPO_DATABASE_FILENAME = "userInfoRepoDB.json"
    }
}