package domain.user.data
//
//import common.data.network.FileApi
//import common.uuid2.UUID2
//import domain.user.User
//
///**
// * UserInfoFileApi
// *
// * Simulates a persistent API using a local file database for the DTOUserInfo.
// *
// * Note: Use Domain-specific language to define the API methods.
// *
// * @author Chris Athanas (realityexpanderdev@gmail.com)
// * @since 0.12
// */
//
//class UserInfoFileApi(
//    userInfoFileApiDatabaseFilename: String = DEFAULT_USERINFO_REPO_DATABASE_FILENAME,
//
//    // Use a file database to persist the book info
//    private val api: FileApi<User, DTOUserInfo> =
//        FileApi(
//            userInfoFileApiDatabaseFilename,
//            DTOUserInfo.serializer()
//        )
//) {
//
//    suspend fun fetchUserInfo(id: UUID2<User>): Result<DTOUserInfo> {
//        return api.fetchDtoInfo(id)
//    }
//
//    suspend fun allUserInfos(): Result<Map<UUID2<User>, DTOUserInfo>> {
//        return api.findAllUUID2ToDtoInfoMap()
//    }
//
//    suspend fun addUserInfo(bookInfo: DTOUserInfo): Result<DTOUserInfo> {
//        return api.addDtoInfo(bookInfo)
//    }
//
//    suspend fun updateUserInfo(bookInfo: DTOUserInfo): Result<DTOUserInfo> {
//        return api.updateDtoInfo(bookInfo)
//    }
//
//    suspend fun upsertUserInfo(bookInfo: DTOUserInfo): Result<DTOUserInfo> {
//        return api.upsertDtoInfo(bookInfo)
//    }
//
//    suspend fun deleteUserInfo(bookInfo: DTOUserInfo): Result<DTOUserInfo> {
//        return api.deleteDtoInfo(bookInfo)
//    }
//
//    suspend fun deleteDatabase(): Result<Unit> {
//        return api.deleteAllDtoInfo()
//    }
//
//    companion object {
//        const val DEFAULT_USERINFO_REPO_DATABASE_FILENAME = "userInfoRepoDB.json"
//    }
//}