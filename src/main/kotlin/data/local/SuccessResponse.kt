package data.local

import kotlinx.serialization.Serializable

@Serializable
data class SuccessResponse(
    val success: String,
)