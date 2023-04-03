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


//fun Application.module() {
//    routing {
//        singlePageApplication {
//            useResources = true
//            filesPath = "sample-web-app"
//            defaultPage = "main.html"
//            ignoreFiles { it.endsWith(".txt") }
//        }
//    }
//}

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

data class User(
    val username: String,
    val password: PasswordString,
    var token: TokenString
)

fun Application.module() {

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

    val users = mutableMapOf<EmailString, User>()
    users["a@b.c"] = User("jetbrains", "test", "")
    users["user"] = User("user", "test", "")
    users["test"] = User("test", "test", "")

    // setup tokenLookup
    val tokenLookup = mutableMapOf<TokenString, EmailString>()
    for (user in users.values) {
        tokenLookup[user.token] = user.username
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

        // Authentication
        post("/api/login") {
            // read the email and password from the request
            val body = call.receiveText()
            val params = jsonConfig.decodeFromString<Map<String, String>>(body)
            val username = params["email"]
            val password = params["password"]

            // hash the password
            val passwordHash = password

            if (username != null && passwordHash != null) {
                if (users[username]?.password == passwordHash) {
                    val token = UUID.randomUUID().toString()
                    users[username]?.token = token
                    tokenLookup[token] = username
                    call.respondText(jsonConfig.encodeToString(mapOf("token" to token)))
                } else {
                    val error = mapOf("error" to "Invalid credentials")
                    call.respondText(jsonConfig.encodeToString(error),
                        status = HttpStatusCode.Unauthorized)
                }
            } else {
                val error = mapOf("error" to "Invalid parameters")
                call.respondText(jsonConfig.encodeToString(error),
                    status = HttpStatusCode.BadRequest)
            }
        }

        post("/api/logout") {
            val params = call.receiveParameters()
            val token = params["token"]

            if (token != null) {
                val user = tokenLookup[token]
                if (user != null) {
                    users[user]?.token = ""
                    tokenLookup.remove(token)
                    call.respondText(jsonConfig.encodeToString(mapOf("success" to true)))
                } else {
                    val error = mapOf("error" to "Invalid token")
                    call.respondText(jsonConfig.encodeToString(error),
                        status = HttpStatusCode.Unauthorized)
                }
            } else {
                val error = mapOf("error" to "Invalid parameters")
                call.respondText(jsonConfig.encodeToString(error),
                    status = HttpStatusCode.BadRequest)
            }
        }

        get("/api/todos") {

            // make call to api
            val response = client.get("http://localhost:3000/todos")
            if(response.status.value == 200) {
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

                } catch (e: Exception) {
                    call.respondText("Error decoding response: ${e.localizedMessage}")
                }
            } else {
                call.respondText("Error: ${response.status.value}")
            }
        }

        // https://tahaben.com.ly/2022/04/uploading-image-using-android-ktor-client-to-ktor-server/
        post("/api/upload-image") {
            val multipart = call.receiveMultipart()
            var tempFilename: String? = null
            var name: String? = null
            var originalFileName: String? = null
            val uploadedImageList = ArrayList<String>()
            try{
                multipart.forEachPart { partData ->
                    when(partData){
                        is PartData.FormItem -> {
                            //to read additional parameters that we sent with the image
                            if (partData.name == "name"){
                                name = partData.value
                            }
                        }
                        is PartData.FileItem ->{
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
                    status =ToDoStatus.pending,
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
                call.respond(HttpStatusCode.InternalServerError,"Error")
            }

            println("name= ${name}")
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
