package com.realityexpander.domain.todo

import kotlinx.serialization.Serializable

@Serializable
enum class ToDoStatus(val value: String) {
    Pending("Pending"),
    Completed("Completed"),
    Archived("Archived")
}