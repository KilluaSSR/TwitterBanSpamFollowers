package killua.dev

import kotlinx.coroutines.delay
import twitter4j.TwitterException
import twitter4j.v1.User
import twitter4j.v1.UsersResources
import java.io.IOException
import java.time.LocalDateTime
import javax.net.ssl.SSLHandshakeException


fun monthAgo(months: Int?): LocalDateTime {
    val monthsToSubtract = months ?: 3 // Default to 3 months if not specified
    return LocalDateTime.now().minusMonths(monthsToSubtract.toLong())
}

fun shouldBlock(user: User): BlockCheckResult {
    val matchingKeywords = mutableListOf<String>()

    // Check if internal keywords are present in username
    val internalMatches = isKeywordPresent(user.screenName)
    if (internalMatches.isNotEmpty()) {
        return BlockCheckResult(true, internalMatches.map { "Internal keyword match: $it" })
    }

    // Check if username contains any of the configured keywords
    val usernameMatches = checkKeywords(user.screenName, blockingConfig.usernameKeywords, matchingKeywords)
    if (usernameMatches.isNotEmpty()) {
        return BlockCheckResult(true, usernameMatches.map { "Username keyword match: $it" })
    }

    // Check if description contains any of the configured keywords
    val descriptionMatches = checkKeywords(user.description, blockingConfig.descriptionKeywords, matchingKeywords)
    if (descriptionMatches.isNotEmpty()) {
        return BlockCheckResult(true, descriptionMatches.map { "Description keyword match: $it" })
    }

    val spamResult = checkSpamCriteria(user)
    if (spamResult.shouldBlock) {
        return BlockCheckResult(true, spamResult.matchingKeywords)
    }

    return BlockCheckResult(false, emptyList())
}

private fun checkKeywords(
    text: String,
    keywords: List<String>,
    matchingKeywords: MutableList<String>
): List<String> {
    return keywords.filter { keyword ->
        text.contains(keyword, ignoreCase = true).also { if (it) matchingKeywords.add(keyword) }
    }
}

private fun checkSpamCriteria(user: User): BlockCheckResult {
    return spam(user, blockingConfig)
}

fun spam(user: User, config: BlockingConfig): BlockCheckResult {
    val matchingKeywords = mutableListOf<String>()

    val isProfileImageDefault = user.profileImageURL.contains("default_profile_images")
    if (isProfileImageDefault) {
        matchingKeywords.add("Default profile image")
    }

    val isCreatedBefore = config.blockRegistered && user.createdAt.isAfter(monthAgo(config.registrationMonths))
    if (isCreatedBefore) {
        matchingKeywords.add("Account created within ${monthAgo(config.registrationMonths)} months")
    }

    val hasFewFollowers = user.followersCount < config.minFollowers
    if (hasFewFollowers) {
        matchingKeywords.add("Few followers")
    }

    val hasManyFriends = user.friendsCount > config.maxFriends
    if (hasManyFriends) {
        matchingKeywords.add("Many friends")
    }

    val friendsToFollowersRatioHigh = config.friendsToFollowersRatioEnabled &&
            user.followersCount > 0 &&
            user.friendsCount / user.followersCount > config.friendsToFollowersRatioThreshold
    if (friendsToFollowersRatioHigh) {
        matchingKeywords.add("High friends-to-followers ratio")
    }

    val shouldBlock = (isProfileImageDefault || isCreatedBefore) &&
            (hasFewFollowers || hasManyFriends || friendsToFollowersRatioHigh)

    return BlockCheckResult(shouldBlock, matchingKeywords)
}

suspend fun blocker(
    usersToBlock: MutableMap<Long, Array<String>>,
    users: UsersResources,
    dryRun: Boolean,
){
    if (!dryRun && getConfirmation("Enter 'ok' to block these users or any other key to cancel: ")) {
        println("Blocking users...")
        processUserBlocking(
            usersToBlock,
            users,
            dryRun,
            ::handleTwitterException
        )
        refreshUsersToBlockFile(usersToBlock)
        println("All specified users have been blocked.")
    } else {
        println("Operation cancelled.")
    }
}

suspend fun processUserBlocking(
    usersToBlock: MutableMap<Long, Array<String>>,
    users: UsersResources,
    dryRun: Boolean,
    handleRateLimitStatus: (TwitterException) -> Unit
) {
    val idsToRemove = mutableListOf<Long>()

    val iterator = usersToBlock.keys.iterator()

    while (iterator.hasNext()) {
        val id = iterator.next()
        if(!dryRun){
            try {
                delay(500)
                users.createBlock(id)
                idsToRemove.add(id)
            } catch (e: SSLHandshakeException) {
                println("SSL握手失败: ${e.message}")
                delay(5000)
            } catch (e: IOException) {
                println("IO异常: ${e.message}")
                delay(5000)
            } catch (e: TwitterException) {
                handleRateLimitStatus(e)
            }  catch (e: Exception) {
                println("Error blocking user $id: ${e.message}")
            }
        }else{
            println(id)
        }

    }
    if(!dryRun){
        idsToRemove.forEach { id ->
            usersToBlock.remove(id)
        }
    }

}

fun getConfirmation(prompt: String): Boolean {
    val confirmation = readLineWithPrompt(prompt)
    return confirmation.trim().equals("ok", ignoreCase = true)
}