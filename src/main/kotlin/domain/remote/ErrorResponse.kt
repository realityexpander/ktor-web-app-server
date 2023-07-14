package com.realityexpander.domain.remote

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String
)