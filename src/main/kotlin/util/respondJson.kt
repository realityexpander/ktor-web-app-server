package util

import com.realityexpander.jsonConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.encodeToString

suspend fun ApplicationCall.respondJson(
    map: Map<String, String> = mapOf(),
    status: HttpStatusCode = HttpStatusCode.OK
) {
    respondText(jsonConfig.encodeToString(map), ContentType.Application.Json, status)
}

