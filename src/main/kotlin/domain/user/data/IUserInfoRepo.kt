package domain.user.data

import common.uuid2.UUID2
import domain.common.data.repo.IRepo
import domain.user.User

/**
 * IUserInfoRepo is an interface for the UserInfoRepo class.<br></br>
 * <br></br>
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

interface IUserInfoRepo : IRepo {
    fun fetchUserInfo(id: UUID2<User>): Result<UserInfo?>?
    fun updateUserInfo(userInfo: UserInfo): Result<UserInfo?>?
    fun upsertUserInfo(userInfo: UserInfo): Result<UserInfo?>?
}
