package com.example

import com.github.slugify.Slugify
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.*
import kotlinx.serialization.EncodeDefault.*
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

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

// ./gradlew :single-page-application:run
fun Application.module() {
    install(Compression)

    // setup ktor client
    val client = HttpClient(OkHttp) {
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }

        install(ContentNegotiation) {
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

    routing {
//        singlePageApplication {
//            react("react-app")
//        }

//        singlePageApplication {
//            defaultPage = "index.html"
//            filesPath = "todo-app"
//        }

        singlePageApplication {
            defaultPage = "index.html"
//            filesPath = "todo-app"
            filesPath = "/Volumes/TRS-83/dev/JavascriptWebComponents"
        }

        // api routes
        get("/api/hello") {
            call.respondText("Hello World!")
        }

        get("/api/todos") {

            // make call to api
            val response = client.get("http://localhost:3000/todos")
            if(response.status.value == 200) {
                try {
                    val todos = jsonConfig.decodeFromString<TodoResponse>(response.body())

//                    // Simulate server edits of data
//                    val todo = todos[0]
//                    val user = User("John")
//                    val updatedTodo = todo.copy(user = user)
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

        post("/upload-image") {
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
                    user = User(
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
data class User(
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
    val user: User? = null
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
