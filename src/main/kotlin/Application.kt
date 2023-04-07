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
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ContentNegotiationServer
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
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
data class UserEntity(
    val email: String,
    val password: PasswordString,
    var token: TokenString,
    var clientIpAddressWhiteList: List<String> = listOf()
)

const val APPLICATION_PROPERTIES_FILE = "./application.properties"

@Serializable
data class ApplicationProperties(
    val pepper: String = "ooga-booga",
    val databaseBaseUrl: String = "http://localhost:3000"
)

fun Application.module() {

    ////////////////////////////////
    // SETUP APPLICATION PROPERTIES

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
    installServer(ForwardedHeaders)

    ///////////////////////////////////////////////
    // SETUP KTOR CLIENT
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
        pepper = applicationConfig.pepper // pepper is used to make the password hash unique
    )
    val usersDb = mutableMapOf<EmailString, UserEntity>()

    fun saveUsersDbToDisk() {
        File("usersDB.json").writeText(
            jsonConfig.encodeToString(
                usersDb.values.toList()
            )
        )
    }

    // Load the users from the resources json file
    fun loadUsersDbFromDisk() {
        if (!File("usersDB.json").exists()) {
            File("usersDB.json").writeText("")
        }

        val userDBJson = File("usersDB.json").readText()
        if (userDBJson.isNotEmpty()) {
            val users = jsonConfig.decodeFromString<List<UserEntity>>(userDBJson)
            for (user in users) {
                usersDb[user.email] = user
            }
        }
    }

    loadUsersDbFromDisk()

    // setup tokenLookup
    val emailToTokenMap = mutableMapOf<TokenString, EmailString>()
    for (user in usersDb.values) {
        emailToTokenMap[user.token] = user.email
    }

    installServer(Authentication) {

        // Note: tokens and client IP address can be passed in the header or in a cookie
        bearer("auth-bearer") {
            realm = "Access to the '/api' path"
            authenticate { tokenCredential ->

                // Check the client IP address is in the whitelist for this user
                val clientIpAddress = request.call.getClientIpAddress()

                // Auth Token can be passed in the header or in a cookie
                if(tokenCredential.token.isEmpty() && this.request.cookies.rawCookies["authenticationToken"] == null) {
                    this.response.status(HttpStatusCode.Unauthorized)
                    this.response.header("Location", "/login")
                    return@authenticate null
                }
                val authenticationToken =
                    if (tokenCredential.token.isNotBlank())
                        tokenCredential.token
                    else
                        this.request.cookies["authenticationToken"]

                println("authenticationToken: $authenticationToken")

                val userEmail = emailToTokenMap[authenticationToken]
                if (userEmail != null) {

                    val userEntity = usersDb[userEmail]
                    userEntity?.let { user ->
                        if (user.clientIpAddressWhiteList.contains(clientIpAddress)) {
                            UserIdPrincipal(userEmail)
                        } else {

                            println("User $userEmail attempted to access the API from an " +
                                    "unauthorized IP address: $clientIpAddress")

                            // attempt redirect to login page
                            this.response.status(HttpStatusCode.Unauthorized)
                            this.response.header("Location", "/login")
                            null
                        }
                    }
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
                var clientIpAddress = params["clientIpAddress"]

                if (
                    username != null
                    && password != null
                    && clientIpAddress != null
                ) {
                    usersDb[username] ?: run {

                        val passwordHash = passwordService.getSaltedPepperedPasswordHash(password)
                        val token = UUID.randomUUID().toString()
                        usersDb[username] = UserEntity(username, passwordHash, token)
                        usersDb[username]?.clientIpAddressWhiteList = listOf(clientIpAddress)

                        saveUsersDbToDisk()

                        call.respondText(jsonConfig.encodeToString(
                            mapOf(
                                "token" to token,
                                "clientIpAddress" to clientIpAddress,
                            )
                        ))
                        return@post
                    }

                    val error = mapOf("error" to "UserEntity already exists")
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
                var clientIpAddressFromParams = params["clientIpAddress"]

                val clientIpAddressFromRequest = call.getClientIpAddress(clientIpAddressFromParams)

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

                    // Add the client ip address to the user if it doesn't already exist
                    if (!user.clientIpAddressWhiteList.contains(clientIpAddressFromRequest)) {
                        val newClientIpWhitelistAddresses = user.clientIpAddressWhiteList.toMutableList()
                        newClientIpWhitelistAddresses.add(clientIpAddressFromRequest)
                        user.clientIpAddressWhiteList = newClientIpWhitelistAddresses

                        saveUsersDbToDisk()
                    }

                    // generate a new token
                    val token = UUID.randomUUID().toString()
                    emailToTokenMap[token] = email

                    // Update the token in the userDb
                    usersDb[email]?.token = token
                    saveUsersDbToDisk()

                    call.respondText(jsonConfig.encodeToString(
                        mapOf(
                            "token" to token,
                            "clientIpAddress" to clientIpAddressFromRequest,
                        )
                    ))
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

                println("token: $token, body: $body, params: $params")

                token?.let {
                    println("token: $token")

                    val userEmail = emailToTokenMap[token]
                    userEmail?.let {
                        usersDb[userEmail]?.token = ""
                        emailToTokenMap.remove(token)
                        saveUsersDbToDisk()

//                        clearCookies()

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

        // api routes are protected by authentication
        authenticate("auth-bearer") {

            get("/api/todos") {
                // make call to local database server
                val response = client.get(applicationConfig.databaseBaseUrl + "/todos")
                if (response.status.value == 200) {
                    try {
                        val body = response.body<String>()
                        println("body: $body")
                        val todos = jsonConfig.decodeFromString<TodoResponse>(body)

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
    @SerialName("user")
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

fun ApplicationCall.getClientIpAddress(suggestedClientIpAddress: String? = null): String {
    val call = this
    val ipFromForwardedForHeader = call.request.header("X-Forwarded-For")
    val ipFromCookies = call.request.cookies["clientIpAddress"]

    return if (ipFromForwardedForHeader != null) {
        ipFromForwardedForHeader.toString()
    } else {
        val originRemoteHost = call.request.origin.remoteHost
        val originServerHost = call.request.origin.serverHost
        val remoteHost = call.request.local.remoteHost
        val serverHost = call.request.local.serverHost

        ipFromCookies ?: suggestedClientIpAddress ?: remoteHost
    }
}


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
