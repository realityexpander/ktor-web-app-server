package domain

import kotlinx.serialization.Serializable

@Serializable
enum class ToDoStatus(val value: String) {
    pending("pending"),
    completed("completed"),
    archived("archived")
}