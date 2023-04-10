package domain

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.pipeline.*
import util.respondJson

suspend fun PipelineContext<Unit, ApplicationCall>.validatePassword(
    newPassword: String
): Boolean {
    // validate password
    if (newPassword.length < 8) {
        call.respondJson(mapOf("error" to "Password must be at least 8 characters"), HttpStatusCode.BadRequest)
        return false
    }
    if (!newPassword.matches(Regex(".*[A-Z].*"))) {
        call.respondJson(
            mapOf("error" to "Password must contain at least one uppercase letter"),
            HttpStatusCode.BadRequest
        )
        return false
    }
    if (!newPassword.matches(Regex(".*[a-z].*"))) {
        call.respondJson(
            mapOf("error" to "Password must contain at least one lowercase letter"),
            HttpStatusCode.BadRequest
        )
        return false
    }
    if (!newPassword.matches(Regex(".*[0-9].*"))) {
        call.respondJson(
            mapOf("error" to "Password must contain at least one number"),
            HttpStatusCode.BadRequest
        )
        return false
    }
    if (!newPassword.matches(Regex(".*[!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*"))) {
        call.respondJson(
            mapOf("error" to "Password must contain at least one special character"),
            HttpStatusCode.BadRequest
        )
        return false
    }
    
    return true
}