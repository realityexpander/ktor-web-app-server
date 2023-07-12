package com.realityexpander.domain.todo

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Todo(
    val id: String,
    val name: String,
    val status: ToDoStatus = ToDoStatus.Pending,
    @SerialName("user")
    val userInTodo: UserInTodo? = null
)