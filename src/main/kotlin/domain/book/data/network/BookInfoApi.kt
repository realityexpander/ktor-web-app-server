package domain.book.data.network

import common.data.network.FakeURL
import common.data.network.FakeHttpClient
import common.data.network.InMemoryAPI
import common.uuid2.UUID2
import domain.book.Book
import domain.book.data.local.EntityBookInfo

/**
 * BookInfoApi encapsulates an in-memory API database simulation for the DTOBookInfo.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */
// Note: Use Domain-specific language to define the API
class BookInfoApi internal constructor(api: InMemoryAPI<Book, DTOBookInfo>) {
    private val api: InMemoryAPI<Book, DTOBookInfo>

    constructor() : this(InMemoryAPI(FakeURL("memory://api.book.com"), FakeHttpClient()))

    init {
        this.api = api
    }

    fun fetchBookInfo(id: String): Result<DTOBookInfo> {
        return api.fetchDtoInfo(id)
    }

    fun fetchBookInfo(id: UUID2<Book>): Result<DTOBookInfo> {
        return api.fetchDtoInfo(id)
    }

    fun addBookInfo(bookInfo: DTOBookInfo): Result<DTOBookInfo> {
        return api.addDtoInfo(bookInfo)
    }

    fun updateBookInfo(bookInfo: DTOBookInfo): Result<DTOBookInfo> {
        return api.updateDtoInfo(bookInfo)
    }

    fun upsertBookInfo(bookInfo: DTOBookInfo): Result<DTOBookInfo> {
        return api.upsertDtoInfo(bookInfo)
    }

    fun deleteBookInfo(bookInfo: DTOBookInfo): Result<DTOBookInfo> {
        return api.deleteDtoInfo(bookInfo)
    }

    val allBookInfos: Map<UUID2<Book>, DTOBookInfo>
        get() = api.findAllUUID2ToDtoInfoMap()
}
