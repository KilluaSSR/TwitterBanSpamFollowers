package dev.hylas.telegram

import com.mongodb.*
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.runBlocking
import org.bson.BsonInt64
import org.bson.Document

val mongoDB: MongoDatabase by lazy {
    val uri = "mongodb://localhost:27017"
    val serverApi = ServerApi.builder()
        .version(ServerApiVersion.V1)
        .build()
    val settings = MongoClientSettings.builder()
        .applyConnectionString(ConnectionString(uri))
        .serverApi(serverApi)
        .build()
    val mongoClient = MongoClient.create(settings)
    val database = mongoClient.getDatabase("who_unfo_me")
    runBlocking {
        try {
            val cmd = Document("ping", BsonInt64(1))
            database.runCommand(cmd) // test
            println("Pinged your deployment. You successfully connected to MongoDB!")
            return@runBlocking database
        }
        catch (e: MongoException) {
            System.err.println(e)
            null!!// panic
        }
    }
}
