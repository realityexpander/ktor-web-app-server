package data.remote

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String
)