package com.realityexpander.domain.common.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class SuccessResponse(
    val success: String,
)