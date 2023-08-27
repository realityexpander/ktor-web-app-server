package com.realityexpander

import com.github.slugify.Slugify
import com.realityexpander.Constants.APPLICATION_PROPERTIES_FILE
import com.realityexpander.common.data.local.JsonRedisDatabase.Companion.escapeRedisSearchSpecialCharacters
import com.realityexpander.common.data.local.JsonRedisDatabase.RedisCommands
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
import com.redis.lettucemod.search.CreateOptions
import com.redis.lettucemod.search.Field
import com.redis.lettucemod.search.SearchOptions
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
import io.ktor.server.http.content.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.*
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.XGroupCreateArgs
import io.lettuce.core.XReadArgs
import kotlinx.coroutines.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.iharder.Base64
import org.example.SubModule2
import org.slf4j.LoggerFactory
import util.JsonString
import util.JwtTokenStr
import util.getClientIpAddressFromRequest
import util.respondJson
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.text.SimpleDateFormat
import java.time.Duration
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
    println("x: $x") // using net.iharder.Base64 library - its a transitive dependency of subModule2

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
    installServer(WebSockets) {
        pingPeriod = Duration.ofSeconds(60)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
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

    // Setup Redis LettuceMod client
    val redis = setupRedisClient()

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
            authenticate { tokenCredential -> // tokenCredential comes from the Authorization header

                // Check the client IP address is in the whitelist for this user
                val clientIpAddress = request.call.getClientIpAddressFromRequest()

                // Auth Token can be passed in the HEADER or in a COOKIE
                if (tokenCredential.token.isEmpty() && this.request.cookies.rawCookies["authenticationToken"] == null) {
                    this.response.status(HttpStatusCode.Unauthorized)
                    this.response.header("Location", "/login")
                    return@authenticate null
                }
                val authenticationToken =
                    if (tokenCredential.token.isNotBlank())
                        tokenCredential.token  // from the Authorization header
                    else
                        this.request.cookies.rawCookies["authenticationToken"]

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

        libraryWebApp()

        route("/redis") {

            // gets string objects
            // http://localhost:8081/redis/get?key=user:100
            get("/get") {
                val key = call.request.queryParameters["key"]
                key ?: run {
                    call.respondJson(mapOf("error" to "Missing key"), HttpStatusCode.BadRequest)
                    return@get
                }

                val value = redis.coroutine.get(key) ?: run {
                    call.respondJson(mapOf("error" to "Key not found"), HttpStatusCode.NotFound)
                    return@get
                }
                call.respondJson(mapOf("key" to key, "value" to value))
            }

            // sets string objects
            // http://localhost:8081/redis/set?key=user:100&value=%22peaches%22
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

                val result = redis.coroutine.set(key, value) ?: run {
                    call.respondJson(mapOf("error" to "Failed to set key"), HttpStatusCode.InternalServerError)
                    return@get
                }
                call.respondJson(mapOf("success" to result))
            }

            // http://localhost:8081/redis/keys
            get("/keys") {
                val keys = redis.coroutine.keys("*")
                val output: ArrayList<String> = arrayListOf()
                keys.collect { key ->
                    output += key
                }
                call.respondJson(mapOf("keys" to output.toString()))
            }

            // http://localhost:8081/redis/jsonGet?key=user:1
            // http://localhost:8081/redis/jsonGet?key=user:1&paths=$
            // http://localhost:8081/redis/jsonGet?key=user:1&paths=$.name
            get("/jsonGet") {
                val key = call.request.queryParameters["key"]
                key ?: run {
                    call.respondJson(mapOf("error" to "Missing key"), HttpStatusCode.BadRequest)
                    return@get
                }
                val paths = call.request.queryParameters["paths"] ?: "$"

                val value = redis.reactive.jsonGet(key, paths) ?: run {
                    call.respondJson(mapOf("error" to "Key not found"), HttpStatusCode.NotFound)
                    return@get
                }
                call.respondJson(mapOf("key" to key, "value" to (value.block()?.toString() ?: "null")))
            }

            // http://localhost:8081/redis/jsonSet?key=user:1&paths=$.name&value=%22Jimmy%22
            get("/jsonSet") {
                val key = call.request.queryParameters["key"] ?: run {
                    call.respondJson(mapOf("error" to "Missing key"), HttpStatusCode.BadRequest)
                    return@get
                }
                val paths = call.request.queryParameters["paths"] ?: "$"
                val value = call.request.queryParameters["value"] ?: run {
                    call.respondJson(mapOf("error" to "Missing value"), HttpStatusCode.BadRequest)
                    return@get
                }

                val result = redis.reactive.jsonSet( key, paths, value) ?: run {
                    call.respondJson(mapOf("error" to "Failed to set key"), HttpStatusCode.InternalServerError)
                    return@get
                }
                call.respondJson(mapOf("success" to Json.encodeToString(result.block())))
            }

            // http://localhost:8081/redis/jsonFind?index=users_index&query=@name:bil*
            get("/jsonFind") {
                val index = call.request.queryParameters["index"] ?: run {
                    call.respondJson(mapOf("error" to "Missing index"), HttpStatusCode.BadRequest)
                    return@get
                }
                val query = call.request.queryParameters["query"] ?: run {
                    call.respondJson(mapOf("error" to "Missing query"), HttpStatusCode.BadRequest)
                    return@get
                }

                val result =
                    redis.sync.ftSearch(
                        index,
                        query,
                        SearchOptions.builder<String, String>()
                            .limit(0, 100)
                            .withSortKeys(true)
                            .build()
                    ) ?: run {
                        call.respondJson(mapOf("error" to "Failed to find key"), HttpStatusCode.InternalServerError)
                        return@get
                    }
                val searchResults: List<Map<String, String>> =
                    result.map { document ->
                        document.keys.map { key ->
                            key to document.get(key).toString()
                        }.toMap()
                    }
                println(searchResults)
                call.respondJson(mapOf("success" to Json.encodeToString(searchResults)))
            }

            get("/sendStreamMessages") {
                val key = call.request.queryParameters["key"] ?: run {
                    call.respondJson(mapOf("error" to "Missing key"), HttpStatusCode.BadRequest)
                    return@get
                }
                val value = call.request.queryParameters["value"] ?: run {
                    call.respondJson(mapOf("error" to "Missing value"), HttpStatusCode.BadRequest)
                    return@get
                }

                val result = redis.sync.xadd("mystream", key, value) ?: run {
                    call.respondJson(mapOf("error" to "Failed to set key"), HttpStatusCode.InternalServerError)
                    return@get
                }
                call.respondJson(mapOf("success" to Json.encodeToString(result)))
            }

            var lastOffset = "0-0"
            get("/readStreamMessages") {
                val result =
                    redis.sync.xread(
                        XReadArgs.Builder. block(1000),
                        XReadArgs.StreamOffset.from("mystream", lastOffset))
                        ?: run {
                            call.respondJson(mapOf("error" to "Failed to read stream"), HttpStatusCode.InternalServerError)
                            return@get
                        }
                if(result.isEmpty()) {
                    call.respondJson(mapOf("success" to "No new messages"))
                    return@get
                }

                lastOffset = result.last().id
                val resultStringMap = result.map { entry ->
                    entry.id to entry.body.map { message ->
                        message.key to message.value
                    }.toMap()
                }.toMap()

                call.respondJson(mapOf("success" to Json.encodeToString(resultStringMap)))
            }

            // Used by "/server-push" route in webApp
            webSocket("/getMessages") {
                send("""{"status":"Channel opened"}""")
                var lastMessageOffset = "0-0"

                // DEL mystream  // delete stream
                // XRANGE mystream - + COUNT 0 // get all messages
                // XREAD BLOCK 0 STREAMS mystream 0-0 // get all messages

                while (true) {
                    val result =
                        redis.sync.xread(
                            XReadArgs.Builder. block(50),
                            XReadArgs.StreamOffset.from("mystream", lastMessageOffset))
                            ?: run {
                                send(Json.encodeToString(mapOf("error" to "Failed to read stream")))
                                return@webSocket
                            }
                    if(result.isEmpty()) {
                        send("No new messages")
                        continue
                    }

                    lastMessageOffset = result.last().id
                    val resultStringMap: MutableMap<String, Map<String, String>> = result.map { entry ->
                        entry.id to entry.body.map { message ->
                            message.key to message.value
                        }.toMap()
                    }.toMap().toMutableMap()
                    resultStringMap["status"] = mapOf("status" to "New messages")
                    resultStringMap["session"] = mapOf("id" to this.call.request.header("Sec-WebSocket-Key").toString())

                    send(Json.encodeToString(resultStringMap))

                    if(this.isActive) {
//                        delay(10)
                         yield()
                    } else {
                        break
                    }
                }
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

                get("findBook/{field}/{searchTerm}") {
                    val field = call.parameters["field"]?.toString()
                    val searchTerm = call.parameters["searchTerm"]?.toString()
                    field ?: run {
                        call.respondJson(mapOf("error" to "Missing field"), HttpStatusCode.BadRequest)
                        return@get
                    }
                    searchTerm ?: run {
                        call.respondJson(mapOf("error" to "Missing searchTerm"), HttpStatusCode.BadRequest)
                        return@get
                    }

                    val bookInfoResult = libraryAppContext.bookInfoRepo.findBookInfosByField(field, searchTerm)

                    val statusJson = resultToStatusCodeJson<List<BookInfo>>(bookInfoResult)
                    call.respond(statusJson.statusCode, statusJson.json)
                }

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

                // catch-all 404 route for library app
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
            filesPath = "/Volumes/TRS-83/dev/Web Projects/Current Project/WebAppPlayground"
        }
    }
}

