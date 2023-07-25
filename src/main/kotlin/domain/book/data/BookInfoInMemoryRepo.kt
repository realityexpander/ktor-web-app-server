package domain.book.data

import common.log.ILog
import common.log.Log
import domain.book.data.local.BookInfoInMemoryDatabase
import domain.book.data.network.BookInfoInMemoryApi

class BookInfoInMemoryRepo(
    override val log: ILog = Log(),
    private val bookInfoInMemoryApi: BookInfoInMemoryApi = BookInfoInMemoryApi(),
    private val bookInfoInMemoryDatabase: BookInfoInMemoryDatabase = BookInfoInMemoryDatabase()
) : BookInfoRepo(
        log,
        bookInfoInMemoryApi,
        bookInfoInMemoryDatabase
    )   
