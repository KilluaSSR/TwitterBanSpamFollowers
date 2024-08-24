package killua.dev

import com.fasterxml.jackson.annotation.JsonInclude.Include
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

fun shouldBlock(user: User, picture: Boolean, registerConverted: Int?, spamConverted: Int?, locked: Boolean, includeSite: Boolean, includeLocation: Boolean, ratioConverted: Double?): BlockCheckResult {
    val matchingKeywords = mutableListOf<String>()

    // 检查用户名是否包含任何配置的黑名单关键词
    val usernameMatches = checkKeywords(user.screenName, blockingConfig.userKeywords, matchingKeywords)
    if (usernameMatches.isNotEmpty()) {
        // 检查用户名是否包含任何配置的白名单关键词
        val excludeMatches = checkKeywords(user.screenName, blockingConfig.excludeKeywords, matchingKeywords)
        if (excludeMatches.isNotEmpty()) {
            return BlockCheckResult(false, usernameMatches.map { "Matched blacklist keyword: $it, but also matched whitelist keyword: ${excludeMatches.joinToString(", ")}. Not blocking." })
        }
        return BlockCheckResult(true, usernameMatches.map { "Username keyword match: $it" })
    }

    // 检查描述是否包含任何配置的黑名单关键词
    val descriptionMatches = checkKeywords(user.description, blockingConfig.userKeywords, matchingKeywords)
    if (descriptionMatches.isNotEmpty()) {
        // 检查描述是否包含任何配置的白名单关键词
        val excludeMatches = checkKeywords(user.description, blockingConfig.excludeKeywords, matchingKeywords)
        if (excludeMatches.isNotEmpty()) {
            return BlockCheckResult(false, descriptionMatches.map { "Matched blacklist keyword: $it, but also matched whitelist keyword: ${excludeMatches.joinToString(", ")}. Not blocking." })
        }
        return BlockCheckResult(true, descriptionMatches.map { "Description keyword match: $it" })
    }

    if (includeLocation) {
        // 检查位置是否包含任何配置的黑名单关键词
        val locationMatches = checkKeywords(user.location, blockingConfig.userKeywords, matchingKeywords)
        if (locationMatches.isNotEmpty()) {
            // 检查位置是否包含任何配置的白名单关键词
            val excludeMatches = checkKeywords(user.location, blockingConfig.excludeKeywords, matchingKeywords)
            if (excludeMatches.isNotEmpty()) {
                return BlockCheckResult(false, locationMatches.map { "Matched blacklist keyword: $it, but also matched whitelist keyword: ${excludeMatches.joinToString(", ")}. Not blocking." })
            }
            return BlockCheckResult(true, locationMatches.map { "Location keyword match: $it" })
        }
    }

    if (includeSite) {
        // 检查网址是否包含任何配置的黑名单关键词
        val urlMatches = checkKeywords(user.url, blockingConfig.userKeywords, matchingKeywords)
        if (urlMatches.isNotEmpty()) {
            // 检查网址是否包含任何配置的白名单关键词
            val excludeMatches = checkKeywords(user.url, blockingConfig.excludeKeywords, matchingKeywords)
            if (excludeMatches.isNotEmpty()) {
                return BlockCheckResult(false, urlMatches.map { "Matched blacklist keyword: $it, but also matched whitelist keyword: ${excludeMatches.joinToString(", ")}. Not blocking." })
            }
            return BlockCheckResult(true, urlMatches.map { "URL keyword match: $it" })
        }
    }

    val spamResult = checkSpamCriteria(user, picture, registerConverted, spamConverted, locked, ratioConverted)
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

private fun checkSpamCriteria(user: User,picture:Boolean,registerConverted: Int?,spamConverted: Int?,locked:Boolean,ratioConverted: Double?): BlockCheckResult {
    return spam(user,picture,registerConverted,spamConverted,locked,ratioConverted)
}

fun spam(user: User, picture: Boolean, registerConverted: Int?, spamConverted: Int?, locked: Boolean, ratioConverted: Double?): BlockCheckResult {
    val matchingKeywords = mutableListOf<String>()
    var shouldBlock = true

    if (!picture && registerConverted == null && spamConverted == null && !locked && ratioConverted == null) {
        shouldBlock = false
    } else {
        if (picture) {
            val isProfileImageDefault = user.profileImageURL.contains("default_profile_images")
            if (isProfileImageDefault) {
                matchingKeywords.add("Default profile image")
            } else {
                shouldBlock = false
            }
        }

        if (registerConverted != null) {
            val isCreatedBefore = user.createdAt.isAfter(monthAgo(registerConverted))
            if (isCreatedBefore) {
                matchingKeywords.add("Account created within $registerConverted months")
            } else {
                shouldBlock = false
            }
        }

        if (locked) {
            val isLocked = user.isProtected
            if (isLocked) {
                matchingKeywords.add("Account is protected")
            } else {
                shouldBlock = false
            }
        }

        if (spamConverted != null && picture) {
            val noFansButTooManyFollowing = user.followersCount == 0 && user.friendsCount > spamConverted && user.profileImageURL.contains("default_profile_images")
            if (noFansButTooManyFollowing) {
                matchingKeywords.add("0 Fans but too many following with default profile image")
            } else {
                shouldBlock = false
            }
        }

        if (ratioConverted != null) {
            val friendsToFollowersRatioHigh = user.followersCount > 0 && user.friendsCount / user.followersCount > ratioConverted
            if (friendsToFollowersRatioHigh) {
                matchingKeywords.add("High friends-to-followers ratio")
            } else {
                shouldBlock = false
            }
        }
    }

    return BlockCheckResult(shouldBlock, matchingKeywords)
}

suspend fun blocker(
    usersToBlock: MutableMap<Long, Array<String>>,
    users: UsersResources,
    dryRun: Boolean,
){
    if (usersToBlock.isNotEmpty()) {
        println("Users to block:")
        usersToBlock.forEach { (id, details) ->
            val (screenName, name) = details
            val profileUrl = "https://twitter.com/$name"
            println("ID: $id, Screen Name: $screenName, ID: $name, Profile URL: $profileUrl ")
        }
        println("${RED_TEXT} You should manually review each user pending block to prevent mistakes. ${RESET_TEXT}")
    } else {
        println("Nothing to block.")
        return
    }

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