package domain.user.data

import common.log.ILog
import common.uuid2.UUID2
import domain.common.data.repo.Repo
import domain.user.User

/**
 * UserInfoRepo is a repository for UserInfo objects.
 *
 * Holds User info for all the users in the system (simple CRUD operations).
 *
 * Simulates a database on a server via in-memory HashMap.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

class UserInfoRepo(log: ILog) : Repo(log), IUserInfoRepo {
    // Simulate a database on a server somewhere
    private val database: MutableMap<UUID2<User>, UserInfo> = mutableMapOf()

    override fun fetchUserInfo(id: UUID2<User>): Result<UserInfo> {
        log.d(this, "userId: $id")

        // Simulate network/database
        return if (database.containsKey(id))
            Result.success(database[id] ?: return Result.failure(Exception("Repo.UserInfo, UserInfo not found, id:$id")))
        else
            Result.failure(Exception("Repo.UserInfo, UserInfo not found, id:$id"))
    }

    override fun updateUserInfo(userInfo: UserInfo): Result<UserInfo> {
        val methodName = Thread.currentThread().stackTrace[2].methodName  // todo remove?
        log.d(this, methodName + ", userId:" + userInfo.id())

        // Simulate network/database
        if (database.containsKey(userInfo.id())) {
            database[userInfo.id()] = userInfo
            return Result.success(userInfo)
        }

        return Result.failure(Exception("Repo.UserInfo, UserInfo not found, id:" + userInfo.id()))
    }

    override fun upsertUserInfo(userInfo: UserInfo): Result<UserInfo> {
        val methodName = Thread.currentThread().stackTrace[2].methodName // todo remove?
        log.d(this, methodName + ", userId:" + userInfo.id())

        // Simulate network/database
        database[userInfo.id()] = userInfo

        return Result.success(userInfo)
    }

    override suspend fun deleteDatabase(): Result<Unit> {
        log.d(this, "deleteDatabase")

        // Simulate network/database
        database.clear()
        return Result.success(Unit)
    }
}
