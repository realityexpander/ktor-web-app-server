package util

import com.realityexpander.authRepo
import common.uuid2.UUID2
import domain.user.User
import io.ktor.http.*
import io.ktor.server.application.*
import javax.naming.AuthenticationException

suspend fun ApplicationCall.getUserIdFromCookies(): Result<UUID2<User>> {
    val call = this
    // check the auth token & client ip from the request cookies
    val authToken = call.request.cookies["authenticationToken"]
    val clientIpAddress = call.getClientIpAddressFromRequest()

    val user = authRepo.findUserByAuthToken(authToken) ?: run {
        call.respondJson(mapOf("error" to "Invalid authToken"), HttpStatusCode.Unauthorized)
        return Result.failure(AuthenticationException("Invalid authToken"))
    }

    // validate client ip
    if (!user.clientIpAddressWhiteList.contains(clientIpAddress)) {
        call.respondJson(mapOf("error" to "Invalid clientIpAddress"), HttpStatusCode.Unauthorized)
        return Result.failure(AuthenticationException("Invalid clientIpAddress"))
    }

    return Result.success(user.id)
}