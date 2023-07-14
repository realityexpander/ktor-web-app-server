package com.realityexpander.domain.remote

import kotlinx.serialization.Serializable

@Serializable
data class SuccessResponse(
    val success: String,
)