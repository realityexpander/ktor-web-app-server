
import common.uuid2.UUID2
import domain.Context
import domain.account.Account
import domain.account.data.AccountInfo
import domain.book.Book
import domain.book.data.BookInfo
import domain.book.data.local.EntityBookInfo
import domain.book.data.network.DTOBookInfo
import domain.library.Library
import domain.library.data.LibraryInfo
import domain.user.User
import domain.user.data.UserInfo
import java.time.Instant

/**
 * Testing Utility Methods
 *
 * Useful for adding fake data to the Repos, DB and API for testing purposes.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

class TestingUtils(val context: Context) {

    ///////////////////////////
    // Book Repo, DB and API //
    ///////////////////////////

    suspend fun populateFakeBookInfoInBookRepoDBandAPI() {
        populateDBWithFakeBookInfo()
        populateApiWithFakeBookInfo()
    }

    suspend fun populateDBWithFakeBookInfo() {
        for (i in 0..9) {
            val id = 1000 + i * 100
            val result: Result<BookInfo> = context.bookInfoRepo
                .upsertTestEntityBookInfoToDB(
                    EntityBookInfo(
                        UUID2.createFakeUUID2(id, Book::class.java),
                        "Title $id",
                        "Author $id",
                        "Description $id",
                        "Some extra info from the Entity$id",
                        Instant.parse("2023-01-01T00:00:00.00Z").toEpochMilli(),
                        Instant.parse("2023-01-01T00:00:00.00Z").toEpochMilli(),
                        false
                    )
                )
            if (result.isFailure) {
                context.log.d(this, "Error: " + result.exceptionOrNull()?.message)
            }
        }
    }

    suspend fun populateApiWithFakeBookInfo() {
        for (i in 0..9) {
            val id = 1000 + i * 100
            val result: Result<BookInfo> = context.bookInfoRepo.upsertTestDTOBookInfoToApi(
                DTOBookInfo(
                    UUID2.createFakeUUID2(id, Book::class.java),
                    "Title $id",
                    "Author $id",
                    "Description $id",
                    "Some extra info from the DTO$id",
                    Instant.parse("2023-01-01T00:00:00.00Z").toEpochMilli(),
                    Instant.parse("2023-01-01T00:00:00.00Z").toEpochMilli(),
                    false
                )
            )
            if (result.isFailure) {
                context.log.d(this, "Error: " + result.exceptionOrNull()?.message)
            }
        }
    }

    suspend fun printBookInfoDBandAPIEntries() {
        print("\n")
        context.log.d(this, "DB Dump")
        context.bookInfoRepo.printDB()
        print("\n")
        context.log.d(this, "API Dump")
        context.bookInfoRepo.printAPI()
        print("\n")
    }

    suspend fun addFakeBookInfoToBookInfoRepo(id: Int?): Result<BookInfo> {
        val bookInfo: BookInfo = createFakeBookInfo(id)
        return context.bookInfoRepo.upsertBookInfo(bookInfo)
    }

    fun createFakeBookInfo(id: Int?): BookInfo {
        var fakeId = id
        if (fakeId == null) fakeId = 1
        val uuid2: UUID2<Book> = UUID2.createFakeUUID2(fakeId, Book::class.java)
        
        return BookInfo(
            uuid2,
            "Book $fakeId",
            "Author $fakeId",
            "Description $fakeId",
            Instant.parse("2023-01-01T00:00:00.00Z").toEpochMilli(),
            Instant.parse("2023-01-01T00:00:00.00Z").toEpochMilli(),
            false
        )
    }

    ////////////////
    // User Repo  //
    ////////////////

    fun createFakeUserInfoInUserInfoRepo(id: Int?): Result<UserInfo> {
        var someNumber = id
        if (someNumber == null) someNumber = 1

        val upsertUserInfoResult: Result<UserInfo> = context.userInfoRepo
            .upsertUserInfo(
                UserInfo(
                    UUID2.createFakeUUID2(someNumber, User::class.java),  // uses DOMAIN id
                    "User $someNumber",
                    "user$someNumber@gmail.com"
                )
            )
        if (upsertUserInfoResult.isFailure) {
            context.log.d(
                this,
                "User Error: " + upsertUserInfoResult.exceptionOrNull()?.message
            )
        }

        return upsertUserInfoResult
    }

    ///////////////////
    // Account Repo  //
    ///////////////////

    fun createFakeAccountInfoInAccountRepo(id: Int?): Result<AccountInfo> {
        var someNumber = id
        if (someNumber == null) someNumber = 1

        val accountInfoResult: Result<AccountInfo> = context.accountInfoRepo
            .upsertAccountInfo(
                AccountInfo(
                    UUID2.createFakeUUID2(someNumber, Account::class.java),  // uses DOMAIN id
                    "Account for User $someNumber"
                )
            )
        if (accountInfoResult.isFailure) {
            context.log.d(this, "Account Error: " + accountInfoResult.exceptionOrNull()?.message)
            return accountInfoResult
        }

        val accountInfo: AccountInfo = (accountInfoResult.getOrNull()
            ?: return Result.failure(Exception("AccountInfo was null.")))
        accountInfo.addTestAuditLogMessage("AccountInfo created for User $someNumber")

        return accountInfoResult
    }

    fun populateAccountWithFakeAuditMessages(
        accountId: UUID2<Account>,
        numberOfMessagesToCreate: Int
    ) {
        context.log.d(this, "accountId: $accountId, numberOfAccountsToCreate: $numberOfMessagesToCreate")
        val infoResult: Result<AccountInfo> = context.accountInfoRepo.fetchAccountInfo(accountId)
        if (infoResult.isFailure) {
            context.log.d(this, "Error: " + infoResult.exceptionOrNull()?.message)
            return
        }

        val accountInfo: AccountInfo = (infoResult.getOrNull() ?: return)
        for (i in 0 until numberOfMessagesToCreate) {
            accountInfo.addTestAuditLogMessage(
                "Test Audit message " + i + " for account: " + accountInfo.id()
            )
        }
    }

    ///////////////////
    // Library Repo  //
    ///////////////////

    fun createFakeLibraryInfoInLibraryInfoRepo(id: Int?): Result<LibraryInfo> {
        var someNumber = id
        if (someNumber == null) someNumber = 1

        return context.libraryInfoRepo
            .upsertLibraryInfo(
                LibraryInfo(
                    UUID2.createFakeUUID2(someNumber, Library::class.java),  // uses DOMAIN id
                    "Library $someNumber"
                )
            )
    }

    fun populateLibraryWithFakeBooks(
        libraryId: UUID2<Library>,
        numberOfBooksToCreate: Int
    ) {
        context.log.d(this, "libraryId: $libraryId, numberOfBooksToCreate: $numberOfBooksToCreate")
        val libraryInfoResult: Result<LibraryInfo> = context.libraryInfoRepo.fetchLibraryInfo(libraryId)
        if (libraryInfoResult.isFailure) {
            context.log.d(this, "Error: " + libraryInfoResult.exceptionOrNull()?.message)
            return
        }

        val libraryInfo: LibraryInfo = libraryInfoResult.getOrNull() ?: return

        for (i in 0 until numberOfBooksToCreate) {
            val addBookResult: Result<UUID2<Book>> =
                libraryInfo.addTestBook(UUID2.createFakeUUID2(1000 + i * 100, Book::class.java), 1)
            if (addBookResult.isFailure) {
                context.log.e(this, "Error: " + addBookResult.exceptionOrNull()?.message)
            }
        }

//        // Update the Library with the new Info
//        val updateLibraryResult: Result<LibraryInfo> = context.libraryInfoRepo.upsertLibraryInfo(libraryInfo)
//        if (updateLibraryResult.isFailure) {
//            context.log.e(this, "Error: " + updateLibraryResult.exceptionOrNull()?.message)
//        }
    }
}
