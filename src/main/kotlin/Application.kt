package com.realityexpander

import com.github.slugify.Slugify
import com.realityexpander.Constants.APPLICATION_PROPERTIES_FILE
import com.realityexpander.common.data.local.JsonRedisDatabase
import com.realityexpander.common.data.network.AnySerializer
import com.realityexpander.common.data.network.PairSerializer
import com.realityexpander.common.data.network.ResultSerializer
import com.realityexpander.domain.auth.*
import com.realityexpander.domain.remote.emailer.sendPasswordResetEmail
import com.realityexpander.domain.remote.fileUpload.FileUploadResponse
import com.realityexpander.domain.remote.fileUpload.save
import com.realityexpander.domain.todo.ToDoStatus
import com.realityexpander.domain.todo.Todo
import com.realityexpander.domain.todo.TodoResponse
import com.realityexpander.domain.todo.UserInTodo
import com.redis.lettucemod.RedisModulesClient
import com.redis.lettucemod.api.StatefulRedisModulesConnection
import com.redis.lettucemod.api.reactive.RedisModulesReactiveCommands
import com.redis.lettucemod.api.sync.RedisModulesCommands
import com.redis.lettucemod.search.CreateOptions
import com.redis.lettucemod.search.Field
import common.uuid2.UUID2
import common.uuid2.UUID2.Companion.fromUUID2StrToTypedUUID2
import common.uuid2.UUID2.Companion.toUUID2WithUUID2TypeOf
import domain.Context
import domain.account.Account
import domain.account.data.AccountInfo
import domain.book.Book
import domain.book.data.BookInfo
import domain.library.Library
import domain.library.PrivateLibrary
import domain.library.data.LibraryInfo
import domain.user.User
import domain.user.data.UserInfo
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
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.lettuce.core.dynamic.RedisCommandFactory
import kotlinx.html.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.iharder.Base64
import org.example.SubModule2
import org.slf4j.LoggerFactory
import util.*
import util.testingUtils.TestingUtils
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
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
    allowStructuredMapKeys = true
    serializersModule = kotlinx.serialization.modules.SerializersModule {
        contextual(kotlin.Result::class, ResultSerializer)
        contextual(Pair::class, PairSerializer)
        contextual(Any::class, AnySerializer)
        contextual(AccountInfo::class, AccountInfo.serializer())
    }
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

// Setup the UUID2 types for UUID2 deserialization
val uuid2WhiteList = UUID2.registerUUID2TypesForWhiteListDeserialization(listOf(
    Book::class,
    Library::class,
    PrivateLibrary::class,
    User::class,
    Account::class
))

// Load the User Repository
val authRepo = AuthenticationRepository()

// Setup the LibraryApp
val libraryAppContext = Context.setupContextInstance()

