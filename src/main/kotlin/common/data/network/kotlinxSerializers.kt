package com.realityexpander.common.data.network

import com.realityexpander.jsonConfig
import io.ktor.http.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import util.JsonString

object PairSerializer : KSerializer<Pair<*, *>> {
    override val descriptor = PrimitiveSerialDescriptor("Pair", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Pair<*, *> {
        return Pair(decoder.decodeString(), decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Pair<*, *>) {
        encoder.encodeString(value.toString())
    }
}

object AnySerializer : KSerializer<Any> {
    override val descriptor = PrimitiveSerialDescriptor("Any", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Any {
        return decoder.decodeString()
    }

    override fun serialize(encoder: Encoder, value: Any) {
        encoder.encodeString(value.toString())
    }
}

@Serializable
sealed class SerializedResult(
    val result: String,  // "Success" or "Failure"
) {
    @Serializable
    data class Success(val value: String) : SerializedResult("Success")

    @Serializable
    class Failure(val errorMessage: String) : SerializedResult("Failure")
}

object ResultSerializer : KSerializer<Result<*>> {
    override val descriptor = PrimitiveSerialDescriptor("SerializedResult", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Result<*> {
        val valueStr = decoder.decodeString()

        // todo fix - this does not work. It always returns a failure. Need to serialize the type of the value & Test
        return when {
            valueStr.startsWith("Success") -> {
                val success = decoder.decodeSerializableValue(SerializedResult.Success.serializer())
                Result.success(success.value)
            }
            valueStr.startsWith("Failure") -> {
                val failure = decoder.decodeSerializableValue(SerializedResult.Failure.serializer())
                Result.failure<Nothing>(Exception(failure.errorMessage))
            }
            else -> {
                Result.failure<Nothing>(Exception("Unknown error"))
            }
        }
    }

    override fun serialize(encoder: Encoder, value: Result<*>) {
        if(value.isSuccess) {
            encoder.encodeSerializableValue(  // unlike `encodeString`, this will NOT add quotes around the string
                SerializedResult.Success.serializer(),
                SerializedResult.Success(
                    jsonConfig.encodeToString(
                        value.getOrThrow()
                    )
                )
            )
        } else {
            encoder.encodeSerializableValue(  // unlike `encodeString`, this will NOT add quotes around the string
                SerializedResult.Failure.serializer(),
                SerializedResult.Failure(
                    value.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                )
            )
        }
    }

}