package killua.dev

import com.fasterxml.jackson.annotation.JsonInclude.Include
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import twitter4j.TwitterException
import twitter4j.v1.User
import twitter4j.v1.UsersResources
import withRetry
import java.io.IOException
import java.time.LocalDateTime
import javax.net.ssl.SSLHandshakeException

fun monthAgo(months: Int?): LocalDateTime {
    val monthsToSubtract = months ?: 3 // Default to 3 months if not specified
    return LocalDateTime.now().minusMonths(monthsToSubtract.toLong())
}

fun shouldBlock(
    user: User,
    picture: Boolean,
    registerConverted: Int?,
    spamConverted: Int?,
    locked: Boolean,
    includeSite: Boolean,
    includeLocation: Boolean,
    ratioConverted: Double?
): BlockCheckResult {
    val matchingKeywords = mutableListOf<String>()
    val reasonList = mutableListOf<String>()
    val screennameResult = checkField(user.screenName, "Screenname", matchingKeywords, reasonList)
    if (screennameResult.shouldBlock) {
        println("\nHis/Her Screenname:" +user.screenName)
        screennameResult.reasonList += listOf("Screenname: ${user.screenName}")
        return screennameResult
    }

    val usernameResult = checkField(user.name, "Username", matchingKeywords, reasonList)
    if (usernameResult.shouldBlock) {
        println("\nHis/Her Username:" +user.screenName)
        usernameResult.reasonList += listOf("Username: $user.username")
        return usernameResult
    }

    val descriptionResult = checkField(user.description, "Description", matchingKeywords, reasonList)
    if (descriptionResult.shouldBlock) {
        println("\nHis/Her Description:" +user.description)
        descriptionResult.reasonList += listOf("Description: ${user.description}")
        return descriptionResult
    }

    if (includeLocation) {
        val locationResult = checkField(user.location, "Location", matchingKeywords, reasonList)
        if (locationResult.shouldBlock) {
            println("\nHis/Her Location:" +user.location)
            locationResult.reasonList += listOf("Location: ${user.location}")
            return locationResult
        }
    }

    if (includeSite) {
        val urlResult = checkField(user.url, "URL", matchingKeywords, reasonList)
        if (urlResult.shouldBlock) {
            println("\nHis/Her Site:" +user.url)
            urlResult.reasonList += listOf("Site: ${user.url}")
            return urlResult
        }
    }

    val spamResult = checkSpamCriteria(user, picture, registerConverted, spamConverted, locked, ratioConverted)
    if (spamResult.shouldBlock) {
        return BlockCheckResult(true, spamResult.matchingKeywords, listOf("Spam"))
    }

    return BlockCheckResult(false, emptyList(), emptyList())
}

fun checkField(field: String?, fieldName: String, matchingKeywords: MutableList<String>, reasonList: MutableList<String>): BlockCheckResult {
    if (field == null) return BlockCheckResult(false, emptyList(), emptyList())

    val matches = checkKeywords(field, blockingConfig.userKeywords, matchingKeywords) + isKeywordPresent(field)
    if (matches.isNotEmpty()) {
        val excludeMatches = checkKeywords(field, blockingConfig.excludeKeywords, matchingKeywords)
        if (excludeMatches.isNotEmpty()) {
            return BlockCheckResult(
                false,
                matches.map {
                    "Matched blacklist keyword: $it, but also matched whitelist keyword: ${
                        excludeMatches.joinToString(", ")
                    }. Not blocking."
                },
                emptyList())
        }
        return BlockCheckResult(true, matches.map { "$fieldName keyword match: $it" }, emptyList())
    }
    return BlockCheckResult(false, emptyList(), emptyList())
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

private fun checkSpamCriteria(
    user: User,
    picture: Boolean,
    registerConverted: Int?,
    spamConverted: Int?,
    locked: Boolean,
    ratioConverted: Double?
): BlockCheckResult {
    return spam(user, picture, registerConverted, spamConverted, locked, ratioConverted)
}

fun spam(
    user: User,
    picture: Boolean,
    registerConverted: Int?,
    spamConverted: Int?,
    locked: Boolean,
    ratioConverted: Double?
): BlockCheckResult {
    val matchingKeywords = mutableListOf<String>()
    var shouldBlock = true

    if (!picture && registerConverted == null && spamConverted == null && !locked && ratioConverted == null) {
        shouldBlock = false
    } else {
        shouldBlock = checkProfileImage(user, picture, matchingKeywords) &&
                checkAccountCreation(user, registerConverted, matchingKeywords) &&
                checkAccountProtection(user, locked, matchingKeywords) &&
                checkSpamFollowing(user, spamConverted, matchingKeywords) &&
                checkFriendsToFollowersRatio(user, ratioConverted, matchingKeywords)
    }

    return BlockCheckResult(shouldBlock, matchingKeywords, listOf("Spam"))
}
fun checkProfileImage(user: User, picture: Boolean, matchingKeywords: MutableList<String>): Boolean {
    if (picture) {
        val isProfileImageDefault = user.profileImageURL.contains("default_profile_images")
        if (isProfileImageDefault) {
            matchingKeywords.add("Default profile image")
            return true
        }
    }
    return false
}


