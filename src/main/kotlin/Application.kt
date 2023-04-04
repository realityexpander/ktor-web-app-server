package com.realityexpander

import com.github.slugify.Slugify
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ContentNegotiationClient
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ContentNegotiationServer
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import io.ktor.server.application.install as installServer

val jsonConfig = Json {
    prettyPrint = true
    isLenient = true
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

typealias EmailString = String
typealias PasswordString = String
typealias TokenString = String

@Serializable
data class User(
    val email: String,
    val password: PasswordString,
    var token: TokenString
)

const val APPLICATION_PROPERTIES_FILE = "./application.properties"

@Serializable
data class ApplicationProperties(
    val pepper: String = "ooga-booga"
)

fun Application.module() {

    // load application.properties
    val applicationProperties = File(APPLICATION_PROPERTIES_FILE).inputStream()
    val applicationConfig =
        try {
            Properties().apply {
                load(applicationProperties)
            }.let {
                ApplicationProperties(
                    pepper = it.getProperty("pepper")
                )
            }
        } catch (e: Exception) {
            println("Error loading application properties: $e")
            ApplicationProperties()
        }

    installServer(Compression) {
        gzip()
    }

    installServer(ContentNegotiationServer) {
        json(jsonConfig)
    }


    // setup ktor client
    val client = HttpClient(OkHttp) {
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }

        install(ContentNegotiationClient) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                }
            )
        }

        engine {
            config {
                connectTimeout(10, TimeUnit.SECONDS)
                readTimeout(10, TimeUnit.SECONDS)
            }
        }
    }

    /////////////////////////////////////////
    // SETUP AUTHENTICATION

    val passwordService = ArgonPasswordService(
        pepper = applicationConfig.pepper
    )
    val usersDb = mutableMapOf<EmailString, User>()

    // Save the users to the resources json file
    fun saveUsers() {
        File("usersDB.json").writeText(
            jsonConfig.encodeToString(
                usersDb.values.toList()
            )
        )
    }

    // Load the users from the resources json file
    fun loadUsers() {
        if (!File("usersDB.json").exists()) {
            File("usersDB.json").writeText("")
        }

        val userDBJson = File("usersDB.json").readText()
        if (userDBJson.isNotEmpty()) {
            val users = jsonConfig.decodeFromString<List<User>>(userDBJson)
            for (user in users) {
                usersDb[user.email] = user
            }
        }
    }

    loadUsers()

    // setup tokenLookup
    val tokenLookup = mutableMapOf<TokenString, EmailString>()
    for (user in usersDb.values) {
        tokenLookup[user.token] = user.email
    }

    installServer(Authentication) {
        bearer("auth-bearer") {
            realm = "Access to the '/api' path"
            authenticate { tokenCredential ->

                val user = tokenLookup[tokenCredential.token]
                if (user != null) {
                    UserIdPrincipal(user)
                } else {
                    null
                }
            }
        }
    }


    routing {
        // setup CORS
//        options("*") {
//            call.response.header("Access-Control-Allow-Origin", "*")
//            call.response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
//            call.response.header("Access-Control-Allow-Headers", "Content-Type, Authorization")
//            call.response.header("Content-Type", "application/json")
//        }

        // api routes
        post("/api/register") {
            try {
                val body = call.receiveText()
                val params = jsonConfig.decodeFromString<Map<String, String>>(body)
                val username = params["email"]
                val password = params["password"]

                if (username != null && password != null) {
                    usersDb[username] ?: run {

                        val passwordHash = passwordService.getSaltedPepperedPasswordHash(password)
                        usersDb[username] = User(username, passwordHash, "")

                        val token = UUID.randomUUID().toString()
                        usersDb[username]?.token = token

                        saveUsers()

                        call.respondText(jsonConfig.encodeToString(mapOf("token" to token)))
                        return@post
                    }

                    val error = mapOf("error" to "User already exists")
                    call.respondText(
                        jsonConfig.encodeToString(error),
                        status = HttpStatusCode.Conflict
                    )
                    return@post
                }

                val error = mapOf("error" to "Invalid parameters")
                call.respondText(
                    jsonConfig.encodeToString(error),
                    status = HttpStatusCode.BadRequest
                )
            } catch (e: Exception) {
                val error = mapOf("error" to e.message)
                call.respondText(
                    jsonConfig.encodeToString(error),
                    status = HttpStatusCode.BadRequest
                )
            }
        }

        // Authentication
        post("/api/login") {
            try {
                val body = call.receiveText()
                val params = jsonConfig.decodeFromString<Map<String, String>>(body)
                val email = params["email"]
                val password = params["password"]

                if (email != null && password != null) {
                    // check if the user exists
                    if (!usersDb.containsKey(email)) {
                        val error = mapOf("error" to "User does not exist")
                        call.respondText(
                            jsonConfig.encodeToString(error),
                            status = HttpStatusCode.NotFound
                        )
                        return@post
                    }

                    // check if the password is correct
                    val user = usersDb[email]!!
                    val userPasswordHash = user.password
                    if (!passwordService.validatePassword(password, userPasswordHash)) {
                        val error = mapOf("error" to "Invalid credentials")
                        call.respondText(
                            jsonConfig.encodeToString(error),
                            status = HttpStatusCode.Unauthorized
                        )
                        return@post
                    }

                    val token = UUID.randomUUID().toString()
                    usersDb[email]?.token = token
                    tokenLookup[token] = email
                    call.respondText(jsonConfig.encodeToString(mapOf("token" to token)))
                    return@post
                }

                val error = mapOf("error" to "Invalid parameters")
                call.respondText(
                    jsonConfig.encodeToString(error),
                    status = HttpStatusCode.BadRequest
                )
            } catch (e: Exception) {
                val error = mapOf("error" to e.message)
                call.respondText(
                    jsonConfig.encodeToString(error),
                    status = HttpStatusCode.BadRequest
                )
            }
        }

        post("/api/logout") {
            try {
                val body = call.receiveText()
                val params = jsonConfig.decodeFromString<Map<String, String>>(body)
                val token = params["token"]

                token?.let {
                    val userEmail = tokenLookup[token]
                    userEmail?.let {
                        usersDb[userEmail]?.token = ""
                        tokenLookup.remove(token)
                        saveUsers()

                        call.respondText(jsonConfig.encodeToString(mapOf("success" to true)))
                        return@post
                    }

                    val error = mapOf("error" to "Invalid token")
                    call.respondText(
                        jsonConfig.encodeToString(error),
                        status = HttpStatusCode.Unauthorized
                    )
                    return@post
                }

                val error = mapOf("error" to "Invalid parameters")
                call.respondText(
                    jsonConfig.encodeToString(error),
                    status = HttpStatusCode.BadRequest
                )
            } catch (e: Exception) {
                val error = mapOf("error" to e.message)
                call.respondText(
                    jsonConfig.encodeToString(error),
                    status = HttpStatusCode.BadRequest
                )
            }
        }

        get("/api/todos") {

            // make call to api
            val response = client.get("http://localhost:3000/todos")
            if (response.status.value == 200) {
                try {
                    val todos = jsonConfig.decodeFromString<TodoResponse>(response.body())

//                    // Simulate server edits of data
//                    val todo = todos[0]
//                    val userInTodo = UserInTodo("John")
//                    val updatedTodo = todo.copy(userInTodo = userInTodo)
//                    todos[0] = updatedTodo

//                    call.response.apply {
//                        headers.append("Content-Type", "application/json")
//                    }
                    call.respond(Json.encodeToString(todos))
                    return@get

                } catch (e: Exception) {
                    val error = mapOf("error" to e.localizedMessage)
                    call.respondText(
                        jsonConfig.encodeToString(error),
                        status = response.status
                    )
                    return@get
                }
            }

            val error = mapOf("error" to response.body<String>().toString())
            call.respondText(
                jsonConfig.encodeToString(error),
                status = response.status
            )
        }

        // https://tahaben.com.ly/2022/04/uploading-image-using-android-ktor-client-to-ktor-server/
        post("/api/upload-image") {
            val multipart = call.receiveMultipart()
            var tempFilename: String? = null
            var name: String? = null
            var originalFileName: String?
            val uploadedImageList = ArrayList<String>()
            try {
                multipart.forEachPart { partData ->
                    when (partData) {
                        is PartData.FormItem -> {
                            //to read additional parameters that we sent with the image
                            if (partData.name == "name") {
                                name = partData.value
                            }
                        }

                        is PartData.FileItem -> {
                            tempFilename = partData.save(Constants.USER_IMAGES_PATH)
                            originalFileName = partData.originalFileName

                            // Create slug for originalFileName
                            val filename = originalFileName?.takeWhile { it != '.' }
                            val fileExtension = originalFileName?.takeLastWhile { it != '.' }
                            val fileNameSlug = Slugify.builder()
                                .locale(Locale.US)
                                .build()
                                .slugify(filename)
                            val newFilenameSlug = "$fileNameSlug.$fileExtension"

                            // Move/Rename file
                            val newFilePath = "${Constants.USER_IMAGES_PATH}${newFilenameSlug}"
                            File("${Constants.USER_IMAGES_PATH}/$tempFilename").renameTo(File(newFilePath))

                            uploadedImageList.add(newFilePath)
                        }

                        is PartData.BinaryItem -> Unit
                        else -> {
                            println("/upload-image Unknown PartData.???")
                        }
                    }
                }

                val newTodo = Todo(
                    id = "1",
                    name = name.toString(),
                    status = ToDoStatus.pending,
                    userInTodo = UserInTodo(
                        name = name.toString(),
                        files = uploadedImageList
                    )
                )
                val fileUploadResponse = FileUploadResponse(
                    todo = newTodo,
                    uploadedFiles = uploadedImageList
                )

                call.respond(HttpStatusCode.OK, Json.encodeToString(fileUploadResponse))
            } catch (ex: Exception) {
                File("${Constants.USER_IMAGES_PATH}/$tempFilename").delete()
                call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }

        singlePageApplication {
            defaultPage = "index.html"
            filesPath = "/Volumes/TRS-83/dev/JavascriptWebComponents"
        }
    }
}

