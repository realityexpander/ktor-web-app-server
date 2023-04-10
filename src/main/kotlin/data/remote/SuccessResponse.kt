package data.remote

import kotlinx.serialization.Serializable

@Serializable
data class SuccessResponse(
    val success: String,
)