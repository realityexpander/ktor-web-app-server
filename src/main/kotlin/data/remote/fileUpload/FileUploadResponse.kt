package data.remote.fileUpload

import com.realityexpander.domain.todo.Todo
import kotlinx.serialization.Serializable

@Serializable
data class FileUploadResponse(
    val todo: Todo,
    val uploadedFiles: List<String>
)