@OptIn(InternalSerializationApi::class, ExperimentalLettuceCoroutinesApi::class)
fun Application.module() {

    // test sub-module-2
    SubModule2.output("hello from application module")
    val x = Base64.encodeObject("hello")

    /////////////////////
    // SERVER SETUP    //
    /////////////////////

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
            rateLimiter(limit = 15, refillPeriod = 60.seconds)
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

    // Setup Ktor client
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

    // Setup Authentication password service for hashing passwords
    val passwordService = ArgonPasswordService(
        pepper = applicationConfig.pepper // pepper is used to make the password hash unique
    )

    // Setup JWT (`environment` is config by'resources/application.conf' file)
    val secret = environment.config.config("ktor").property("jwt.secret").getString()
    val issuer = environment.config.config("ktor").property("jwt.issuer").getString()
    val audience = environment.config.config("ktor").property("jwt.audience").getString()
    val apiRealm = environment.config.config("ktor").property("jwt.realm").getString()

    // Setup JWT authentication
    val jwtService = JwtService(
        secret = secret,
        issuer = issuer,
        audience = audience,
    )


    /////////////////////////////
    // Setup Redis LettuceMod client

    val (redisCoroutineCommand, redisReactiveCommand) = setupRedisClient()


    // Setup JWT & bearer authentication
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

                val user = authRepo.findUserByEmail(email)
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

        // Note: token and clientIPAddress can be passed in:
        // - in header (tokenCredential, X-Forwarded-For)
        // - in a cookie (authenticationToken, clientIpAddress)
        // - NOT JWT
        bearer("auth-bearer") {
            realm = "Access to the 'auth-bearer' routes"
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

                // Look up the user by the authentication token
                val user = authRepo.findUserByAuthToken(authenticationToken)
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

//        // setup CORS
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
            user: UserAuthEntity
        ) {
            // Generate a new simple-auth session auth Bearer token
            val token = UUID.randomUUID().toString()

            // Generate a new session auth JWT token
            val jwtToken = jwtService.generateLoginAuthToken(user, clientIpAddress)
            authRepo.updateUser(
                user.copy(
                    authJwtToken = jwtToken,
                    authToken = token
                ))

            respondJson(mapOf(
                "userId" to user.id.toString(),
                "token" to token,
                "jwtToken" to jwtToken,
                "clientIpAddress" to clientIpAddress,
            ))
        }

        // Get userId from JWT token
        suspend fun ApplicationCall.getUserId(): UUID2<User>? {
            return  principal<UserIdPrincipal>()?.let {
                val userId = authRepo.findUserByEmail(it.name)?.id
                    ?: run {
                        respondJson(mapOf("error" to "Invalid userId, email: ${it.name}"), HttpStatusCode.BadRequest)
                        return null
                    }

                userId
            } ?: run {
                respondJson(mapOf("error" to "Invalid UserIdPrincipal"), HttpStatusCode.BadRequest)
                return null
            }

        }

        //////////////////////////
        // LibaryApp Web HTML   //
        //////////////////////////

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

                        // note: only allow one user per email address.
                        authRepo.findUserByEmail(email) ?: run {

                            val passwordHash = passwordService.getSaltedPepperedPasswordHash(password)

                            val newUser = UserAuthEntity(
                                id = UUID2(User::class.java),
                                email = email,
                                password = passwordHash,
                                authToken = "",
                                authJwtToken = "",
                                clientIpAddressWhiteList = listOf(clientIpAddress),
                            )
                            authRepo.addUser(newUser)

                            call.login(clientIpAddress, newUser)
                            return@post
                        }

                        call.respondJson(mapOf("error" to "UserAuthEntity already exists"), HttpStatusCode.Conflict)
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
                        var user = authRepo.findUserByEmail(email)
                        user ?: run {
                            val error = mapOf("error" to "User does not exist, please register.")
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
                            user = authRepo.updateUser(user.copy(clientIpAddressWhiteList = newClientIpWhitelistAddresses))
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
                        val user = authRepo.findUserByAuthJwtToken(token)
                        user?.let {
                            authRepo.updateUser(
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
                val user = authRepo.findUserByEmail(emailAddress)
                user ?: run {
                    call.respondJson(mapOf("error" to "User does not exist"), HttpStatusCode.BadRequest) // todo: Dont reveal this information
                    return@get
                }

                // generate password reset token
                val passwordResetToken = UUID.randomUUID().toString()
                val passwordResetJwtToken = jwtService.generatePasswordResetToken(user)

                // save the password reset token to the user's account
                user.let {
                    authRepo.updateUser(
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
                val user = authRepo.findUserByPasswordResetJwtToken(passwordResetToken)
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
//                    if (passwordResetTokenClaims.claims["id"]?.asString() != user.id) {
                    if (passwordResetTokenClaims.claims["id"]?.asString() != user.id.toString()) {
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
                    authRepo.updateUser(updatedUser)

                    call.respondJson(map = mapOf("success" to "Password updated"))
                    return@post
                } ?: run {
                    call.respondJson(mapOf("error" to "Invalid password reset token"), HttpStatusCode.BadRequest)
                    return@post
                }
            }

        }

        route("/libraryWeb") {

            // more examples: https://photos.google.com/photo/AF1QipNkHSZiBPmtvSZWguN_BEaBmN7a5xansX3DSRHc

            get("/") {
                call.respondHtml {
                    body {
                        h1 { +"Library App" }
                        p { +"Welcome to the Library App" }
                        br

                        a {
                            href = "listBooks/UUID2:Role.Library@00000000-0000-0000-0000-000000000001"
                            +"List Books for Library: 1"
                        }
                        br

                        a {
                            href = "upsertBook/UUID2:Role.Library@00000000-0000-0000-0000-000000000001"
                            +"Upsert Book to Library: 1"
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
                if(bookInfoResult.isFailure) {
                    call.respondJson(mapOf("error" to (bookInfoResult.exceptionOrNull()?.localizedMessage ?: "Unknown error")), HttpStatusCode.BadRequest)
                    return@get
                }
                val bookInfo = bookInfoResult.getOrThrow()
                val books = bookInfo.findAllKnownBookIds().map { Book(it, libraryAppContext) }
                val bookInfos = books.mapNotNull { book ->
                    book.info()
                }

                call.respondHtml {
                    body {
                        libraryStyle()

                        h1 { +"List Books for Library: $libraryId" }

                        bookInfos.forEach { bookInfo ->
                            p { +"Title: ${bookInfo.title}" }
                            p { +"Author: ${bookInfo.author}" }
//                            a {
//                                href = "../book/${bookInfo.id}"
//                                +"View"
//                            }
                            button(type=ButtonType.button) {
                                onClick = "viewBook(`${bookInfo.id}`)"
                                +"View"
                            }
                            button(type=ButtonType.button) {
                                onClick = "confirmDeleteBookInfo(`${bookInfo.id}`, `${bookInfo.title}`)"
                                +"Delete"
                            }
                            br
                        }
                        br

                        // go to upsert book page
                        a {
                            href = "../upsertBook/$libraryId"
                            +"Upsert Book to Library"
                        }

                        script {
                            type = ScriptType.textJavaScript
                            unsafe { raw(
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
                                        .then(res => res.json())
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
                            """.trimIndent())
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
                if(bookInfoResult.isFailure) {
                    call.respondJson(mapOf("error" to (bookInfoResult.exceptionOrNull()?.localizedMessage ?: "Unknown error")), HttpStatusCode.BadRequest)
                    return@get
                }
                val bookInfo = bookInfoResult.getOrThrow()

                call.respondHtml {
                    body {
                        h1 { +"Book: $bookId" }
                        p { +"Title: ${bookInfo.title}" }
                        p { +"Author: ${bookInfo.author}" }
                        p { +"Description: ${bookInfo.description}" }

                        // go to home
                        a {
                            href = "../"
                            +"Home"
                        }
                    }
                }
            }

            get("/upsertBookToLibrary/{libraryId}") {
                val libraryIdfromParams = call.parameters["libraryId"]?.toString()
                val libraryId = libraryIdfromParams ?: "UUID2:Role.Library@00000000-0000-0000-0000-000000000001"

                call.respondHtml {
                    body {
                        libraryStyle()

                        h1 { +"Upsert Book" }

                        button(type = ButtonType.button) {
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
                val title = params["title"]
                val author = params["author"]
                val description = params["description"]
                val isbn = params["isbn"]
                val libraryId = params["libraryId"]

                if (bookId != null &&
                    title != null &&
                    author != null &&
                    isbn != null &&
                    description != null &&
                    libraryId != null
                ) {
                    val library = Library(libraryId.fromUUID2StrToTypedUUID2<Library>(), libraryAppContext)
                    val book = Book(bookId.fromUUID2StrToTypedUUID2<Book>(), libraryAppContext)
                    val bookInfo = BookInfo(
                        id = book.id(),
                        title = title,
                        author = author,
                        description = description,
                    )

                    // add book to bookInfo repo
                    val upsertBookInfoResult = libraryAppContext.bookInfoRepo.upsertBookInfo(bookInfo)
                    val upsertBookInfostatusJson = resultToStatusCodeJson<BookInfo>(upsertBookInfoResult)

                    val addBookToLibraryResult = library.addBookToLibrary(book, 1)
                    val addBookToLibraryStatusJson: StatusCodeAndJson =
                        resultToStatusCodeJson<Book>(addBookToLibraryResult)

                    call.respondHtml {
                        body {
                            if (upsertBookInfostatusJson.statusCode != HttpStatusCode.OK) {
                                h1 { +"Error adding book to library" }
                                p { +upsertBookInfostatusJson.json }
                            } else if (addBookToLibraryStatusJson.statusCode != HttpStatusCode.OK) {
                                h1 { +"Error adding book to library" }
                                p { +addBookToLibraryStatusJson.json }
                            } else {
                                h1 { +"Book added:" }
                            }

                            p { +"Book Id: $bookId" }
                            p { +"Title: $title" }
                            p { +"Author: $author" }
                            p { +"Description: $description" }
                            p { +"ISBN: $isbn" }
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

            get("/populateBookInfo") {
                val testingUtils = TestingUtils(libraryAppContext)
                testingUtils.populateDBWithFakeBookInfo()

                val bookResult =
                    libraryAppContext
                        .bookInfoRepo
                        .bookInfoDatabase
                        .allBookInfos()
                val bookInfos = bookResult.getOrThrow()

                call.respondHtml {
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
                    body {
                        h1 { +"Book Info for $field search: $searchTerm" }
                        if(bookInfos.isEmpty()) {
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
                    body {
                        h1 { +"Add Book Info" }

                        form {
                            method = FormMethod.post
                            action = "./addBookInfo"
                            encType = FormEncType.textPlain // uses key/value pairs, one per line

                            label {
                                +"UUID2 id"
                                textInput {
                                    name = "id"
                                }
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

                            submitInput {
                                value = "Add Book Info"
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
        }

        route("/redis") {

            get("/get") {
                val key = call.request.queryParameters["key"]
                key ?: run {
                    call.respondJson(mapOf("error" to "Missing key"), HttpStatusCode.BadRequest)
                    return@get
                }

                val value = redisCoroutineCommand.get(key) ?: run {
                    call.respondJson(mapOf("error" to "Key not found"), HttpStatusCode.NotFound)
                    return@get
                }
                call.respondJson(mapOf("key" to key, "value" to value))
            }

            get("/set") {
                val key = call.request.queryParameters["key"]
                val value = call.request.queryParameters["value"]
                key ?: run {
                    call.respondJson(mapOf("error" to "Missing key"), HttpStatusCode.BadRequest)
                    return@get
                }
                value ?: run {
                    call.respondJson(mapOf("error" to "Missing value"), HttpStatusCode.BadRequest)
                    return@get
                }

                val result = redisCoroutineCommand.set(key, value) ?: run {
                    call.respondJson(mapOf("error" to "Failed to set key"), HttpStatusCode.InternalServerError)
                    return@get
                }
                call.respondJson(mapOf("success" to result))
            }

            get("/keys") {
                val keys = redisCoroutineCommand.keys("*")
                val output: ArrayList<String> = arrayListOf()
                keys.collect { key ->
                    output += key
                }
                call.respondJson(mapOf("keys" to output.toString()))
            }

            get("/jsonGet") {
                val key = call.request.queryParameters["key"]
                key ?: run {
                    call.respondJson(mapOf("error" to "Missing key"), HttpStatusCode.BadRequest)
                    return@get
                }

                val value = redisReactiveCommand.jsonGet("json1", key) ?: run {
                    call.respondJson(mapOf("error" to "Key not found"), HttpStatusCode.NotFound)
                    return@get
                }
                call.respondJson(mapOf("key" to key, "value" to (value.block()?.toString() ?: "null")))
            }

            get("/jsonSet") {
                val key = call.request.queryParameters["key"] ?: run {
                    call.respondJson(mapOf("error" to "Missing key"), HttpStatusCode.BadRequest)
                    return@get
                }
                val value = call.request.queryParameters["value"] ?: run {
                    call.respondJson(mapOf("error" to "Missing value"), HttpStatusCode.BadRequest)
                    return@get
                }

                val result = redisReactiveCommand.jsonSet("json1", key, value) ?: run {
                    call.respondJson(mapOf("error" to "Failed to set key"), HttpStatusCode.InternalServerError)
                    return@get
                }
                call.respondJson(mapOf("success" to Json.encodeToString(result.block())))
            }

        }

        // for `jsonPlaceholder-item` in webApp
        post("/todo_echo") {
            val body = call.receiveText()
            try {
                val todos = jsonConfig.decodeFromString<TodoResponse>(body)

                call.respond(jsonConfig.encodeToString(todos))
                return@post

            } catch (e: Exception) {
                call.respondJson(mapOf("error" to e.localizedMessage), HttpStatusCode.BadRequest)
                return@post
            }
        }

        // api routes are protected by Bearer simple authentication
        authenticate("auth-bearer") {

            route("/api") {

//                get("/todo_echo") {
//                    val body = call.receiveText()
//                    try {
//                        val todos = jsonConfig.decodeFromString<TodoResponse>(body)
//
//                        call.respond(jsonConfig.encodeToString(todos))
//                        return@get
//
//                    } catch (e: Exception) {
//                        call.respondJson(mapOf("error" to e.localizedMessage), HttpStatusCode.BadRequest)
//                        return@get
//                    }
//                }

                get("/todos") {
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
                post("/upload-image") {
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

                // 404 route for api
                get("/{...}") {
                    val route = call.request.path()
                    call.respondJson(mapOf("error" to "Invalid route for Api: $route"), HttpStatusCode.NotFound)
                }
            }


            //////////////////
            // Library Api  //
            //////////////////

            route("/libraryApi") {

                ////////////////
                // • USER     //
                ////////////////

                get("/fetchUserInfo/{userId}") {
                    val userId = call.parameters["userId"]?.toString()
                    userId ?: run {
                        call.respondJson(mapOf("error" to "Invalid id"), HttpStatusCode.BadRequest)
                        return@get
                    }

                    val user = User(userId.fromUUID2StrToTypedUUID2<User>(), libraryAppContext)
                    val userInfoResult = user.fetchInfoResult()

                    val statusJson = resultToStatusCodeJson<UserInfo>(userInfoResult)
                    call.respond(statusJson.statusCode, statusJson.json)
                }

                post("/createUser") {
                    val userInfo = call.receive<UserInfo>()

                    // • ACTION: Create User
                    val upsertUserInfoResult = libraryAppContext.userInfoRepo.upsertUserInfo(userInfo)

                    // Create Role objects
                    val user = User(userInfo.id, libraryAppContext)
                    val account = Account(user.id.uuid.toUUID2WithUUID2TypeOf<Account>(), libraryAppContext)

                    // • ACTION: Register Account for User
                    val registerAccountResult = account.registerUser(user)
                    if (registerAccountResult.isFailure) {
                        //call.respondJson(mapOf("error" to (registerAccountResult.exceptionOrNull()?.localizedMessage ?: "Unknown Error")), HttpStatusCode.BadRequest)
                        val statusJson = resultToStatusCodeJson<AccountInfo>(registerAccountResult)
                        call.respond(statusJson.statusCode, statusJson.json)
                        return@post
                    }

                    val statusJson = resultToStatusCodeJson<UserInfo>(upsertUserInfoResult)
                    call.respond(statusJson.statusCode, statusJson.json)
                }

                post("/updateUser") {
                    val userInfo = call.receive<UserInfo>()

                    // • ACTION: Update User
                    val upsertUserInfoResult = libraryAppContext.userInfoRepo.upsertUserInfo(userInfo)

                    val statusJson = resultToStatusCodeJson<UserInfo>(upsertUserInfoResult)
                    call.respond(statusJson.statusCode, statusJson.json)
                }

                post("/deleteUser") {
                    val userInfo = call.receive<UserInfo>()

                    // • ACTION: Delete User
                    val deleteUserInfoResult = libraryAppContext.userInfoRepo.deleteUserInfo(userInfo)
                    if(deleteUserInfoResult.isFailure) {
                        val statusJson = resultToStatusCodeJson<Unit>(deleteUserInfoResult)
                        call.respond(statusJson.statusCode, statusJson.json)
                        return@post
                    }

                    // • ACTION: Unregister Account for User
                    val user = User(userInfo.id, libraryAppContext)
                    val account = Account(user.id.uuid.toUUID2WithUUID2TypeOf<Account>(), libraryAppContext)
                    val unRegisterAccountResult = account.unRegisterUser(user)
                    if(unRegisterAccountResult.isFailure) {
                        val statusJson = resultToStatusCodeJson<AccountInfo>(unRegisterAccountResult)
                        call.respond(statusJson.statusCode, statusJson.json)
                        return@post
                    }

                    val statusCodeAndJson: StatusCodeAndJson = resultToStatusCodeJson<Unit>(deleteUserInfoResult)
                    call.respond(statusCodeAndJson.statusCode, statusCodeAndJson.json)
                }

                post("/acceptBook/{bookId}") {
                    val bookId = call.parameters["bookId"]?.toString()
                    bookId ?: run {
                        call.respondJson(mapOf("error" to "Missing bookId"), HttpStatusCode.BadRequest)
                        return@post
                    }
                    val userId = call.getUserId() ?: run {
                        call.respondJson(mapOf("error" to "Missing userId"), HttpStatusCode.BadRequest)
                        return@post
                    }

                    // Create Role objects
                    val user = User(userId, libraryAppContext)
                    val book = Book(bookId.fromUUID2StrToTypedUUID2<Book>(), libraryAppContext)

                    // • ACTION: Accept Book from User
                    val acceptBookResult = user.acceptBook(book)

                    val statusJson = resultToStatusCodeJson<ArrayList<Book>>(acceptBookResult)
                    call.respond(statusJson.statusCode, statusJson.json)
                }

                post("/unAcceptBook/{bookId}") {
                    val bookId = call.parameters["bookId"]?.toString()
                    bookId ?: run {
                        call.respondJson(mapOf("error" to "Missing bookId"), HttpStatusCode.BadRequest)
                        return@post
                    }
                    val userId = call.getUserId() ?: run {
                        call.respondJson(mapOf("error" to "Missing userId"), HttpStatusCode.BadRequest)
                        return@post
                    }

                    // Create Role objects
                    val user = User(userId, libraryAppContext)
                    val book = Book(bookId.fromUUID2StrToTypedUUID2<Book>(), libraryAppContext)

                    // • ACTION: Unaccept Book from User
                    val unAcceptBookResult = user.unAcceptBook(book)

                    val statusJson = resultToStatusCodeJson<ArrayList<UUID2<Book>>>(unAcceptBookResult)
                    call.respond(statusJson.statusCode, statusJson.json)
                }

                ////////////////
                // • ACCOUNT  //
                ////////////////

                get("/fetchAccountInfo/{accountId}") {
                    val accountId = call.parameters["accountId"]?.toString()
                    accountId ?: run {
                        call.respondJson(mapOf("error" to "Invalid id"), HttpStatusCode.BadRequest)
                        return@get
                    }

                    val account = Account(accountId.fromUUID2StrToTypedUUID2<Account>(), libraryAppContext)
                    val accountInfoResult = account.fetchInfoResult()

                    val statusJson = resultToStatusCodeJson<AccountInfo>(accountInfoResult)
                    call.respond(statusJson.statusCode, statusJson.json)
                }

                post("/registerAccount") {
                    val userId = call.getUserId() ?: run {
                        call.respondJson(mapOf("error" to "Missing userId"), HttpStatusCode.BadRequest)
                        return@post
                    }

                    // Create Role objects
                    val user = User(userId, libraryAppContext)
                    val account = Account(user.id.uuid.toUUID2WithUUID2TypeOf<Account>(), libraryAppContext)

                    // • ACTION: Register Account for User
                    val registerAccountResult = account.registerUser(user)

                    val statusJson = resultToStatusCodeJson<AccountInfo>(registerAccountResult)
                    call.respond(statusJson.statusCode, statusJson.json)
                }

                post("/unRegisterAccount") {
                    val userId = call.getUserId() ?: run {
                        call.respondJson(mapOf("error" to "Missing userId"), HttpStatusCode.BadRequest)
                        return@post
                    }

                    // Create Role objects
                    val user = User(userId, libraryAppContext)
                    val account = Account(user.id.uuid.toUUID2WithUUID2TypeOf<Account>(), libraryAppContext)

                    // • ACTION: Unregister Account for User
                    val unRegisterAccountResult = account.unRegisterUser(user)

                    val statusJson = resultToStatusCodeJson<AccountInfo>(unRegisterAccountResult)
                    call.respond(statusJson.statusCode, statusJson.json)
                }

                ////////////////
                // • LIBRARY  //
                ////////////////

                get("/fetchLibraryInfo/{libraryId}") {
                    val libraryId = call.parameters["libraryId"]?.toString()
                    libraryId ?: run {
                        call.respondJson(mapOf("error" to "Invalid id"), HttpStatusCode.BadRequest)
                        return@get
                    }

                    val library = Library(libraryId.fromUUID2StrToTypedUUID2<Library>(), libraryAppContext)
                    val libraryInfoResult = library.fetchInfoResult()

                    val statusJson = resultToStatusCodeJson<LibraryInfo>(libraryInfoResult)
                    call.respond(statusJson.statusCode, statusJson.json)
                }

                post("/checkOutBookFromLibrary/{bookId}/{libraryId}") {
                    val bookId = call.parameters["bookId"]?.toString()
                    val libraryId = call.parameters["libraryId"]?.toString()
                    bookId ?: run {
                        call.respondJson(mapOf("error" to "Missing bookId"), HttpStatusCode.BadRequest)
                        return@post
                    }
                    libraryId ?: run {
                        call.respondJson(mapOf("error" to "Missing libraryId"), HttpStatusCode.BadRequest)
                        return@post
                    }
                    val userId = call.getUserId() ?: run {
                        call.respondJson(mapOf("error" to "Missing userId"), HttpStatusCode.BadRequest)
                        return@post
                    }

                    // Create Role objects
                    val library = Library(libraryId.fromUUID2StrToTypedUUID2<Library>(), libraryAppContext)
                    val book = Book(bookId.fromUUID2StrToTypedUUID2<Book>(), library, libraryAppContext)
                    val user = User(userId, libraryAppContext)

                    // • ACTION: Check out Book from Library to User
                    val checkOutBookResult = library.checkOutBookToUser(book, user)

                    val statusJson = resultToStatusCodeJson<Book>(checkOutBookResult)
                    call.respond(statusJson.statusCode, statusJson.json)
                }

                post("/checkInBookToLibrary/{bookId}/{libraryId}") {
                    val bookId = call.parameters["bookId"]?.toString()
                    val libraryId = call.parameters["libraryId"]?.toString()
                    bookId ?: run {
                        call.respondJson(mapOf("error" to "Missing bookId"), HttpStatusCode.BadRequest)
                        return@post
                    }
                    libraryId ?: run {
                        call.respondJson(mapOf("error" to "Missing libraryId"), HttpStatusCode.BadRequest)
                        return@post
                    }
                    val userId = call.getUserId() ?: run {
                        call.respondJson(mapOf("error" to "Missing userId"), HttpStatusCode.BadRequest)
                        return@post
                    }

                    // Create Role objects
                    val library = Library(libraryId.fromUUID2StrToTypedUUID2<Library>(), libraryAppContext)
                    val book = Book(bookId.fromUUID2StrToTypedUUID2<Book>(), library, libraryAppContext)
                    val user = User(userId, libraryAppContext)

                    // • ACTION: Check in Book to Library from User
                    val checkInBookResult = library.checkInBookFromUser(book, user)

                    val statusJson = resultToStatusCodeJson<Book>(checkInBookResult)
                    call.respond(statusJson.statusCode, statusJson.json)
                }

                ////////////////
                // • BOOK     //
                ////////////////

                get("/fetchBookInfo/{bookId}") {
                    val bookId = call.parameters["bookId"]?.toString()
                    bookId ?: run {
                        call.respondJson(mapOf("error" to "Invalid id"), HttpStatusCode.BadRequest)
                        return@get
                    }

                    val book = Book(bookId.fromUUID2StrToTypedUUID2<Book>(), libraryAppContext)
                    val bookInfoResult = book.fetchInfoResult()

                    val statusJson = resultToStatusCodeJson<BookInfo>(bookInfoResult)
                    call.respond(statusJson.statusCode, statusJson.json)
                }

                delete("/deleteBookInfo/{bookId}") {
                    val bookId = call.parameters["bookId"]?.toString()
                    bookId ?: run {
                        call.respondJson(mapOf("error" to "Missing bookId"), HttpStatusCode.BadRequest)
                        return@delete
                    }

                    val book = Book(bookId.fromUUID2StrToTypedUUID2<Book>(), libraryAppContext)
                    val bookInfoResult = book.fetchInfoResult()
                    if(bookInfoResult.isFailure) {
                        call.respondJson(mapOf("error" to (bookInfoResult.exceptionOrNull()?.localizedMessage ?: "Unknown error")), HttpStatusCode.BadRequest)
                        return@delete
                    }
                    val bookInfo = bookInfoResult.getOrThrow()
                    val deleteBookInfoResult = libraryAppContext.bookInfoRepo.deleteBookInfo(bookInfo)

                    val statusJson = resultToStatusCodeJson<Unit>(deleteBookInfoResult)
                    call.respond(statusJson.statusCode, statusJson.json)
                }

                // 404 catch-all route for library app
                get("/{...}") {
                    val route = call.request.path()
                    call.respondJson(mapOf("error" to "Invalid route for Library: $route"), HttpStatusCode.NotFound)
                }
            }


        }

        authenticate("auth-jwt") {

            @Suppress("UNUSED_VARIABLE")
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
//            filesPath = "/Volumes/TRS-83/dev/WebAppPlayground/"
            filesPath = "/Volumes/TRS-83/dev/Web Projects/Current Project/WebAppPlayground"
        }
    }
}

fun String.parseTextPlainEncodedFormParameters(): Map<String, String> {
    val params = mutableMapOf<String, String>()
    this.lines().forEach { line ->
        val split = line.split("=")
        if (split.size == 2) {
            params[split[0]] = split[1]
        }
    }
    return params
}

@OptIn(ExperimentalLettuceCoroutinesApi::class)
private fun setupRedisClient():
    Pair<
        RedisCoroutinesCommands<String, String>,
        RedisModulesReactiveCommands<String, String>
    >
{
    val redisClient: RedisModulesClient = RedisModulesClient.create("redis://localhost:6379")
    val redisConnection: StatefulRedisModulesConnection<String, String> = redisClient.connect()
    val redisSyncCommand: RedisModulesCommands<String, String> = redisConnection.sync()
    val redisCoroutineCommand = redisConnection.coroutines()
    val redisReactiveCommand = redisConnection.reactive()

    val redisSearchCommands =
        RedisCommandFactory(redisConnection).getCommands(JsonRedisDatabase.RedisSearchCommands::class.java)
    redisSearchCommands.ftConfigSet("MINPREFIX", "1") // allow one character prefix for FT.SEARCH

    try {
        // check if index exists
        val result = redisSyncCommand.ftInfo("users_index")
    } catch (e: Exception) {
        // setup index
        val result = redisSyncCommand.ftCreate(
            "users_index",
            CreateOptions.builder<String, String>()
                .prefix("user:")
                .on(CreateOptions.DataType.JSON)
                .build(),
            Field.text("$.email")
                .`as`("email")
                .sortable()
                .withSuffixTrie()  // for improved search (allows partial word search)
                .build(),
            Field.text("$.name")
                .`as`("name")
                .sortable()
                .withSuffixTrie()  // for improved search (allows partial word search)
                .build()
        )

        if (result != "OK") {
            ktorLogger.error("Error creating index: $result")
        }
    }

    val redisInfo = redisSyncCommand.info("users_index")
    println("redisInfo: $redisInfo")

    val resultRedisAdd1 = redisSyncCommand.jsonSet(
        "user:1",
        "$", // path
        """
            {
                "email": "a@b.c",
                "name": "Chris"
            }
        """.trimIndent()
    )
    println("resultRedisAdd1: $resultRedisAdd1")

    val resultRedisAdd2 = redisSyncCommand.jsonSet(
        "user:2",
        "$",
        """
            {
                "email": "d@e.f",
                "name": "Billy"
            }
        """.trimIndent()
    )
    println("resultRedisAdd2: $resultRedisAdd2")

    val resultSearch = redisSyncCommand.ftSearch(
        "users_index",
        "@email:'*o*'"
    )
    println("resultSearch: $resultSearch")

    @Serializable
    data class UserSearchResult(
        val email: String,
        val name: String,
    )

    val resultArray = resultSearch.map { resultMap ->
        val resultValue = resultMap.get("$") as String
        jsonConfig.decodeFromString<UserSearchResult>(resultValue)
    }
    println("resultArray: $resultArray")

    return Pair(redisCoroutineCommand, redisReactiveCommand)
}

private fun BODY.libraryStyle() {
    style {
        unsafe {
            raw(
                """
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
                    """.trimIndent()
            )
        }

    }
}

data class StatusCodeAndJson(
    val json: JsonString,
    val statusCode: HttpStatusCode,
)

inline fun <reified T> resultToStatusCodeJson(
    result: Result<T>,
) : StatusCodeAndJson {

    if (result.isSuccess) {
        return StatusCodeAndJson(
            com.realityexpander.jsonConfig.encodeToString(result.getOrThrow()),
            HttpStatusCode.OK
        )
    } else {
        return StatusCodeAndJson(
            com.realityexpander.jsonConfig.encodeToString(
                mapOf(
                    "error" to (result.exceptionOrNull()?.localizedMessage
                        ?: result.exceptionOrNull()?.message
                        ?: "Unknown error")
                )),
            HttpStatusCode.BadRequest
        )
    }
}


// Simple client to test server
// https://www.youtube.com/watch?v=2bD2lq_ezVQ
fun main() {
    var message = "GET / HTTP/1.1\n\n"

    try {
        val socket = Socket("localhost", 8081)
        val out = socket.getOutputStream()
        val writer = PrintWriter(out, true)

        // send message to server
        writer.println(message)
        writer.flush()

        // read response from server
        val input = socket.getInputStream()
        val reader = BufferedReader(InputStreamReader(input))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            println(line)
        }
        reader.close()
        socket.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}