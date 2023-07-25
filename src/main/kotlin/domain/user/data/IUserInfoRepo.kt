package domain.user.data

import common.uuid2.UUID2
import domain.common.data.repo.IRepo
import domain.user.User

/**
 * IUserInfoRepo is an interface for the UserInfoInMemoryRepo class.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

interface IUserInfoRepo : IRepo {
    suspend fun fetchUserInfo(id: UUID2<User>): Result<UserInfo>
    suspend fun fetchAllUserInfo(): Result<List<UserInfo>>
    suspend fun updateUserInfo(userInfo: UserInfo): Result<UserInfo>
    suspend fun upsertUserInfo(userInfo: UserInfo): Result<UserInfo>
    suspend fun deleteUserInfo(userInfo: UserInfo): Result<Unit>
    suspend fun deleteDatabase(): Result<Unit>
}
