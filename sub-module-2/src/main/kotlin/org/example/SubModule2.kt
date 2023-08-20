package org.example

import net.iharder.Base64

object SubModule2 {
    @JvmStatic
    fun main(args: Array<String>) {
        System.out.printf("Hello and welcome!")

        for (i in 1..5) {

            println("i = $i")
        }
    }

    fun output(value: String)  {
        println("Value from subclass 2: $value")

        val z = "hello"
        val result = Base64.encodeObject(z)

        println("result: $result")
    }
}