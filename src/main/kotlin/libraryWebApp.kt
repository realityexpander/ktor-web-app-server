package com.realityexpander

import common.uuid2.UUID2.Companion.fromUUID2StrToTypedUUID2
import domain.book.Book
import domain.book.data.BookInfo
import domain.library.Library
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import util.parseTextPlainEncodedFormParameters
import util.respondJson
import util.testingUtils.TestingUtils

fun Routing.libraryWebApp() {
    route("/libraryWeb") {

        // more examples: https://photos.google.com/photo/AF1QipNkHSZiBPmtvSZWguN_BEaBmN7a5xansX3DSRHc

        get("/") {
            call.respondHtml {
                bootstrapHeader()

                body {
                    libraryStyle()

                    h1 { +"Library App" }
                    p { +"Welcome to the Library App" }
                    br

                    // search for books
                    h2 { +"Search for Books" }
                    div {
                        classes = setOf("container")
                        form {
                            method = FormMethod.get
                            action = "./findBook"
                            encType = FormEncType.textPlain // uses key/value pairs, one per line

                            div("form-group row") {
                                label("col-sm-2 col-form-label") {
                                    htmlFor = "id2"
                                    +"UUID2 id"
                                }
                                div("col-sm-10") {
                                    input(InputType.text) {
                                        classes = setOf("form-control")
                                        id = "id"
                                        name = "id"
                                        //+"UUID2:Book@"
                                        placeholder = "UUID2:Book@..."
                                    }
                                }
                            }
                            br

                            div("form-group row") {
                                label("col-sm-2 col-form-label") {
                                    htmlFor = "title"
                                    +"Title"
                                }
                                div("col-sm-10") {
                                    input(InputType.text) {
                                        classes = setOf("form-control")
                                        id = "title"
                                        name = "title"
                                        //+"UUID2:Book@"
                                        placeholder = "Title of book"
                                    }
                                }
                            }
                            br

                            div("form-group row") {
                                label("col-sm-2 col-form-label") {
                                    htmlFor = "author"
                                    +"Author"
                                }
                                div("col-sm-10") {
                                    input(InputType.text) {
                                        classes = setOf("form-control")
                                        id = "author"
                                        name = "author"
                                        //+"Author"
                                        placeholder = "Author of book"
                                    }
                                }
                            }
                            br

                            div("form-group row") {
                                label("col-sm-2 col-form-label") {
                                    htmlFor = "description"
                                    +"Description"
                                }
                                div("col-sm-10") {
                                    input(InputType.text) {
                                        classes = setOf("form-control")
                                        id = "description"
                                        name = "description"
                                        //+"Description"
                                        placeholder = "Description of book"
                                    }
                                }
                            }
                            br

                            submitInput {
                                classes = setOf("btn", "btn-primary", "btn-block")
                                value = "Search Book Info"
                            }
                        }
                    }
                    br
                    br

                    h2 { +"Other Library Functions" }

                    a {
                        href = "listBooks/UUID2:Role.Library@00000000-0000-0000-0000-000000000001"
                        +"List Books for Library: 1"
                    }
                    br

                    a {
                        href = "upsertBookToLibrary/UUID2:Role.Library@00000000-0000-0000-0000-000000000001"
                        +"Upsert Book to Library: 1"
                    }
                    br
                    br

                    a {
                        href = "populateBookInfo"
                        +"Populate BookInfo into Database/API"
                    }
                }
            }
        }

        get("/listBooks/{libraryId}") {
            val libraryId = call.parameters["libraryId"]?.toString()
            libraryId ?: run {
                call.respondJson(mapOf("error" to "Missing libraryId"), HttpStatusCode.BadRequest)
                return@get
            }
            // val userIdResult = call.getUserIdFromCookies()

            val library = Library(libraryId.fromUUID2StrToTypedUUID2<Library>(), libraryAppContext)
            val bookInfoResult = library.fetchInfoResult()
            if (bookInfoResult.isFailure) {
                call.respondJson(
                    mapOf(
                        "error" to (bookInfoResult.exceptionOrNull()?.localizedMessage ?: "Unknown error")
                    ), HttpStatusCode.BadRequest
                )
                return@get
            }
            val bookInfo = bookInfoResult.getOrThrow()
            val books = bookInfo.findAllKnownBookIds().map { Book(it, libraryAppContext) }
            val bookInfos = books.mapNotNull { book ->
                book.info()
            }

            call.respondHtml {
                bootstrapHeader()

                body {
                    libraryStyle()

                    h1 { +"List Books for Library" }

                    div("container") {
                        div("row") {
                            div("col-sm-2") { +"Library id:" }
                            div("col-sm-10") {
                                +libraryId
                            }
                        }
                    }
                    br
                    br

                    bookInfos.forEach { bookInfo ->
                        div("container") {
                            div("row") {
                                div("col-sm-2") { +"UUID2 id:" }
                                div("col-sm-10") {
                                    +bookInfo.id.toString()
                                }
                            }
                            div("row") {
                                div("col-sm-2") { +"Title:" }
                                div("col-sm-10") { +bookInfo.title }
                            }
                            div("row") {
                                div("col-sm-2") { +"Author:" }
                                div("col-sm-10") { +bookInfo.author }
                            }
                            div("row") {
                                div("col-sm-2") { +"Description:" }
                                div("col-sm-10") { +bookInfo.description }
                            }
                            //    a {
                            //        href = "../book/${bookInfo.id}"
                            //        +"View"
                            //    }

                            div("d-grid gap-2 d-md-flex justify-content-md-start pt-2") {
                                button(type = ButtonType.button) {
                                    classes = setOf("btn", "btn-danger")
                                    onClick = """confirmDeleteBookInfo(`${bookInfo.id}`, `${bookInfo.title}`)"""
                                    +"Delete"
                                }
                                button(type = ButtonType.button) {
                                    classes = setOf("btn", "btn-primary", "btn-block")
                                    onClick = "viewBook(`${bookInfo.id}`)"
                                    +"View"
                                }
                            }
                            br
                        }
                    }
                    br

                    // go to upsert book page
                    a {
                        href = "../upsertBook/$libraryId"
                        +"Upsert Book to Library"
                    }

                    script {
                        type = ScriptType.textJavaScript
                        unsafe {
                            raw(
                                """ 
                                function viewBook(bookId) {
                                    window.location.href = `../book/${'$'}{bookId}`
                                }
                                
                                function confirmDeleteBookInfo(bookId, title) {
                                    if (confirm(`Are you sure you want to delete this book? ${'$'}{title}`)) {
                                        fetch(`/libraryApi/deleteBookInfo/${'$'}{bookId}`, {
                                            method: "DELETE",
                                            headers: {
                                                "Content-Type": "application/json",
                                                "Accept": "application/json",
                                                "Authorization": "Bearer " + document.cookie
                                                    .split(";")
                                                    .find((item) => item.includes("authenticationToken"))
                                                    .split("=")[1],
                                                "X-Forwarded-For": document.cookie
                                                    .split(";")
                                                    .find((item) => item.includes("clientIpAddress"))
                                                    .split("=")[1]
                                            }
                                            //,
                                            //body: JSON.stringify({
                                            //    bookId: bookId
                                            //})
                                        })
                                        .then( res => {
                                            if(res.status === 401) {
                                                alert("Unauthorized")
                                                // window.location.href = "/login" // todo - redirect to login page
                                                return Promise.reject("Unauthorized")
                                            }
                                            
                                            return res.json()
                                        })
                                        .then(res => {
                                            if (res.error) {
                                                alert(res.error)
                                            } else {
                                                alert("Book deleted")
                                                window.location.reload()
                                            }
                                        })
                                    }
                                }
                            """.trimIndent()
                            )
                        }
                    }
                }
            }
        }

        get("/book/{bookId}") {
            val bookId = call.parameters["bookId"]?.toString()
            bookId ?: run {
                call.respondJson(mapOf("error" to "Missing bookId"), HttpStatusCode.BadRequest)
                return@get
            }
            // val userIdResult = call.getUserIdFromCookies()

            val book = Book(bookId.fromUUID2StrToTypedUUID2<Book>(), libraryAppContext)
            val bookInfoResult = book.fetchInfoResult()
            if (bookInfoResult.isFailure) {
                call.respondJson(
                    mapOf(
                        "error" to (bookInfoResult.exceptionOrNull()?.localizedMessage ?: "Unknown error")
                    ), HttpStatusCode.BadRequest
                )
                return@get
            }
            val bookInfo = bookInfoResult.getOrThrow()

            call.respondHtml {
                bootstrapHeader()

                body {
                    h1 { +"Book" }

                    div("container") {
                        div("row") {
                            div("col-sm-2") { +"UUID2 id:" }
                            div("col-sm-10") {
                                +bookInfo.id.toString()
                            }
                        }
                        div("row") {
                            div("col-sm-2") { +"Title:" }
                            div("col-sm-10") { +bookInfo.title }
                        }
                        div("row") {
                            div("col-sm-2") { +"Author:" }
                            div("col-sm-10") { +bookInfo.author }
                        }
                        div("row") {
                            div("col-sm-2") { +"Description:" }
                            div("col-sm-10") { +bookInfo.description }
                        }
                    }

                    // go to home
                    a {
                        href = "../"
                        +"Home"
                    }
                }
            }
        }

        get("/upsertBookToLibrary/{libraryId}") {
            val libraryIdFromParams = call.parameters["libraryId"]?.toString()
            val libraryId = libraryIdFromParams ?: "UUID2:Role.Library@00000000-0000-0000-0000-000000000001"

            call.respondHtml {
                bootstrapHeader()

                body {
                    libraryStyle()

                    h1 { +"Upsert Book to Library" }

                    button {
                        onClick = "generateBookId()"
                        +"Generate Book Id"
                    }
                    br

                    form {
                        method = FormMethod.post
                        action = "../upsertBookToLibrary"
                        encType = FormEncType.textPlain // uses key/value pairs, one per line

                        label {
                            +"Book Id"
                            textInput {
                                name = "UUID2:Book"
                                value = "UUID2:Role.Book@00000000-0000-0000-0000-000000000001"
                            }
                        }
                        button(type = ButtonType.button) {
                            onClick = "generateBookId()"
                            +"Generate Book Id"
                        }

                        label {
                            +"Title"
                            textInput {
                                name = "title"
                            }
                        }
                        br
                        label {
                            +"Author"
                            textInput {
                                name = "author"
                            }
                        }
                        br
                        label {
                            +"Description"
                            textInput {
                                name = "description"
                            }
                        }
                        br
                        label {
                            +"ISBN"
                            textInput {
                                name = "isbn"
                            }
                        }
                        br
                        label {
                            +"Library Id"
                            textInput {
                                name = "libraryId"
                                value = "UUID2:Role.Library@00000000-0000-0000-0000-000000000001"
                            }
                        }
                        br

                        submitInput {
                            value = "Upsert Book"
                        }
                    }

                    // go to list books page
                    a {
                        href = "../listBooks/$libraryId"
                        +"List Books for Library: $libraryId"
                    }

                    script {
                        type = ScriptType.textJavaScript
                        unsafe {
                            raw(
                                """ 
                                function generateUUID() { // Public Domain/MIT
                                    var d = new Date().getTime();//Timestamp
                                    var d2 = (performance && performance.now && (performance.now()*1000)) || 0;//Time in microseconds since page-load or 0 if unsupported
                                    return 'xxxxxxxx-xxxx-xxxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
                                        var r = Math.random() * 16;//random number between 0 and 16
                                        if(d > 0){//Use timestamp until depleted
                                            r = (d + r)%16 | 0;
                                            d = Math.floor(d/16);
                                        } else {//Use microseconds since page-load if supported
                                            r = (d2 + r)%16 | 0;
                                            d2 = Math.floor(d2/16);
                                        }
                                        return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
                                    });
                                }
                                
                                function generateBookId(e) {
                                    document.getElementsByName("UUID2:Book")[0].value = "UUID2:Role.Book@" + generateUUID();
                                }
                            """.trimIndent()
                            )
                        }
                    }
                }
            }
        }
        post("/upsertBookToLibrary") {
            val body = call.receiveText().decodeURLPart(charset = Charsets.UTF_8)
            // val params = body.parseUrlEncodedParameters()
            val params = body.parseTextPlainEncodedFormParameters()
            val bookId = params["UUID2:Book"]
            val libraryId = params["libraryId"]

            if (bookId != null &&
                libraryId != null
            ) {
                val library = Library(libraryId.fromUUID2StrToTypedUUID2<Library>(), libraryAppContext)
                val book = Book(bookId.fromUUID2StrToTypedUUID2<Book>(), libraryAppContext)
                val bookInfoResult = book.fetchInfoResult()
                val bookInfoResultStatusJson = resultToStatusCodeJson<BookInfo>(bookInfoResult)

                val addBookToLibraryResult = library.addBookToLibrary(book, 1)
                val addBookToLibraryStatusJson: StatusCodeAndJson =
                    resultToStatusCodeJson<Book>(addBookToLibraryResult)

                call.respondHtml {
                    bootstrapHeader()

                    body {
                        if (bookInfoResultStatusJson.statusCode != HttpStatusCode.OK) {
                            h1 { +"Error adding book to library" }
                            p { +bookInfoResultStatusJson.json }
                        } else if (addBookToLibraryStatusJson.statusCode != HttpStatusCode.OK) {
                            h1 { +"Error adding book to library" }
                            p { +addBookToLibraryStatusJson.json }
                        } else {
                            h1 { +"Book added:" }
                        }

                        p { +"Book Id: $bookId" }
                        p { +"Title: ${book.info.get().title}" }
                        p { +"Author: ${book.info.get().author}" }
                        p { +"Description: ${book.info.get().description}" }
                        p { +"Library Id: $libraryId" }

                        // go to list books page
                        a {
                            href = "../listBooks/$libraryId"
                            +"List Books for Library: $libraryId"
                        }
                    }
                }
                return@post
            }

            call.respondJson(mapOf("error" to "Invalid parameters"), HttpStatusCode.BadRequest)
        }

        get("/findBook") {
            val searchId = call.request.queryParameters["id"]
            val searchTitle = call.request.queryParameters["title"]
            val searchAuthor = call.request.queryParameters["author"]
            val searchDescription = call.request.queryParameters["description"]

            if (searchId != null) {
                call.respondRedirect("./findBook/id/$searchId")
                return@get
            }
            if (searchTitle != null) {
                call.respondRedirect("./findBook/title/$searchTitle")
                return@get
            }
            if (searchAuthor != null) {
                call.respondRedirect("./findBook/author/$searchAuthor")
                return@get
            }
            if (searchDescription != null) {
                call.respondRedirect("./findBook/description/$searchDescription")
                return@get
            }

            // no search parameters
            call.respondHtml {
                bootstrapHeader()

                body {
                    h1 { +"No search criteria given for Book" }
                }
            }
        }
        get("/findBook/{field}/{searchTerm}") {
            val field = call.parameters["field"]?.toString() ?: run {
                call.respondJson(mapOf("error" to "Missing field"), HttpStatusCode.BadRequest)
                return@get
            }
            val searchTerm = call.parameters["searchTerm"]?.toString() ?: run {
                call.respondJson(mapOf("error" to "Missing title"), HttpStatusCode.BadRequest)
                return@get
            }

            val bookInfosResult = libraryAppContext.bookInfoRepo.findBookInfosByField(field, searchTerm)
            val bookInfos = bookInfosResult.getOrThrow()

            call.respondHtml {
                bootstrapHeader()

                body {
                    h1 { +"Book Info for $field search: $searchTerm" }
                    if (bookInfos.isEmpty()) {
                        p { +"No results found" }
                    }
                    bookInfos.forEach { bookInfo ->
                        p { +"Title: ${bookInfo.title}" }
                        p { +"Author: ${bookInfo.author}" }
                        p { +"Description: ${bookInfo.description}" }
                        p { +"Id: ${bookInfo.id}" }
                        br
                    }
                }
            }
        }

        get("/addBookInfo") {
            call.respondHtml {
                bootstrapHeader()

                body {
                    h1 { +"Add Book Info" }

                    div {
                        classes = setOf("container")
                        form {
                            method = FormMethod.post
                            action = "./addBookInfo"
                            encType = FormEncType.textPlain // uses key/value pairs, one per line

                            div("form-group row") {
                                label("col-sm-2 col-form-label") {
                                    htmlFor = "id2"
                                    +"UUID2 id"
                                }
                                div("col-sm-10") {
                                    input(InputType.text) {
                                        classes = setOf("form-control")
                                        id = "id"
                                        name = "id"
                                        //+"UUID2:Book@"
                                        placeholder = "UUID2:Book@..."
                                    }
                                }
                            }
                            br

                            div("form-group row") {
                                label("col-sm-2 col-form-label") {
                                    htmlFor = "title"
                                    +"Title"
                                }
                                div("col-sm-10") {
                                    input(InputType.text) {
                                        classes = setOf("form-control")
                                        id = "title"
                                        name = "title"
                                        //+"UUID2:Book@"
                                        placeholder = "Title of book"
                                    }
                                }
                            }
                            br

                            div("form-group row") {
                                label("col-sm-2 col-form-label") {
                                    htmlFor = "author"
                                    +"Author"
                                }
                                div("col-sm-10") {
                                    input(InputType.text) {
                                        classes = setOf("form-control")
                                        id = "author"
                                        name = "author"
                                        //+"Author"
                                        placeholder = "Author of book"
                                    }
                                }
                            }
                            br

                            div("form-group row") {
                                label("col-sm-2 col-form-label") {
                                    htmlFor = "description"
                                    +"Description"
                                }
                                div("col-sm-10") {
                                    input(InputType.text) {
                                        classes = setOf("form-control")
                                        id = "description"
                                        name = "description"
                                        //+"Description"
                                        placeholder = "Description of book"
                                    }
                                }
                            }
                            br

                            submitInput {
                                classes = setOf("btn", "btn-primary", "btn-block")
                                value = "Add Book Info"
                            }
                        }
                    }
                }
            }
        }
        post("/addBookInfo") {
            val body = call.receiveText().decodeURLPart(charset = Charsets.UTF_8)
            // val params = body.parseUrlEncodedParameters() // default encoding (applicationXWwwFormUrlEncoded)
            val params = body.parseTextPlainEncodedFormParameters()
            val id = params["id"]
            val title = params["title"]
            val author = params["author"]
            val description = params["description"]

            if (id != null &&
                title != null &&
                author != null &&
                description != null
            ) {
                val bookInfo = BookInfo(
                    id = id.fromUUID2StrToTypedUUID2<Book>(),
                    title = title,
                    author = author,
                    description = description,
                )

                // add book to bookInfo repo
                val upsertBookInfoResult = libraryAppContext.bookInfoRepo.upsertBookInfo(bookInfo)
                val upsertBookInfostatusJson = resultToStatusCodeJson<BookInfo>(upsertBookInfoResult)

                call.respondHtml {
                    body {
                        if (upsertBookInfostatusJson.statusCode != HttpStatusCode.OK) {
                            h1 { +"Error adding book info" }
                            p { +upsertBookInfostatusJson.json }
                        } else {
                            h1 { +"Book info added:" }
                        }

                        p { +"Book Id: $id" }
                        p { +"Title: $title" }
                        p { +"Author: $author" }
                        p { +"Description: $description" }

                        // go to list books page
                        a {
                            href = "/"
                            +"Home"
                        }
                    }
                }
                return@post
            }

            call.respondJson(mapOf("error" to "Invalid parameters"), HttpStatusCode.BadRequest)
        }

        get("/populateBookInfo") {
            val testingUtils = TestingUtils(libraryAppContext)
            testingUtils.populateFakeBooksInBookInfoRepoDBandAPI()

            val bookResult =
                libraryAppContext
                    .bookInfoRepo
                    .bookInfoDatabase
                    .allBookInfos()
            val bookInfos = bookResult.getOrThrow()

            call.respondHtml {
                bootstrapHeader()

                body {
                    h1 { +"Populated Book Info" }
                    bookInfos.forEach { bookInfo ->
                        p { +"Title: ${bookInfo.value.title}" }
                        p { +"Author: ${bookInfo.value.author}" }
                        p { +"Description: ${bookInfo.value.description}" }
                        p { +"Id: ${bookInfo.value.id}" }
                        br
                    }
                }
            }
        }
    }
}

