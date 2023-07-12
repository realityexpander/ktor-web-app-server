package com.realityexpander.util.playgrounds

import io.fluidsonic.mongo.MongoClients
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.bson.BsonInt32
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.pojo.PojoCodecProvider

@OptIn(DelicateCoroutinesApi::class)
fun main() {

    GlobalScope.launch {

        val collection = MongoClients.create()
            .getDatabase("test")
            .getCollection("test")

        launch {

            collection.find().collect { document ->  // Kotlin Flow
                println("Event: $document")
            }

            // Setting up a codec registry allows retrieving objects as Java classes
            val pojoCodecRegistry: CodecRegistry = CodecRegistries.fromRegistries(
                MongoClients.defaultCodecRegistry,
                CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
            )

            val database = MongoClients.create()
                .getDatabase("test")
                .withCodecRegistry(pojoCodecRegistry)
            val result = database
                .getCollection("test")
                .distinct("count", Integer::class)
            val flow = result
            flow.collect { a -> println(Document("value", BsonInt32(a.toInt()))) }

            println("Done")
        }
    }
}