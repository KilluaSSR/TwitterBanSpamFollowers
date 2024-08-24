package killua.dev

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.FileReader
import java.io.FileWriter

data class BlockingConfig(
    val userKeywords: List<String>,
    val excludeKeywords: List<String>,
)

data class BlockCheckResult(val shouldBlock: Boolean, val matchingKeywords: List<String>)

fun loadBlockingConfig() {
    if (configFile.exists()) {
        val json = FileReader(configFile).readText()
        val gson = Gson()
        val type = object : TypeToken<BlockingConfig>() {}.type
        blockingConfig = gson.fromJson(json, type)
    } else {
        println("Config file not found. Using default values.")
        // Set default values if config file does not exist
        blockingConfig = BlockingConfig(
            userKeywords = emptyList(),
            excludeKeywords = emptyList()
        )
        println("Config file blocking_rules.json not found. Creating a new one with default values.")
        // Set default values if config file does not exist
        blockingConfig = BlockingConfig(
            userKeywords = emptyList(),
            excludeKeywords = emptyList()
        )
        // Create a new config file with default values
        val gson = Gson()
        val json = gson.toJson(blockingConfig)
        FileWriter(configFile).use { it.write(json) }
    }
}
