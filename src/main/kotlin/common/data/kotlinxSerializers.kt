package com.realityexpander.common.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object PairSerializer : KSerializer<Pair<*, *>> {
    override val descriptor = PrimitiveSerialDescriptor("Pair", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Pair<*, *> {
        return Pair(decoder.decodeString(), decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Pair<*, *>) {
        encoder.encodeString(value.toString())
    }
}