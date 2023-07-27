package util

import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import java.util.*

fun ApplicationCall.getClientIpAddressFromRequest(suggestedClientIpAddress: String? = null): String {
    val call = this
    val ipFromForwardedForHeader = call.request.header("X-Forwarded-For")
    val ipFromCookies = call.request.cookies["clientIpAddress"]

    @Suppress("UNUSED_VARIABLE")
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
            // Generate a UUID instead of `localhost`, because `localhost` is not a valid IP. 
            // This will be the unique ID for the Application instance.
            UUID.randomUUID().toString()
        }
    }
}