private fun HTML.bootstrapHeader() {
    head {
        link {
            rel = "stylesheet"
            href = "https://cdn.jsdelivr.net/npm/bootstrap@5.2.3/dist/css/bootstrap.min.css"
            integrity =
                "sha384-rbsA2VBKQhggwzxH7pPCaAqO46MgnOM80zW1RWuH61DGLwZJEdK2Kadq2F9CUG65"
            attributes["crossorigin"] = "anonymous"
        }
        style {
            unsafe {
                raw(
                    """
                        input[type="text"] {
                            margin-left: 10px;
                        }
                    """.trimIndent()
                )
            }
        }
    }
}

private fun BODY.libraryStyle() {
    style {
        unsafe {
            raw(
                """
                    body {
                        margin-left: 10px;
                        margin-right: 10px;
                    }
                    form {
                        display: flex;
                        flex-direction: column;
                        width: 80%;
                    }
                    label {
                        font-weight: bold;
                        margin-right: 5px;
                    }
                    input {
                        margin-bottom: 10px;
                        width: 100%;
                    }
                    button {
                        margin-bottom: 10px;
                        margin-right: 5px;
                    }
                    .col-sm-2 {
                        font-weight: bold;
                        font-style: italic;
                    }
                """.trimIndent()
            )
        }

    }
}