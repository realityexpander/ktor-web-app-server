package data.local

import kotlinx.serialization.Serializable

@Serializable
data class FileUploadResponse(
    val todo: Todo,
    val uploadedFiles: List<String>
)