package com.realityexpander

import com.github.slugify.Slugify
import com.realityexpander.Constants.APPLICATION_PROPERTIES_FILE
import com.realityexpander.domain.auth.*
import com.realityexpander.domain.todo.ToDoStatus
import com.realityexpander.domain.todo.Todo
import com.realityexpander.domain.todo.UserInTodo
import com.realityexpander.domain.remote.emailer.sendPasswordResetEmail
import com.realityexpander.domain.todo.TodoResponse
import com.realityexpander.domain.remote.fileUpload.FileUploadResponse
import com.realityexpander.domain.remote.fileUpload.save
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import util.JwtTokenStr
import util.getClientIpAddressFromRequest
import util.respondJson
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.naming.AuthenticationException
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ContentNegotiationClient
import io.ktor.server.application.install as installServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ContentNegotiationServer

val ktorLogger: ch.qos.logback.classic.Logger =
    LoggerFactory.getLogger("KTOR-WEB-APP") as ch.qos.logback.classic.Logger

val jsonConfig = Json {
    prettyPrint = true
    isLenient = true
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

@Serializable
data class ApplicationProperties(
    val pepper: String = "ooga-booga",
    val jwtSecret: String = "default-jwt-secret",
    val databaseBaseUrl: String = "http://localhost:3000",
    val emailSendinblueApiKey: String? = null,
    val emailSendinblueFromEmail: String? = null,
    val emailSendinblueFromName: String? = null,
    val maxLoginTimeSeconds: Long = 60 * 60 * 24 * 7, // 7 days
)

//////////////////////////////////
// SETUP APPLICATION PROPERTIES //
//////////////////////////////////

object Constants {
    val USER_IMAGES_PATH = "./uploaded-images/"
    val APPLICATION_PROPERTIES_FILE = "./application.properties"
}

// Load application.properties
val applicationProperties = File(APPLICATION_PROPERTIES_FILE).inputStream()
val applicationConfig =
    try {
        Properties().apply {
            load(applicationProperties)
        }.let {
            ApplicationProperties(
                pepper = it.getProperty("pepper"),
                jwtSecret = System.getenv("JWT_SECRET") ?: "default-jwt-secret",
                databaseBaseUrl = it.getProperty("databaseBaseUrl"),
                emailSendinblueApiKey = it.getProperty("emailSendinblueApiKey"),
                emailSendinblueFromEmail = it.getProperty("emailSendinblueFromEmail"),
                emailSendinblueFromName = it.getProperty("emailSendinblueFromName"),
                maxLoginTimeSeconds = it.getProperty("maxLoginTimeSeconds")?.toLong() ?: 60 * 60 * 24 * 7,
            )
        }
    } catch (e: Exception) {
        ktorLogger.error("Error loading application properties: $e")
        ApplicationProperties()
    }

/////////////////////////
// SETUP USER SERVICE  //
/////////////////////////
val userService = UserService()

fun Application.module() {

    installServer(Compression) {
        gzip()
    }
    installServer(ContentNegotiationServer) {
        json(jsonConfig)
    }
    installServer(ForwardedHeaders)
    installServer(RateLimit) {
        global {
            rateLimiter(limit = 500, refillPeriod = 1.seconds)
        }

        register(RateLimitName("auth-routes")) {
            rateLimiter(limit = 5, refillPeriod = 60.seconds)
        }
    }
    installServer(StatusPages) {
        status(HttpStatusCode.TooManyRequests) { call, status ->
            val retryAfter = call.response.headers["Retry-After"]
            call.respondText(text = "429: Too many requests. Wait for $retryAfter seconds.", status = status)
        }

        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause" , status = HttpStatusCode.InternalServerError)
        }
    }

    /////////////////////////
    // SETUP KTOR CLIENT   //
    /////////////////////////

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

    /////////////////////////////
    // SETUP AUTHENTICATION    //
    /////////////////////////////

    val passwordService = ArgonPasswordService(
        pepper = applicationConfig.pepper // pepper is used to make the password hash unique
    )

    // Setup JWT (`environment` is config by'resources/application.conf' file)
    val secret = environment.config.config("ktor").property("jwt.secret").getString()
    val issuer = environment.config.config("ktor").property("jwt.issuer").getString()
    val audience = environment.config.config("ktor").property("jwt.audience").getString()
    val apiRealm = environment.config.config("ktor").property("jwt.realm").getString()

    // Setup JWT Authentication
    val jwtService = JwtService(
        secret = secret,
        issuer = issuer,
        audience = audience,
    )

    // Setup JWT & Bearer Authentication
    installServer(Authentication) {

        jwt("auth-jwt") {
            realm = apiRealm

//            skipWhen { call ->
//                call.request.path().startsWith("/api")
//            }

            verifier(
                jwtService.verifier
            )

            validate { credential ->
                val pl = credential.payload
                if (pl.getClaim("email")
                    .asString().isNullOrBlank()) return@validate null
                if (pl.getClaim("clientIpAddress")
                    .asString().isNullOrBlank()) return@validate null
                if (pl.getClaim("exp")
                    .asInt() < (System.currentTimeMillis() / 1000).toInt()) return@validate null

                val email = pl.getClaim("email").asString()
                val clientIpAddress = pl.getClaim("clientIpAddress").asString()

                val user = userService.getUserByEmail(email)
                if (user != null && user.clientIpAddressWhiteList.contains(clientIpAddress)) {
                    JWTPrincipal(pl)
                } else {
                    null
                }
            }

            challenge { defaultScheme, realm ->
                val error =
                    mapOf("error" to "Token is not valid or has expired. defaultScheme: $defaultScheme, realm: $realm")
                call.respondJson(error, status = HttpStatusCode.Unauthorized)
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

                val user = userService.getUserByAuthToken(authenticationToken)
                user?.let {
                    if (user.clientIpAddressWhiteList.contains(clientIpAddress)) {
                        UserIdPrincipal(user.email)// success
                    } else {

                        ktorLogger.warn(
                            "User ${user.email} attempted to access this API from an " +
                                    "non-white-list IP address: $clientIpAddress"
                        )

                        // todo send email to admin

                        // attempt redirect to login page
                        this.response.status(HttpStatusCode.Unauthorized)
                        this.response.header("Location", "/login")

                        null
                    }
                }
            }
        }
    }

    //////////////
    // ROUTING  //
    //////////////

    routing {
        // setup CORS
//        options("*") {
//            call.response.header("Access-Control-Allow-Origin", "*")
//            call.response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
//            call.response.header("Access-Control-Allow-Headers", "Content-Type, Authorization")
//            call.response.header("Content-Type", "application/json")
//        }

        ////////////////////
        // API ROUTES     //
        ////////////////////

        // Log a user in
        suspend fun ApplicationCall.login(
            clientIpAddress: String,
            user: UserEntity
        ) {
            // Generate a new session auth token
            val token = UUID.randomUUID().toString()

            // Generate a new session auth JWT token
            val jwtToken = jwtService.generateLoginAuthToken(user, clientIpAddress)
            userService.updateUser(
                user.copy(
                    authJwtToken = jwtToken,
                    authToken = token
                ))

            respondJson(mapOf(
                "token" to token,
                "jwtToken" to jwtToken,
                "clientIpAddress" to clientIpAddress,
            ))
        }

        rateLimit(RateLimitName("auth-routes")) {

            post("/api/register") {
                try {
                    val body = call.receiveText()
                    val params = jsonConfig.decodeFromString<Map<String, String>>(body)
                    val email = params["email"]
                    val password = params["password"]
                    var clientIpAddressFromParams = params["clientIpAddress"]

                    if (!validatePassword(password ?: "")) return@post

                    val clientIpAddress =
                        call.getClientIpAddressFromRequest(suggestedClientIpAddress = clientIpAddressFromParams)

                    if (email != null && password != null) {
                        userService.getUserByEmail(email) ?: run {

                            val passwordHash = passwordService.getSaltedPepperedPasswordHash(password)

                            val newUser = UserEntity(
                                email = email,
                                password = passwordHash,
                                authToken = "",
                                authJwtToken = "",
                                clientIpAddressWhiteList = listOf(clientIpAddress),
                            )
                            userService.addUser(newUser)

                            call.login(clientIpAddress, newUser)
                            return@post
                        }
                        call.respondJson(mapOf("error" to "UserEntity already exists"), HttpStatusCode.Conflict)
                        return@post
                    }
                    call.respondJson(mapOf("error" to "Invalid email or password"), HttpStatusCode.BadRequest)
                    return@post
                } catch (e: Exception) {
                    call.respondJson(mapOf("error" to e.localizedMessage), HttpStatusCode.BadRequest)
                    return@post
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
                        var user = userService.getUserByEmail(email)
                        user ?: run {
                            val error = mapOf("error" to "User does not exist") // todo change to generic error?
                            call.respondText(
                                jsonConfig.encodeToString(error),
                                status = HttpStatusCode.NotFound
                            )
                            return@post
                        }

                        // check if the password is correct
                        val userPasswordHash = user.password
                        if (!passwordService.validatePassword(password, userPasswordHash)) {
                            val error = mapOf("error" to "Invalid credentials")
                            call.respondText(
                                jsonConfig.encodeToString(error),
                                status = HttpStatusCode.Unauthorized
                            )
                            return@post
                        }

                        // Add the client ip address to the user's ip address whitelist (if it doesn't already exist)
                        // Note: This is to prevent a potential malicious attacker from using the same token from a different IP address.
                        // (that they havent yet authenticated from.)
                        // This may be a good place to add a captcha or send confirmation email to prevent brute force attacks.
                        if (!user.clientIpAddressWhiteList.contains(clientIpAddress)) {
                            val newClientIpWhitelistAddresses = user.clientIpAddressWhiteList.toMutableList()

                            newClientIpWhitelistAddresses.add(clientIpAddress)
                            user = userService.updateUser(user.copy(clientIpAddressWhiteList = newClientIpWhitelistAddresses))
                        }

                        call.login(clientIpAddress, user)
                        return@post
                    }
                    call.respondJson(mapOf("error" to "Invalid parameters"), HttpStatusCode.BadRequest)
                    return@post
                } catch (e: Exception) {
                    call.respondJson(mapOf("error" to e.localizedMessage), HttpStatusCode.BadRequest)
                    return@post
                }
            }

            post("/api/logout") {
                try {
                    val body = call.receiveText()
                    val params = jsonConfig.decodeFromString<Map<String, String>>(body)

                    @Suppress("USELESS_CAST") // need to indicate String type is a JWT token string
                    val token = params["jwtToken"] as JwtTokenStr?

                    token?.let {
                        val user = userService.getUserByAuthJwtToken(token)
                        user?.let {
                            userService.updateUser(
                                user.copy(
                                    authToken = "",
                                    authJwtToken = ""
                                )
                            )

                            call.respondJson(map = mapOf("success" to "true"))
                            return@post
                        }

                        call.respondJson(mapOf("error" to "Invalid token"), HttpStatusCode.Unauthorized)
                        return@post
                    }
                    call.respondJson(mapOf("error" to "Invalid parameters"), HttpStatusCode.BadRequest)
                } catch (e: Exception) {
                    call.respondJson(mapOf("error" to e.localizedMessage), HttpStatusCode.BadRequest)
                }
            }

            get("/api/send-password-reset-email") {
                val emailAddress = call.request.queryParameters["emailAddress"]
                emailAddress ?: run {
                    call.respondJson(mapOf("error" to "Email address is required"), HttpStatusCode.BadRequest)
                    return@get
                }

                // check if the user exists
                val user = userService.getUserByEmail(emailAddress)
                user ?: run {
                    call.respondJson(mapOf("error" to "User does not exist"), HttpStatusCode.BadRequest) // todo: Dont reveal this information
                    return@get
                }

                // generate password reset token
                val passwordResetToken = UUID.randomUUID().toString()
                val passwordResetJwtToken = jwtService.generatePasswordResetToken(user)

                // save the password reset token to the user's account
                user.let {
                    userService.updateUser(
                        user.copy(
                            passwordResetToken = passwordResetToken,
                            passwordResetJwtToken = passwordResetJwtToken
                        )
                    )
                }

                val res = sendPasswordResetEmail(emailAddress = emailAddress, passwordResetJwtToken)
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

                if (passwordResetToken == null || newPassword == null) {
                    call.respondJson(
                        mapOf("error" to "Password reset token and new password are required"),
                        HttpStatusCode.BadRequest
                    )
                    return@post
                }

                // validate the password reset token
                // old way using UUID - NOTE: LEAVE for reference
                //if (passwordResetToken.length != 36) {
                //    call.respondJson(mapOf("error" to "Invalid password reset token"), HttpStatusCode.BadRequest)
                //    return@post
                //}

                if (!validatePassword(newPassword)) return@post

                // find the user with the password reset token
                // val user = usersDb.values.find { it.passwordResetToken == passwordResetToken } // checks UUID old way - NOTE: LEAVE for reference
                val user = userService.getUserByPasswordResetJwtToken(passwordResetToken)
                user?.let {
                    // validate the password reset token
                    // new way using JWT
                    val passwordResetTokenClaims = jwtService.verify(passwordResetToken)
                    val passwordResetTokenExpiration = passwordResetTokenClaims.claims["exp"]?.asLong()
                        ?: run {
                            call.respondJson(
                                mapOf("error" to "Invalid password reset token - missing expiry"),
                                HttpStatusCode.BadRequest
                            )
                            return@post
                        }
                    passwordResetTokenExpiration.let {
                        val now = Date().time / 1000
                        if (now > passwordResetTokenExpiration) {
                            call.respondJson(
                                mapOf("error" to "Password reset token has expired"),
                                HttpStatusCode.BadRequest
                            )
                            return@post
                        }
                    }
                    if (passwordResetTokenClaims.claims["type"]?.asString() != "passwordReset") {
                        call.respondJson(mapOf("error" to "Invalid password reset token"), HttpStatusCode.BadRequest)
                        return@post
                    }
                    val passwordResetTokenEmail = passwordResetTokenClaims.claims["email"]?.asString()
                    if (passwordResetTokenEmail != user.email) {
                        call.respondJson(
                            mapOf("error" to "Invalid email in password reset token"),
                            HttpStatusCode.BadRequest
                        )
                        return@post
                    }
                    if (passwordResetTokenClaims.claims["id"]?.asString() != user.id) {
                        call.respondJson(
                            mapOf("error" to "Invalid user id in password reset token"),
                            HttpStatusCode.BadRequest
                        )
                        return@post
                    }

                    // update the user's password & clear the password reset token(s)
                    val passwordHash = passwordService.getSaltedPepperedPasswordHash(newPassword)
                    val updatedUser =
                        user.copy(
                            password = passwordHash,
                            passwordResetToken = "",
                            passwordResetJwtToken = ""
                        )
                    userService.updateUser(updatedUser)

                    call.respondJson(map = mapOf("success" to "Password updated"))
                    return@post
                } ?: run {
                    call.respondJson(mapOf("error" to "Invalid password reset token"), HttpStatusCode.BadRequest)
                    return@post
                }
            }
        }

        // api routes are protected by authentication
        authenticate("auth-bearer") {

            get("/api/todo_echo") {
                val body = call.receiveText()
                try {
                    val todos = jsonConfig.decodeFromString<TodoResponse>(body)

                    call.respond(jsonConfig.encodeToString(todos))
                    return@get

                } catch (e: Exception) {
                    call.respondJson(mapOf("error" to e.localizedMessage), HttpStatusCode.BadRequest)
                    return@get
                }
            }

            get("/api/todos") {
                // make call to local database server
                val response = client.get(applicationConfig.databaseBaseUrl + "/todos")
                if (response.status.value == 200) {
                    try {
                        val body = response.body<String>()
                        val todos = jsonConfig.decodeFromString<TodoResponse>(body)

//                    // Simulate this server editing data before sending it back to the client
//                    val todo = todos[0]
//                    val userInTodo = UserInTodo("John")
//                    val updatedTodo = todo.copy(userInTodo = userInTodo)
//                    todos[0] = updatedTodo

//                    call.response.apply {
//                        headers.append("Content-Type", "application/json")
//                    }
                        call.respond(jsonConfig.encodeToString(todos))
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
                                ktorLogger.error("/upload-image Unknown PartData.???")
                            }
                        }
                    }

                    val newTodo = Todo(
                        id = "1",
                        name = name.toString(),
                        status = ToDoStatus.Pending,
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
            filesPath = "/Volumes/TRS-83/dev/WebAppPlayground"
        }
    }
}
