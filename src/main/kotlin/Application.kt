package com.realityexpander

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
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
import io.ktor.server.auth.jwt.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.apache.commons.mail.DefaultAuthenticator
import org.apache.commons.mail.SimpleEmail
import org.slf4j.LoggerFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.naming.AuthenticationException
import io.ktor.server.application.install as installServer

val jwtLogger: ch.qos.logback.classic.Logger = LoggerFactory.getLogger("KTOR-JWT-Auth") as ch.qos.logback.classic.Logger

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
typealias JWTString = String

@Serializable
data class UserEntity(
    val id: String = UUID.randomUUID().toString(),
    val email: String,
    val password: PasswordString,
    val token: TokenString,
    val jwtToken: JWTString? = null,
    val clientIpAddressWhiteList: List<String> = listOf(),
    val passwordResetToken: String? = null,
)

const val APPLICATION_PROPERTIES_FILE = "./application.properties"

@Serializable
data class ApplicationProperties(
    val pepper: String = "ooga-booga",
    val databaseBaseUrl: String = "http://localhost:3000",
    val emailSendinblueApiKey: String? = null,
    val emailSendinblueFromEmail: String? = null,
    val emailSendinblueFromName: String? = null,
)

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
                pepper = it.getProperty("pepper"),
                databaseBaseUrl = it.getProperty("databaseBaseUrl"),
                emailSendinblueApiKey = it.getProperty("emailSendinblueApiKey"),
                emailSendinblueFromEmail = it.getProperty("emailSendinblueFromEmail"),
                emailSendinblueFromName = it.getProperty("emailSendinblueFromName")

            )
        }
    } catch (e: Exception) {
        println("Error loading application properties: $e")
        ApplicationProperties()
    }