fun PartData.FileItem.save(path: String): String {
    val fileBytes = streamProvider().readBytes()
    val fileExtension = originalFileName?.takeLastWhile { it != '.' }
    val fileName = UUID.randomUUID().toString() + "." + fileExtension
    val folder = File(path)
    folder.mkdir()
    println("Path = $path $fileName")
    File("$path$fileName").writeBytes(fileBytes)
    return fileName
}

object Constants {
    val USER_IMAGES_PATH = "./uploaded-images/"
    val BASE_URL = "http://localhost:8080"
}

@Serializable
data class FileUploadResponse(
    val todo: Todo,
    val uploadedFiles: List<String>
)

@Serializable
data class UserInTodo(
    val name: String,
    val files: List<String>? = null
)

@Serializable
enum class ToDoStatus(val value: String) {
    pending("pending"),
    completed("completed"),
    archived("archived")
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Todo(
    val id: String,
    val name: String,
    val status: ToDoStatus = ToDoStatus.pending,
    @SerialName("userInTodo")
    val userInTodo: UserInTodo? = null
)

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class SuccessResponse(
    val success: String,
)

//@Serializable
//data class TodoResponse(val todos: ArrayList<Todo>)

typealias TodoResponse = ArrayList<Todo>

//fun main() {
//    embeddedServer(Netty, port = 8081) {
//        install(Compression)
//
//        routing {
//            singlePageApplication {
//                defaultPage = "index.html"
//                filesPath = "todo-app"
//            }
//
//            // api routes
//            get("/api/hello") {
//                call.respondText("Hello World!")
//            }
//        }
//
//    }.start(wait = true)
//}
