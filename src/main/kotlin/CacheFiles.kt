package killua.dev

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.PrintWriter
import java.util.*

val usersToBlockFile = File("users_to_block.json")
val configFile = File("blocking_rules.json")
val idsFile = File("ids_cache.txt")
val credentialFile = File("twitter_credentials.properties")

fun readCredentialsFromFile(): Pair<String, String>? {
    if (!credentialFile.exists()) {
        println("Credentials file not found.")
        return null
    }
    val properties = Properties().apply {
        load(FileReader(credentialFile))
    }

    val token = properties.getProperty("accessToken")
    val secret = properties.getProperty("accessTokenSecret")

    if (token.isNullOrBlank() || secret.isNullOrBlank()) {
        println("Invalid credentials in the file.")
        return null
    }

    return token to secret
}

fun deleteCredentialsFile() {
    if (credentialFile.exists()) {
        if (credentialFile.delete()) {
            println("Credentials file is invalid. Re-Auth please.")
        } else {
            println("Failed to delete credentials file.")
        }
    }
}

fun saveIdsToFile(ids: List<Long>) {
    idsFile.writeText("")
    PrintWriter(idsFile).use { writer ->
        ids.forEach { writer.println(it) }
    }
}

fun loadIdsFromFile(): List<Long> {
    return if (idsFile.exists()) {
        idsFile.readLines().mapNotNull { it.toLongOrNull() }
    } else {
        emptyList()
    }
}

fun saveUsersToBlock(id: Long, screenName: String, name: String) {
    // Create a JSON object with the user details
    val jsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("screenName", screenName)
        addProperty("name", name)
    }

    // Convert the JSON object to a JSON string
    val jsonString = Gson().toJson(jsonObject)

    // Append the JSON string to the file
    FileWriter(usersToBlockFile, true).use { writer ->
        writer.write("$jsonString\n")
    }
    println("User $id appended to ${usersToBlockFile.absolutePath}")
}

fun loadUserIdsToBlock(): Map<Long, Array<String>> {
    if (!usersToBlockFile.exists()) return emptyMap()

    val userMap = mutableMapOf<Long, Array<String>>()
    val jsonParser = JsonParser()

    // Read each line from the file
    usersToBlockFile.forEachLine { line ->
        try {
            // Parse the JSON line
            val jsonElement = jsonParser.parse(line)
            val jsonObject = jsonElement.asJsonObject
            // Extract the user ID, screen name, and name
            val id = jsonObject.get("id")?.asLong
            val screenName = jsonObject.get("screenName")?.asString
            val name = jsonObject.get("name")?.asString
            // Add the user details to the map
            if (id != null && screenName != null && name != null) {
                userMap[id] = arrayOf(screenName, name)
            }
        } catch (e: Exception) {
            println("Error reading line: $line, ${e.message}")
        }
    }

    return userMap
}

fun refreshFileWithCachedIds(file: File, cachedIds: Set<Long>) {
    file.writeText("") // 清空文件内容
    PrintWriter(file).use { writer ->
        cachedIds.forEach { writer.println(it) }
    }
}

fun refreshUsersToBlockFile(users: Map<Long, Array<String>>) {
    // Clear the file contents
    usersToBlockFile.writeText("")

    // Append the updated users to the file
    FileWriter(usersToBlockFile, true).use { writer ->
        users.forEach { (id, details) ->
            val jsonObject = JsonObject().apply {
                addProperty("id", id)
                addProperty("screenName", details[0])
                addProperty("name", details[1])
            }
            val jsonString = Gson().toJson(jsonObject)
            writer.write("$jsonString\n")
        }
    }

    println("Updated users saved to ${usersToBlockFile.absolutePath}")
}