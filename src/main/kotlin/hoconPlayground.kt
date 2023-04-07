package com.realityexpander

import com.typesafe.config.ConfigFactory
import io.github.config4k.getValue
import java.io.File

fun main() {
    val config = ConfigFactory.parseString(
        """
        |stringValue = hello
        |booleanValue = true
        |"""
            .trimMargin()
    )

    val stringValue: String by config
    println(stringValue) // hello

    val nullableStringValue: String? by config
    println(nullableStringValue) // null

    val booleanValue: Boolean by config
    println(booleanValue) // true

    val authConfig = ConfigFactory.parseFile(File("./auth.conf"))
    val port = authConfig.getInt("ktor.deployment.port")

}