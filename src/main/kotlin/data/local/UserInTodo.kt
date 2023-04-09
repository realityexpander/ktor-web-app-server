package data.local

import kotlinx.serialization.Serializable

@Serializable
data class UserInTodo(
    val name: String,
    val files: List<String>? = null
)