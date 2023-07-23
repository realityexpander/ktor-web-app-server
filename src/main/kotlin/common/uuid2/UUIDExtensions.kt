package com.realityexpander.common.uuid2

import com.google.gson.Gson
import common.uuid2.UUID2
import domain.account.Account
import domain.book.Book
import domain.user.User
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

fun String.toUUID(): UUID = UUID.fromString(this)