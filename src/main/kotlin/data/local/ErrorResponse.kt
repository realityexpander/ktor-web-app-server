package data.local

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String
)