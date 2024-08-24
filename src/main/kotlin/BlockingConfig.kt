package killua.dev

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.FileReader

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
    }
}