fun Application.module() {

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

    // setup email to token Lookup table
    val emailToTokenMap = mutableMapOf<TokenString, EmailString>()
    for (user in usersDb.values) {
        emailToTokenMap[user.token] = user.email
    }

    // Setup JWT (environment is from the 'resources/application.conf' file)
    val secret = environment.config.config("ktor").property("jwt.secret").getString()
    val issuer = environment.config.config("ktor").property("jwt.issuer").getString()
    val audience = environment.config.config("ktor").property("jwt.audience").getString()
    val apiRealm = environment.config.config("ktor").property("jwt.realm").getString()

    installServer(Authentication) {

        jwt("auth-jwt") {
            realm = apiRealm

//            skipWhen { call ->
//                call.request.path().startsWith("/api")
//            }

            verifier(
                JWT
                    .require(Algorithm.HMAC256(secret))
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .build()
            )

            validate { credential ->
                if (credential.payload.getClaim("email").asString().isNullOrBlank()) return@validate null
                if (credential.payload.getClaim("clientIpAddress").asString().isNullOrBlank()) return@validate null
                if (credential.payload.getClaim("exp")
                        .asInt() < (System.currentTimeMillis() / 1000).toInt()
                ) return@validate null

                val email = credential.payload.getClaim("email").asString()
                val clientIpAddress = credential.payload.getClaim("clientIpAddress").asString()

                val user = usersDb[email]
                if (user != null && user.clientIpAddressWhiteList.contains(clientIpAddress)) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { defaultScheme, realm ->
                println("challenge: $defaultScheme, $realm")
                //call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
                val error = mapOf("error" to "Token is not valid or has expired")
                call.respondText(
                    jsonConfig.encodeToString(error),
                    status = HttpStatusCode.Unauthorized
                )
            }
        }

        // Note: token and clientIPAddress can be passed in the header or in a cookie (not JWT)
        bearer("auth-bearer") {
            realm = "Access to the '/api' path"
            authenticate { tokenCredential ->

                // Check the client IP address is in the whitelist for this user
                val clientIpAddress = request.call.getClientIpAddressFromRequest()

                // Auth Token can be passed in the header or in a cookie
                if (tokenCredential.token.isEmpty() && this.request.cookies.rawCookies["authenticationToken"] == null) {
                    this.response.status(HttpStatusCode.Unauthorized)
                    this.response.header("Location", "/login")
                    return@authenticate null
                }
                val authenticationToken =
                    if (tokenCredential.token.isNotBlank())
                        tokenCredential.token
                    else
                        this.request.cookies["authenticationToken"]

                val userEmail = emailToTokenMap[authenticationToken]
                if (userEmail != null) {

                    val userEntity = usersDb[userEmail]
                    userEntity?.let { user ->
                        if (user.clientIpAddressWhiteList.contains(clientIpAddress)) {
                            UserIdPrincipal(userEmail)
                        } else {

                            println(
                                "User $userEmail attempted to access the API from an " +
                                        "unauthorized IP address: $clientIpAddress"
                            )

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
                val email = params["email"]
                val password = params["password"]
                var clientIpAddressFromParams = params["clientIpAddress"]
                val clientIpAddress =
                    call.getClientIpAddressFromRequest(suggestedClientIpAddress = clientIpAddressFromParams)

                if (
                    email != null
                    && password != null
                ) {
                    usersDb[email] ?: run {

                        val passwordHash = passwordService.getSaltedPepperedPasswordHash(password)
                        val token = UUID.randomUUID().toString()

                        // Generate JWT token for this user
                        val jwtToken = JWT.create()
                            .withSubject("Authentication")
                            .withIssuer(issuer)
                            .withAudience(audience)
                            .withClaim("email", email)
                            .withClaim("clientIpAddress", clientIpAddress)
                            .withExpiresAt(Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7)) // 7 days
                            .sign(Algorithm.HMAC256(secret))

                        val newUser = UserEntity(
                            email=email,
                            password = passwordHash,
                            token = token,
                            clientIpAddressWhiteList = listOf(clientIpAddress),
                            jwtToken = jwtToken
                        )
                        usersDb[email] = newUser

                        saveUsersDbToDisk()

                        call.respondJson(
                            map=mapOf(
                                "token" to token,
                                "clientIpAddress" to clientIpAddress,
                                "jwtToken" to jwtToken,
                                "user" to jsonConfig.encodeToString(newUser)
                            )
                        )
                        return@post
                    }

                    call.respondJson(mapOf("error" to "UserEntity already exists"), HttpStatusCode.Conflict)
                    return@post
                }

                call.respondJson(mapOf("error" to "Invalid parameters"), HttpStatusCode.BadRequest)
            } catch (e: Exception) {
                call.respondJson(mapOf("error" to e.localizedMessage), HttpStatusCode.BadRequest)
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
                val clientIpAddress = call.getClientIpAddressFromRequest(clientIpAddressFromParams)

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

                    // Add the client ip address to the user's ip address white list (if it doesn't already exist)
                    // Note: This is to prevent a potential malicious attacker from using the same token from a different IP address.
                    // (that they havent yet authenticated from.)
                    // This may be a good place to add a captcha or send confirmation email to prevent brute force attacks.
                    if (!user.clientIpAddressWhiteList.contains(clientIpAddress)) {
                        val newClientIpWhitelistAddresses = user.clientIpAddressWhiteList.toMutableList()
                        newClientIpWhitelistAddresses.add(clientIpAddress)

                        usersDb[email] = user.copy(clientIpAddressWhiteList = newClientIpWhitelistAddresses)
                        saveUsersDbToDisk()
                    }

                    // Generate a new session token
                    val token = UUID.randomUUID().toString()
                    emailToTokenMap[token] = email
                    usersDb[email] = user.copy(token = token)

                    // Generate JWT token for this user
                    val jwtToken = JWT.create()
                        .withSubject("Authentication")
                        .withIssuer(issuer)
                        .withAudience(audience)
                        .withClaim("email", email)
                        .withClaim("clientIpAddress", clientIpAddress)
                        .withExpiresAt(Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7)) // 7 days
                        .sign(Algorithm.HMAC256(secret))
                    usersDb[email] = usersDb[email]!!.copy(jwtToken = jwtToken)

                    saveUsersDbToDisk()

                    call.respondJson(
                        map=mapOf(
                            "token" to token,
                            "clientIpAddress" to clientIpAddress,
                            "jwtToken" to jwtToken
                        )
                    )
                    return@post
                }

                call.respondJson(mapOf("error" to "Invalid parameters"), HttpStatusCode.BadRequest)
            } catch (e: Exception) {
                call.respondJson(mapOf("error" to e.localizedMessage), HttpStatusCode.BadRequest)
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
                        usersDb[userEmail] = usersDb[userEmail]!!.copy(token = "")
                        usersDb[userEmail] = usersDb[userEmail]!!.copy(jwtToken = "")
                        emailToTokenMap.remove(token)

                        saveUsersDbToDisk()

                        call.respondJson(map=mapOf("success" to "true"))
                        return@post
                    }

                    call.respondJson(mapOf("error" to "Invalid token"), HttpStatusCode.Unauthorized)
                    return@post
                }

                val error = mapOf("error" to "Invalid parameters")
                call.respondText(
                    jsonConfig.encodeToString(error),
                    status = HttpStatusCode.BadRequest
                )
                call.respondJson(mapOf("error" to "Invalid parameters"), HttpStatusCode.BadRequest)
            } catch (e: Exception) {
                val error = mapOf("error" to e.message)
                call.respondText(
                    jsonConfig.encodeToString(error),
                    status = HttpStatusCode.BadRequest
                )
            }
        }

        get("/api/send-password-reset-email") {
            val emailAddress = call.request.queryParameters["emailAddress"]
            println("emailAddress: $emailAddress")

            if (emailAddress == null) {
                call.respondJson(mapOf("error" to "Email address is required"), HttpStatusCode.BadRequest)
                return@get
            }

            // generate a password reset token
            val passwordResetToken = UUID.randomUUID().toString()
            // save the password reset token to the user's account
            val user = usersDb[emailAddress]
            user?.let {
                usersDb[emailAddress] = user.copy(passwordResetToken = passwordResetToken)
                saveUsersDbToDisk()
            } ?: run {
                call.respondJson(mapOf("error" to "User does not exist"), HttpStatusCode.BadRequest)
                return@get
            }

            val res = sendPasswordResetEmail(emailAddress = emailAddress, passwordResetToken)

            if (res) {
                call.respondJson(mapOf("success" to "Email sent"))
                return@get
            } else {
                call.respondJson(mapOf("error" to "Email failed to send"), HttpStatusCode.BadRequest)
                return@get
            }

        }

        post("/api/reset-password") {
            val body = call.receiveText()
            val params = jsonConfig.decodeFromString<Map<String, String>>(body)
            val passwordResetToken = params["passwordResetToken"]
            val newPassword = params["newPassword"]

            //println("passwordResetToken: $passwordResetToken, newPassword: $newPassword, body: $body, params: $params")

            if (passwordResetToken == null || newPassword == null) {
                call.respondJson(
                    mapOf("error" to "Password reset token and new password are required"), HttpStatusCode.BadRequest
                )
                return@post
            }

            // validate the password reset token
            if (passwordResetToken.length != 36) {
                call.respondJson(mapOf("error" to "Invalid password reset token"), HttpStatusCode.BadRequest)
                return@post
            }

            // validate password
            if (newPassword.length < 8) {
                call.respondJson(mapOf("error" to "Password must be at least 8 characters"), HttpStatusCode.BadRequest)
                return@post
            }
            if (!newPassword.matches(Regex(".*[A-Z].*"))) {
                call.respondJson(
                    mapOf("error" to "Password must contain at least one uppercase letter"),
                    HttpStatusCode.BadRequest
                )
                return@post
            }
            if (!newPassword.matches(Regex(".*[a-z].*"))) {
                call.respondJson(
                    mapOf("error" to "Password must contain at least one lowercase letter"),
                    HttpStatusCode.BadRequest
                )
                return@post
            }
            if (!newPassword.matches(Regex(".*[0-9].*"))) {
                call.respondJson(
                    mapOf("error" to "Password must contain at least one number"),
                    HttpStatusCode.BadRequest
                )
                return@post
            }
            if (!newPassword.matches(Regex(".*[!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*"))) {
                call.respondJson(
                    mapOf("error" to "Password must contain at least one special character"),
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            // find the user with the password reset token
            val user = usersDb.values.find { it.passwordResetToken == passwordResetToken }
            user?.let {
                // update the user's password
                val passwordHash = passwordService.getSaltedPepperedPasswordHash(newPassword)
                val updatedUser =
                    user.copy(
                        password = passwordHash,
                        passwordResetToken = ""
                    )
                usersDb[user.email] = updatedUser
                saveUsersDbToDisk()

                call.respondJson(map=mapOf("success" to "Password updated"))
                return@post
            } ?: run {
                call.respondJson(mapOf("error" to "Invalid password reset token"), HttpStatusCode.BadRequest)
                return@post
            }

            call.respondJson(mapOf("error" to "Invalid parameters"), HttpStatusCode.BadRequest)
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
                        call.respondJson(mapOf("error" to e.localizedMessage), response.status)
                        return@get
                    }
                }

                call.respondJson(mapOf("error" to response.body<String>().toString()), response.status)
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

        authenticate("auth-jwt") {

            get("/api/hello") {
                try {
                    val principal =
                        call.principal<JWTPrincipal>()
                            ?: throw AuthenticationException("Missing JWTPrincipal")
                    val username = principal.payload.getClaim("email").asString()
                    val expiresAt = principal.expiresAt?.time?.minus(System.currentTimeMillis())
                    val clientIpAddress = principal.payload.getClaim("clientIpAddress").asString()

                    // convert expiresAt to date
                    val date = Date(principal.expiresAt?.time ?: 0)
                    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    val expiresAtDate = formatter.format(date)
                    println("expiresAtDate: $expiresAtDate")

                    call.respondJson(
                        map = mapOf(
                            "success" to "Hello, $username! " +
                            "Token will expire at $expiresAtDate. " +
                            "Client IP Address: $clientIpAddress",
                        )
                    )
                } catch (e: Exception) {
                    call.respondJson(mapOf("error" to e.localizedMessage), HttpStatusCode.BadRequest)
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

fun ApplicationCall.getClientIpAddressFromRequest(suggestedClientIpAddress: String? = null): String {
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

        val resultIp = ipFromCookies ?: suggestedClientIpAddress ?: remoteHost

        if (resultIp != "localhost") {
            resultIp
        } else {
            UUID.randomUUID()
                .toString() // return a UUID instead of localhost, because `localhost` is not a valid IP. This will be the unique ID for the Application instance.
        }
    }
}

suspend fun ApplicationCall.respondJson(
    map: Map<String, String> = mapOf(),
    status: HttpStatusCode = HttpStatusCode.OK
) {
    respondText(jsonConfig.encodeToString(map), ContentType.Application.Json, status)
}

suspend fun sendPasswordResetEmail(emailAddress: String, passwordResetToken: String = ""): Boolean {
    jwtLogger.info("Sending reset email with SendInBlue to $emailAddress")

    val message = """
    <html>
        <body>
            <h1>Reset Password</h1>
            <img src="https://picsum.photos/200/300" alt="image">
            <p>Someone requested that you reset your password.</p>
            <p>
            <p>If this was not you, please ignore this email.</p>
            <br>
            <br>
            <a href="http://localhost:8081/reset-password/${passwordResetToken}">Click here to reset your password</a>
        </body>
    </html>
        
    """.trimIndent()

    return sendEmail(
        message,
        emailAddress,
        "Reset your password"
    )
}

suspend fun sendEmail(
    message: String,
    emailAddress: String,
    subject: String
): Boolean {
    return sendSimpleEmailViaSendInBlue(message, emailAddress, subject)
}

suspend fun sendSimpleEmailViaSendInBlue(
    message: String,
    emailAddress: String,
    subject: String
): Boolean {
    jwtLogger.info("Sending simple email with SendInBlue to $emailAddress")

    // Dashboard: https://app.sendinblue.com/settings
    // uses SendInBlue - google account realityexpanderdev@gmail.com

    return withContext(Dispatchers.IO) {
        try {
            val email = SimpleEmail()
            email.hostName = "smtp-relay.sendinblue.com"
//            email.setSmtpPort(587)
            email.setDebug(true)
            email.setAuthenticator(
                DefaultAuthenticator(
                    applicationConfig.emailSendinblueFromEmail,
                    applicationConfig.emailSendinblueApiKey
                )
            )
            email.isSSLOnConnect = true
            email.setFrom(applicationConfig.emailSendinblueFromEmail, applicationConfig.emailSendinblueFromName)
            email.subject = subject
            email.setMsg(message)
            email.addTo(emailAddress)
            email.send()

            return@withContext true
        } catch (e: Exception) {
            jwtLogger.error("Error sending email: $e")
            return@withContext false
        }
    }
}

//suspend fun sendSimpleEmailNetcore(emailAddress: String): Boolean {
//    jwtLogger.info("Sending simple email with NetCore to $emailAddress")
//
//    // uses Netcorecloud.com - google account realityexpanderdev@gmail.com, Zapper2041$$
//    // Dashboard: https://email.netcorecloud.com/app/settings/sandbox
//
//    return withContext(Dispatchers.IO) {
//        try {
//            val email = SimpleEmail()
//            email.hostName = "smtp.netcorecloud.net"
//            email.isStartTLSEnabled = true
//            email.isSSLOnConnect = true
//            email.isStartTLSRequired = true
//            email.setSmtpPort(587)
//            email.setDebug(true)
//            email.setAuthenticator(
//                DefaultAuthenticator(
//                    "realityexpanderdev",
//                    "realityexpanderdev_ee2912c85b380a09ef7f3edfe99b4a61"
//                )
//            )
//            email.setFrom("realityexpanderdev@pepisandbox.com", "Reality Expander Dev")
//            email.subject = "Test email"
//            email.setMsg("YO YO YO YOU GOT AN EMAIL")
//            email.addTo(emailAddress)
//            email.send()
//
//            return@withContext true
//        } catch (e: Exception) {
//            jwtLogger.error("Error sending email: $e")
//            return@withContext false
//        }
//    }
//}


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
