package domain.user.data

import common.uuid2.UUID2
import domain.book.Book
import domain.common.data.Model
import domain.common.data.info.DomainInfo
import domain.library.Library
import domain.library.PrivateLibrary
import domain.user.User
import okhttp3.internal.toImmutableMap
import java.util.*

/**
 * UserInfo is a DomainInfo class that holds the information about a User.
 *
 * Holds the information about a User, including the User's name, email, and the list of Books the User has accepted.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

class UserInfo(
    id: UUID2<User>,  // note this is a UUID2<User> not a UUID2<UserInfo>, it is the id of the User.
    val name: String,
    val email: String,
    private val acceptedBookIdToSourceLibraryIdMap: MutableMap<UUID2<Book>, UUID2<Library>> = mutableMapOf()
) : DomainInfo(id),
    Model.ToDomainInfo<UserInfo>
{
    constructor(userInfo: UserInfo) : this(
        userInfo.id(),
        userInfo.name,
        userInfo.email,
        userInfo.acceptedBookIdToSourceLibraryIdMap
    )
    constructor(
        uuid: UUID,
        name: String,
        email: String,
        acceptedBookIdToSourceLibraryIdMap: MutableMap<UUID2<Book>, UUID2<Library>>
    ) : this(UUID2<User>(uuid, User::class.java), name, email, acceptedBookIdToSourceLibraryIdMap)
    constructor(uuid2: UUID2<User>, name: String, email: String) : this(uuid2, name, email, mutableMapOf())
    constructor(uuid: UUID, name: String, email: String) : this(UUID2<User>(uuid, User::class.java), name, email)
    constructor(uuid: String, name: String, email: String) : this(UUID.fromString(uuid), name, email)

    ///////////////////////////////
    // Published Simple Getters  //  // note: no setters, all changes are made through business logic methods.
    ///////////////////////////////

    // Convenience method to get the Type-safe id from the Class
    override fun id(): UUID2<User> {
        @Suppress("UNCHECKED_CAST")
        return this.id as UUID2<User> // todo remove and use direct val access
    }

    ////////////////////////////////////////
    // User Info Business Logic Methods   //
    ////////////////////////////////////////

    fun isBookIdAcceptedByThisUser(bookId: UUID2<Book>): Boolean {
        return acceptedBookIdToSourceLibraryIdMap.containsKey(bookId)
    }

    fun acceptBook(bookId: UUID2<Book>, LibraryId: UUID2<Library>): Result<ArrayList<UUID2<Book>>> {
        if (acceptedBookIdToSourceLibraryIdMap.containsKey(bookId))
            return Result.failure(Exception("Book already accepted by user, book id:$bookId"))

        try {
            acceptedBookIdToSourceLibraryIdMap[bookId] = LibraryId
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(findAllAcceptedBookIds())
    }

    fun unacceptBook(bookId: UUID2<Book>): Result<ArrayList<UUID2<Book>>> {
        if (!acceptedBookIdToSourceLibraryIdMap.containsKey(bookId)) {
            return Result.failure(Exception("Book not in accepted Books List for user, book id:$bookId"))
        }

        try {
            acceptedBookIdToSourceLibraryIdMap.remove(bookId)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(findAllAcceptedBookIds())
    }

    ////////////////////////////////////////
    // Published Reporting Methods        //
    ////////////////////////////////////////

    fun findAllAcceptedBookIds(): ArrayList<UUID2<Book>> {
        return ArrayList<UUID2<Book>>(
            acceptedBookIdToSourceLibraryIdMap.keys
        )
    }

    fun findAllAcceptedBookIdToLibraryIdMap(): Map<UUID2<Book>, UUID2<Library>> {
        return acceptedBookIdToSourceLibraryIdMap.toImmutableMap()
    }

    private fun findAllAcceptedPublicLibraryBookIds(): ArrayList<UUID2<Book>> {
        val acceptedPublicLibraryBookIds: ArrayList<UUID2<Book>> = ArrayList<UUID2<Book>>()

        for (bookId in acceptedBookIdToSourceLibraryIdMap.keys) {
            if (acceptedBookIdToSourceLibraryIdMap[bookId]
                    ?.uuid2Type
                    .equals(UUID2.calcUUID2TypeStr(Library::class.java))
            ) {
                acceptedPublicLibraryBookIds.add(bookId)
            }
        }

        return acceptedPublicLibraryBookIds
    }

    fun findAllAcceptedPrivateLibraryBookIds(): ArrayList<UUID2<Book>> {
        val acceptedPrivateLibraryBookIds: ArrayList<UUID2<Book>> = ArrayList<UUID2<Book>>()

        for (bookId in acceptedBookIdToSourceLibraryIdMap.keys) {
            if (acceptedBookIdToSourceLibraryIdMap[bookId]
                    ?.uuid2Type
                    .equals(UUID2.calcUUID2TypeStr(PrivateLibrary::class.java))
            ) {
                acceptedPrivateLibraryBookIds.add(bookId)
            }
        }

        return acceptedPrivateLibraryBookIds
    }

    fun calculateAmountOfAcceptedBooks(): Int {
        return acceptedBookIdToSourceLibraryIdMap.size
    }

    fun calculateAmountOfAcceptedPublicLibraryBooks(): Int {
        return findAllAcceptedPublicLibraryBookIds().size
    }

    fun calculateAmountOfAcceptedPrivateLibraryBooks(): Int {
        return findAllAcceptedPrivateLibraryBookIds().size
    }

    ///////////////////////////////
    // ToDomain implementation   //
    ///////////////////////////////

    // note: no DB or API for UserInfo (so no .ToEntity() or .ToDTO())
    override fun toDeepCopyDomainInfo(): UserInfo {
        // Note: Must return a deep copy (no original references)
        val domainInfoCopy = UserInfo(this)

        // deep copy of acceptedBooks
        domainInfoCopy.acceptedBookIdToSourceLibraryIdMap.clear()
        for (bookId in acceptedBookIdToSourceLibraryIdMap.keys) {

            domainInfoCopy.acceptedBookIdToSourceLibraryIdMap[bookId]?.let { libraryUUID2 ->
                this.acceptedBookIdToSourceLibraryIdMap[bookId] = libraryUUID2
            }
        }

        return domainInfoCopy
    }
}