@OptIn(ExperimentalLettuceCoroutinesApi::class)
private fun setupRedisClient(): RedisCommands
{
    val redisClient: RedisModulesClient = RedisModulesClient.create("redis://localhost:6379")
    val redisConnection: StatefulRedisModulesConnection<String, String> = redisClient.connect()
    val redis = RedisCommands(redisConnection)

    // miniTestRedisClient(redis)
    redisStreamsTest(redis)

    return redis
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

private fun redisClientMiniTest(redis: RedisCommands) {
    // MINI-TEST-REDIS-SEARCH
    redis.search.ftConfigSet("MINPREFIX", "1") // allow one character prefix for FT.SEARCH
    try {
        // check if index exists
        redis.sync.ftInfo("users_index")
    } catch (e: Exception) {
        // setup search index
        val result = redis.sync.ftCreate(
            "users_index",
            CreateOptions.builder<String, String>()
                .prefix("user:")
                .on(CreateOptions.DataType.JSON)
                .build(),
            Field.tag("$.id")  // note: TAGs do not separate words/special characters
                .`as`("id")
                .build(),
            Field.tag("$.email")
                .`as`("email")
                .build(),
            Field.text("$.name")
                .`as`("name")
                .sortable()
                .withSuffixTrie()  // for improved search (go -> going, goes, gone)
                .build()
        )

        if (result != "OK") {
            ktorLogger.error("Error creating index: $result")
        }
    }

    val redisInfo = redis.sync.ftInfo("users_index")
    println("redisInfo: $redisInfo")

    val resultRedisAdd1 = redis.sync.jsonSet(
        "user:1",
        "$", // path
        """
            {
                "id": "00000000-0000-0000-0000-000000000001",
                "email": "chris@alpha.com",
                "name": "Chris"
            }
        """.trimIndent()
    )
    println("resultRedisAdd1: $resultRedisAdd1")

    val resultRedisAdd2 = redis.sync.jsonSet(
        "user:2",
        "$",
        """
            {
                "id": "00000000-0000-0000-0000-000000000002",
                "email": "billy@beta.com",
                "name": "Billy"
            }
        """.trimIndent()
    )
    println("resultRedisAdd2: $resultRedisAdd2")

    val escapedSearchId = "0000-000000000001".escapeRedisSearchSpecialCharacters()
    val resultIdSearch = redis.sync.ftSearch(
        "users_index",
        "@id:{*$escapedSearchId*}" // search for '0000-000000000001' in id
    )
    println("resultIdSearch: $resultIdSearch")

    val resultTagSearch = redis.sync.ftSearch(
        "users_index",
        "@email:{*ch*}" // search for 'ch' in email, note use curly-braces for TAG type
    )
    println("resultTagSearch: $resultTagSearch")

    val resultTextSearch = redis.sync.ftSearch(
        "users_index",
        "@name:*bi*" // search for 'bi' in name, note NO curly-braces for TEXT type
    )
    println("resultTextSearch: $resultTextSearch")

    @Serializable
    data class UserSearchResult(
        val id: String,
        val email: String,
        val name: String,
    )

    val resultArray = resultTagSearch.map { resultMap ->
        val resultValue = resultMap.get("$") as String
        jsonConfig.decodeFromString<UserSearchResult>(resultValue)
    }
    println("resultArray: $resultArray")
}

private fun redisStreamsTest(redis: RedisCommands) {

    val streamName = "mystream"
    val consumerGroupName = "mygroup"
    val consumerName = "myconsumer"

    // add messages to stream
    // XADD mystream * command start
    redis.sync.xadd(streamName, mapOf("command" to "start"))
    redis.sync.xadd(streamName, mapOf("command" to "print", "data" to "hello, world"))
//    redis.sync.xadd(streamName, mapOf("command" to "quit"))

    // Check if consumer group exists
    // XINFO GROUPS mystream
    // XGROUP DESTROY mystream mygroup
    val consumerGroupInfo = redis.sync.xinfoGroups(streamName)
    val NAME_FIELD_IDX = 1
    if (consumerGroupInfo.none { element -> (element as ArrayList<*>)[NAME_FIELD_IDX] == consumerGroupName })
        // create the consumer group
        // XGROUP CREATE mystream mygroup $
        redis.sync.xgroupCreate(
            XReadArgs.StreamOffset.from(streamName, "0"),
            consumerGroupName,
            XGroupCreateArgs()
                .mkstream(true)
        )

    CoroutineScope(Dispatchers.IO).launch {
        do {
            var quit = false
            yield()

            try {
                // XREADGROUP GROUP mygroup myconsumer BLOCK 0 STREAMS mystream >
                val streamMessages = redis.sync.xreadgroup(
                    io.lettuce.core.Consumer.from(consumerGroupName, consumerName),
                    XReadArgs.StreamOffset.lastConsumed(streamName)
                )

                for (message in streamMessages) {
                    val messageId = message.id
                    val messageData = message.body.entries.joinToString(", ") { "${it.key}=${it.value}" }

                    println("Received message with ID: $messageId")
                    println("Message data: $messageData")

                    message.body.entries.forEach { entry ->
                        println("entry: [${entry.key}] = ${entry.value}")

                        when(entry.key.toString()) {
                            "command" -> {
                                when(entry.value.toString()) {
                                    "start" -> {
                                        println("starting...")
                                    }
                                    "print" -> {
                                        println("printing... ${message.body["data"]}")
                                    }
                                    "quit" -> {
                                        println("quitting...")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("Exception: ${e.localizedMessage}")
                // quit = true
            }
        } while (quit == false)
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