fun checkAccountCreation(user: User, registerConverted: Int?, matchingKeywords: MutableList<String>): Boolean {
    if (registerConverted != null) {
        val isCreatedBefore = user.createdAt.isAfter(monthAgo(registerConverted))
        if (isCreatedBefore) {
            matchingKeywords.add("Account created within $registerConverted months")
            return true
        }
    }
    return false
}

fun checkAccountProtection(user: User, locked: Boolean, matchingKeywords: MutableList<String>): Boolean {
    if (locked) {
        val isLocked = user.isProtected
        if (isLocked) {
            matchingKeywords.add("Account is protected")
            return true
        }
    }
    return false
}
fun checkFriendsToFollowersRatio(user: User, ratioConverted: Double?, matchingKeywords: MutableList<String>): Boolean {
    if (ratioConverted != null) {
        val friendsToFollowersRatioHigh = user.followersCount > 0 && user.friendsCount / user.followersCount > ratioConverted
        if (friendsToFollowersRatioHigh) {
            matchingKeywords.add("High friends-to-followers ratio")
            return true
        }
    }
    return false
}
fun checkSpamFollowing(user: User, spamConverted: Int?, matchingKeywords: MutableList<String>): Boolean {
    if (spamConverted != null) {
        val noFansButTooManyFollowing = user.followersCount == 0 && user.friendsCount > spamConverted
        if (noFansButTooManyFollowing) {
            matchingKeywords.add("0 Fans but too many following with default profile image")
            return true
        }
    }
    return false
}

suspend fun blocker(
    usersToBlock: MutableMap<Long, Array<String>>,
    users: UsersResources,
    dryRun: Boolean,
) {
    if (usersToBlock.isNotEmpty()) {
        println("Users to block:")
        usersToBlock.forEach { (id, details) ->
            val (screenName, name, reason) = details
            val profileUrl = "https://twitter.com/$screenName"
            println("ID: $id, Screen Name: $screenName, Name: $name, Profile URL: $profileUrl , Reason: $reason")
        }
        println("${RED_TEXT}You should manually review each user pending block to prevent mistakes. ${RESET_TEXT}")
    } else {
        println("Nothing to block.")
        return
    }

    while (true) {
        println("Enter the IDs of the users you don't want to block (separated by commas), or type 'ok' to start blocking:")
        val input = readlnOrNull()?.trim()

        if (input.equals("ok", ignoreCase = true)) {
            break
        }

        input?.split(",")?.map { it.trim().toLongOrNull() }?.forEach { idToExclude ->
            if (idToExclude != null && usersToBlock.containsKey(idToExclude)) {
                usersToBlock.remove(idToExclude)
                println("User with ID $idToExclude has been excluded from blocking.\n\n")
            } else if (idToExclude != null) {
                println("User ID $idToExclude not found in the block list.\n\n")
            } else {
                println("Invalid input detected in the list. Please ensure all entries are valid user IDs.\n\n")
            }
        }

        refreshUsersToBlockFile(usersToBlock)

        usersToBlock.forEach { (id, details) ->
            val (screenName, name) = details
            println("Username: $screenName")
            println("User ID: $id")
            println("Profile URL: https://twitter.com/$name\n")
        }
    }

    // 显示更新后的列表并开始block
    if (usersToBlock.isEmpty()) {
        println("No users left to block after exclusions.")
        return
    }

    if (!dryRun) {
        println("Blocking users...")
        processUserBlocking(
            usersToBlock,
            users,
            dryRun
        )
        refreshUsersToBlockFile(usersToBlock)
        println("All specified users have been blocked.")
    } else {
        println("Dry run completed. No users were blocked.")
    }
}

suspend fun processUserBlocking(
    usersToBlock: MutableMap<Long, Array<String>>,
    users: UsersResources,
    dryRun: Boolean
) {
    val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        println("Caught $exception")
    }
    supervisorScope {
        try {
            launch(exceptionHandler) {
                val idsToRemove = mutableListOf<Long>()
                val iterator = usersToBlock.keys.iterator()
                while (iterator.hasNext()) {
                    val id = iterator.next()
                    if (!dryRun) {
                        try {
                            users.showUser(id)
                        } catch (e: TwitterException) {
                            println(e)
                            continue
                        }
                        withRetry {
                            delay(90)
                            rateLimitStatus.sleepIfNeeded()
                            users.createBlock(id)
                            println("ID blocking now: $id")
                            idsToRemove.add(id)
                        } ?: {
                            removeIds(idsToRemove, usersToBlock)
                            throw Exception("Exceeded max retries.")
                        }
                    }
                }
                if (!dryRun) {
                    removeIds(idsToRemove, usersToBlock)
                }
            }
        } catch (e: Exception) {
            // Handle exceptions from the supervisor scope
            println("Supervisor scope error: ${e.message}")
        }
    }
}

private fun removeIds(
    idsToRemove: MutableList<Long>,
    usersToBlock: MutableMap<Long, Array<String>>
) {
    idsToRemove.forEach { id ->
        usersToBlock.remove(id)
    }
}