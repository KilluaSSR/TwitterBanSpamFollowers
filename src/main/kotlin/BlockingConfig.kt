package killua.dev

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import twitter4j.v1.User
import java.io.FileReader
import java.time.LocalDateTime

data class BlockingConfig(
    val blockNoProfilePicture: Boolean,
    val blockRegisteredShortly: Boolean,
    val noFansButTooManyFollowingsWithDefaultProfilePicture: Int,
    val registrationMonths: Int?,
    val usernameKeywords: List<String>,
    val descriptionKeywords: List<String>,
    val minFollowers: Int,
    val maxFriends: Int,
    val friendsToFollowersRatioEnabled: Boolean,
    val friendsToFollowersRatioThreshold: Double
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
            blockNoProfilePicture = false,
            blockRegisteredShortly = false,
            noFansButTooManyFollowingsWithDefaultProfilePicture = 30,
            registrationMonths = null, // No limit
            usernameKeywords = emptyList(),
            descriptionKeywords = emptyList(),
            minFollowers = 10, // Default values
            maxFriends = 100,
            friendsToFollowersRatioEnabled = false,
            friendsToFollowersRatioThreshold = 20.0
        )
    }
}