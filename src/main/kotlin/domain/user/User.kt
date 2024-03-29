package domain.user

import com.realityexpander.libraryAppContext
import common.uuid2.IUUID2
import common.uuid2.UUID2
import common.uuid2.UUID2.Companion.toUUID2WithUUID2TypeOf
import common.uuid2.UUID2Result
import domain.Context
import domain.account.Account
import domain.account.data.AccountInfo
import domain.book.Book
import domain.common.Role
import domain.library.Library
import domain.user.data.IUserInfoRepo
import domain.user.data.UserInfo
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import util.JsonString

/**
 * User Role
 *
 * User is a Role Object that represents an individual User of the LibraryApp system.
 *
 * Only interacts with its own Repo, the Context, and other Role Objects
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

@Serializable(with = UserSerializer::class)  // for kotlinx.serialization
class User : Role<UserInfo>, IUUID2 {
    private val repo: IUserInfoRepo  // convenience reference to the UserInfoInMemoryRepo in the Context

    // User's Account Role Object
    private val account: Account

    constructor(
        info: UserInfo,
        account: Account,
        context: Context
    ) : super(info.id(), context) {
        this.account = account
        repo = context.userInfoRepo
        context.log.d(this, "User (" + id().toString() + ") created from Info")
    }
    constructor(
        id: UUID2<User>,
        account: Account,
        context: Context
    ) : super(id, context) {
        this.account = account
        repo = context.userInfoRepo
        context.log.d(this, "User (" + id().toString() + ") created from id with no Info")
    }
    constructor(
        userInfoJson: JsonString,
        clazzOfUserInfo: Class<UserInfo>,  // class type of json object
        account: Account,
        context: Context
    ) : super(userInfoJson, clazzOfUserInfo, context) {
        this.account = account
        repo = context.userInfoRepo
        context.log.d(this, "User (" + id().toString() + ") created Json with class: " + clazzOfUserInfo.name)
    }
    constructor(userInfoJson: JsonString, account: Account, context: Context) :
        this(userInfoJson, UserInfo::class.java, account, context)
    constructor(account: Account, context: Context) :
        this(account.id().toUUID2WithUUID2TypeOf(User::class) , account, context)
    constructor(id: UUID2<User>, context: Context) :
        this(id, Account(id.toUUID2WithUUID2TypeOf(Account::class), context), context)

    /////////////////////////
    // Simple Getters      //
    /////////////////////////

    // Gets the Type-safe id from this Class
    override fun id(): UUID2<User> {
        @Suppress("UNCHECKED_CAST")
        return super.id as UUID2<User>
    }

    override fun toString(): String {
        var str = "User (" + id().toString() + ") - "
        str += if (null != this.info.get()) "info=" + this.info.get()?.toPrettyJson(context) else "info=null"
        str += ", account=$account"
        return str
    }

    override fun toJson(): String {
        val pair: Pair<UserInfo, AccountInfo> = Pair(info.get() as UserInfo, account.info.get() as AccountInfo)
        return context.gson.toJson(pair)
    }

    /////////////////////////////////////
    // IRole/UUID2 Required Overrides  //
    /////////////////////////////////////

    override suspend fun fetchInfoResult(): Result<UserInfo> {
        // context.log.d(this,"User (" + this.id.toString() + ") - fetchInfoResult"); // LEAVE for debugging
        return repo.fetchUserInfo(id())
    }

    override suspend fun updateInfo(updatedInfo: UserInfo): Result<UserInfo> {
        context.log.d(this, "User (" + id() + ")")

        // Optimistically Update the cached UserInfo
        super.updateFetchInfoResult(Result.success(updatedInfo))

        // Update the Repo
        return repo.updateUserInfo(updatedInfo)
    }

    override fun uuid2TypeStr(): String {
        return UUID2.calcUUID2TypeStr(this.javaClass)
    }

    /////////////////////////////////////////
    // User Role Business Logic Methods    //
    // - Methods to modify it's UserInfo   //
    // - Interacts with other Role objects //
    /////////////////////////////////////////

    // Note: This delegates to its internal Account Role object.
    // - User has no intimate knowledge of the AccountInfo object, other than
    //   its public methods.
    // - Method shows how to combine User and Account Roles to achieve functionality.
    // - This method uses the UserInfo object to calculate the number of books the user has
    //   and then delegates to the AccountInfo object to determine if the
    //   number of books has reached the max.
    suspend fun acceptBook(book: Book): Result<ArrayList<Book>> {
        context.log.d(this, "User (" + id() + "),  bookId: " + book.id())
        fetchInfoFailureReason()?.let { return Result.failure(Exception(it)) }

        if (hasReachedMaxAmountOfAcceptedPublicLibraryBooks())
            return Result.failure(Exception("User (" + id() + ") has reached maximum amount of accepted Library Books"))

        val userInfo = this.info()
            ?: return Result.failure(Exception("Error finding user info"))
        val acceptResult = userInfo.acceptBook(book.id(), book.sourceLibrary().id())
        if (acceptResult.isFailure) return Result.failure(acceptResult.exceptionOrNull()
            ?: Exception("Error accepting book"))

        val updateInfoResult: Result<UserInfo> = updateInfo(userInfo)

        return if (updateInfoResult.isFailure)
            Result.failure(updateInfoResult.exceptionOrNull() ?: Exception("Error updating user info"))
        else
            findAllAcceptedBooks()
    }

    suspend fun unAcceptBook(book: Book): Result<ArrayList<UUID2<Book>>> {
        context.log.d(this, "User (" + id() + "), bookId: " + book.id())
        fetchInfoFailureReason()?.let { return Result.failure(Exception(it)) }

        val unacceptResult: Result<ArrayList<UUID2<Book>>> = this.info()?.unacceptBook(book.id())
            ?: Result.failure(Exception("Error finding user info"))
        if (unacceptResult.isFailure) {
            return Result.failure(unacceptResult.exceptionOrNull() ?: Exception("Error unaccepting book"))
        }

        val updateInfoResult: Result<UserInfo> = updateInfo(this.info()
            ?: return Result.failure(Exception("Error finding user info")))
        return if (updateInfoResult.isFailure) {
            Result.failure(updateInfoResult.exceptionOrNull() ?: Exception("Error updating user info"))
        } else
            unacceptResult
    }

    suspend fun accountInfo(): AccountInfo? {
        return account.info()
    }

    suspend fun isAccountInGoodStanding(): Boolean {
        // Note: This delegates to this User's internal Account Role object.
        context.log.d(this, "User (" + id() + ")")
        
        accountInfo()?.let { accountInfo ->
            return accountInfo.isAccountInGoodStanding
        }

        context.log.e(this, "User (" + id() + ") - AccountInfo is null")
        return false
    }

    suspend fun isAccountActive(): Boolean {
        // Note: This delegates to this User's internal Account Role object.
        context.log.d(this, "User (" + id() + ")")

        accountInfo()?.let { accountInfo ->
            return accountInfo.isAccountActive
        }

        context.log.e(this, "User (" + id() + ") - AccountInfo is null")
        return false
    }

    // Note: This delegates to this User's internal Account Role object.
    suspend fun hasReachedMaxAmountOfAcceptedPublicLibraryBooks(): Boolean {
        context.log.d(this, "User (" + id() + ")")
        
        accountInfo()?.let { accountInfo ->
            this.info()?.let { userInfo ->
                val numPublicLibraryBooksAccepted: Int = userInfo.calculateAmountOfAcceptedPublicLibraryBooks()

                // Note: This User Role Object delegates to its internal Account Role Object.
                return accountInfo.hasReachedMaxAmountOfAcceptedLibraryBooks(numPublicLibraryBooksAccepted)
            }
            
            context.log.e(this, "User (" + id() + ") - UserInfo is null")
            return false
        }
        
        context.log.e(this, "User (" + id() + ") - AccountInfo is null")
        return false
    }

    suspend fun hasAcceptedBook(book: Book): Boolean {
        context.log.d(this, "User (" + id() + "), book: " + book.id())
        return if (fetchInfoFailureReason() != null) 
            false 
        else 
            this.info()?.isBookIdAcceptedByThisUser(book.id()) 
                ?: false
    }

    suspend fun findAllAcceptedBooks(): Result<ArrayList<Book>> {
        context.log.d(this, "User (" + id() + ")")
        fetchInfoFailureReason()?.let { return Result.failure(Exception(it)) }

        // Create the list of Domain Books from the list of Accepted Book ids
        val books: ArrayList<Book> = ArrayList<Book>()
        this.info()?.let { userInfo ->

            val entries = userInfo.findAllAcceptedBookIdToLibraryIdMap().entries
            if(entries.isEmpty()) return Result.success(books)

            for ((bookId, libraryId) in entries) {
                val book = Book(bookId, Library(libraryId, context), context)
                books.add(book)
            }

            return Result.success(books)
        }

        return Result.failure(Exception("Error finding user info"))
    }

    // Note: *ONLY* the Role Objects can take a Book from one User and give it to another User.
    // - Notice that we are politely asking each Role object to Accept and UnAccept a Book.
    // - No where are there any databases being accessed directly, nor knowledge of where the data comes from.
    // - All Role interactions are SOLELY directed via the Role object's public methods. (No access to references)
    suspend fun giveBookToUser(book: Book, receivingUser: User): Result<ArrayList<Book>> {
        context.log.d(this, "User (" + id() + ") - book: " + book.id() + ", to receivingUser: " + receivingUser.id())
        fetchInfoFailureReason()?.let { return Result.failure(Exception(it)) }

        this.info()?.let { userInfo ->
            // Check this User has the Book
            if (!userInfo.isBookIdAcceptedByThisUser(book.id()))
                return Result.failure(Exception("User (" + id() + ") does not have book (" + book.id() + ")"))

            // Have Library Swap the checkout of Book from this User to the receiving User
            val swapCheckoutResult: Result<Book> = book.sourceLibrary()
                .transferBookAndCheckOutFromUserToUser(
                    book,
                    this,
                    receivingUser
                )

            return if (swapCheckoutResult.isFailure)
                Result.failure(swapCheckoutResult.exceptionOrNull() ?: Exception("Error swapping checkout of book"))
            else
                findAllAcceptedBooks()
        }

        return Result.failure(Exception("Error finding user info"))

        // LEAVE FOR REFERENCE
        // Note: No update() needed as each Role method called performs its own updates on its own Info, as needed.
        //
        // IMPORTANT NOTE!
        //   - if a local object/variable (like a hashmap) was changed after this event, an `.updateInfo(this.info)` would
        //     need to be performed.
    }

    // Convenience method to Check Out a Book from a Library
    // - Is it OK to also have this method in the Library Role Object?
    //   I'm siding with yes, since it just delegates to the Library Role Object.
    suspend fun checkOutBookFromLibrary(book: Book, library: Library): Result<UUID2<Book>> {
        context.log.d(this, "User (" + id() + "), book: " + book.id() + ", library: " + library.id())
        fetchInfoFailureReason()?.let { return Result.failure(Exception(it)) }

        // Note: Simply delegating to the Library Role Object
        val checkoutBookResult: Result<Book> = library.checkOutBookToUser(book, this)
        if (checkoutBookResult.isFailure) {
            return Result.failure(checkoutBookResult.exceptionOrNull() ?: Exception("Error checking out book"))
        }

        // Update Info, since we modified data for this User // todo - is this needed?
        val updateInfoResult: Result<UserInfo> = updateInfo(this.info()
            ?: return Result.failure(Exception("Error updating user info")))
        return if (updateInfoResult.isFailure)
            Result.failure(updateInfoResult.exceptionOrNull() ?: Exception("Error updating user info"))
        else
            checkoutBookResult.map { checkedOutBook ->
                checkedOutBook.id()
            }
    }

    // Convenience method to Check In a Book to a Library
    // - Is it OK to also have this method in the Library Role Object?
    //   I'm siding with yes, since it just delegates to the Library Role Object.
    suspend fun checkInBookToLibrary(book: Book, library: Library): Result<UUID2<Book>> {
        context.log.d(this, "User (" + id() + "), book: " + book.id() + ", library: " + library.id())
        fetchInfoFailureReason()?.let { return Result.failure(Exception(it)) }

        // Note: Simply delegating to the Library Role Object
        val bookResult: Result<Book> = library.checkInBookFromUser(book, this)
        return if (bookResult.isFailure)
            Result.failure(bookResult.exceptionOrNull() ?: Exception("Error checking in book"))
        else
            Result.success(bookResult.getOrNull()?.id() ?: return Result.failure(Exception("Error checking in book")))

        // LEAVE FOR REFERENCE
        // Note: no update() needed as each Role method called performs its own updates on its own Info, as needed.
        //
        // IMPORTANT NOTE!
        //   - if a local object/variable (like a hashmap) was changed after this event, an `.updateInfo(this.info)` would
        //     need to be performed.
    }

    companion object {

        /////////////////////////
        // Static constructors //
        /////////////////////////

        suspend fun fetchUser(id: UUID2<User>, context: Context): Result<User> {

            // get the User's UserInfo
            val userInfoResult: Result<UserInfo> = context.userInfoRepo.fetchUserInfo(id)
            if (userInfoResult.isFailure)
                return Result.failure(userInfoResult.exceptionOrNull() ?: Exception("Error fetching user info"))
            val userInfo: UserInfo = (userInfoResult.getOrNull() ?:
                return Result.failure(Exception("Error fetching user info")))

            // get the User's Account id
            val accountId: UUID2<Account> =
                id.toUUID2WithUUID2TypeOf(Account::class) // accountId is the same as userId
            val accountInfo: Result<AccountInfo> = context.accountInfoRepo.fetchAccountInfo(accountId)
            if (accountInfo.isFailure) return Result.failure(accountInfo.exceptionOrNull() ?: Exception("Error fetching account info"))

            // Get the User's Account
            val accountInfo1: AccountInfo = (accountInfo.getOrNull() ?:
                return Result.failure(Exception("Error fetching account info")))
            val account = Account(accountInfo1, context)

            // Create the User
            return Result.success(User(userInfo, account, context))
        }
    }
}

// for kotlinx.serialization
object UserSerializer : KSerializer<User> {
    override val descriptor = PrimitiveSerialDescriptor("User", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): User {
        libraryAppContext.log.e(this,"WARNING: BookSerializer.deserialize() called - DO NOT USE IN PRODUCTION." +
                "Should only be used for debugging/testing. Use the Account constructor instead.")

        @Suppress("UNCHECKED_CAST")
        return User(
            UUID2Result.serializer().deserialize(decoder).uuid2 as UUID2<User>,
            libraryAppContext
        )
    }

    override fun serialize(encoder: Encoder, value: User) {
        encoder.encodeString(value.toString())
    }
}
