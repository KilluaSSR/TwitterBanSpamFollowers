package killua.dev

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import twitter4j.v1.User
import twitter4j.v1.UsersResources
import java.io.FileReader
import java.time.LocalDateTime

data class BlockingConfig(
    val blockNoProfilePicture: Boolean,
    val blockRegistered: Boolean,
    val registrationMonths: Int?,
    val usernameKeywords: List<String>,
    val descriptionKeywords: List<String>,
    val minFollowers: Int,
    val maxFriends: Int,
    val friendsToFollowersRatioEnabled: Boolean,
    val friendsToFollowersRatioThreshold: Double
)
data class BlockCheckResult(val shouldBlock: Boolean, val matchingKeywords: List<String>)

private fun monthAgo(months: Int?): LocalDateTime {
    val monthsToSubtract = months ?: 3 // Default to 3 months if not specified
    return LocalDateTime.now().minusMonths(monthsToSubtract.toLong())
}

fun spam(user: User, config: BlockingConfig): Boolean {
    val isProfileImageDefault = user.profileImageURL.contains("default_profile_images")
    val isCreatedBefore = config.blockRegistered && user.createdAt.isBefore(monthAgo(config.registrationMonths))
    val hasFewFollowers = user.followersCount < config.minFollowers
    val hasManyFriends = user.friendsCount > config.maxFriends
    val friendsToFollowersRatioHigh = config.friendsToFollowersRatioEnabled && user.followersCount > 0 && user.friendsCount / user.followersCount > config.friendsToFollowersRatioThreshold

    return (isProfileImageDefault && isCreatedBefore && (hasFewFollowers && hasManyFriends || friendsToFollowersRatioHigh))
}

fun shouldBlock(user: User): BlockCheckResult {
    val matchingKeywords = mutableListOf<String>()

    if (blockingConfig.usernameKeywords.any { keyword ->
            user.screenName.contains(keyword, ignoreCase = true) ||
                    isKeywordPresent(user.screenName).also { if (it) matchingKeywords.add(keyword) }
        }) {
        return BlockCheckResult(true, matchingKeywords)
    }

    if (blockingConfig.descriptionKeywords.any { keyword ->
            user.description.contains(keyword, ignoreCase = true) ||
                    isKeywordPresent(user.description).also { if (it) matchingKeywords.add(keyword) }
        }) {
        return BlockCheckResult(true, matchingKeywords)
    }

    if (spam(user, blockingConfig)) {
        return BlockCheckResult(true, listOf("Spam criteria"))
    }

    return BlockCheckResult(false, emptyList())
}

fun blockUser(id: Long, user: UsersResources, dryRun:Boolean) {
    print("$id: blocking…")
    if (!dryRun) {
        try {
            user.createBlock(id)
            println(" Success! User $id has been blocked.")
        } catch (e: Throwable) {
            println(" Error: Failed to block user $id. Manual intervention may be needed.")
            throw e
        }
    } else {
        println("dry run")
    }
}

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
            blockRegistered = false,
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