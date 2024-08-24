package killua.dev

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import twitter4j.TwitterException
import twitter4j.v1.RateLimitStatus
import twitter4j.v1.User
import javax.net.ssl.SSLHandshakeException

var rateLimitStatus: RateLimitStatus = RateLimit.Unlimited
lateinit var blockingConfig: BlockingConfig
// ANSI escape code for red text
const val RED_TEXT = "\u001B[31m"
const val RESET_TEXT = "\u001B[0m"
const val MAX_RETRY = 5
class RunCommand : CliktCommand(
    name = "run",
    help = "Block them now!",
) {
    private val accessToken by option(metavar = "KEY")
        .help("OAuth access token")

    private val accessSecret by option(metavar = "KEY")
        .help("OAuth access token secret")

    private val dryRun by option()
        .flag()
        .help("Print destructive actions instead of performing them")

    private val picture by option()
        .flag()
        .help("Block users with default profile pictures")

    private val register by option(metavar = "INT")
        .help("Block users registered within the specified number of months")

    private val spam by option(metavar = "INT")
        .help("Block users with 0 fans but too many followings")

    private val locked by option()
        .flag()
        .help("Block users who have protected their tweets")

    private val includeSite by option()
        .flag()
        .help("Also scan the user's website link")

    private val includeLocation by option()
        .flag()
        .help("Also scan the user's location string")

    private val ratio by option(metavar = "INT")
        .help("Block users with a followings-to-followers ratio higher than the specified value")

    override fun run() = runBlocking {
        supervisorScope {
            try {
                val (token, secret) = if (accessToken == null || accessSecret == null) {
                    readCredentialsFromFile() ?: throw IllegalArgumentException("No valid credentials found. Run auth first.")
                } else {
                    accessToken to accessSecret
                }

                val ratioConverted = ratio?.toDoubleOrNull()
                if (ratio?.isNotEmpty() == true && ratioConverted == null) {
                    throw Exception("You need to input a number.")
                }

                val spamConverted = spam?.toIntOrNull()
                if (spam?.isNotEmpty() == true && spamConverted == null) {
                    throw Exception("You need to input a number.")
                }

                val registerConverted = register?.toIntOrNull()
                if (register?.isNotEmpty() == true && registerConverted == null) {
                    throw Exception("You need to input a number.")
                }

                val twitter = initializeTwitterClient(token, secret) ?: return@supervisorScope

                val twitterV1 = twitter.v1()
                val users = twitterV1.users()
                val friendsFollowers = twitterV1.friendsFollowers()

                if (dryRun) {
                    println("DRY RUN\n")
                }

                var cursor = -1L
                val usersToBlock = loadUserIdsToBlock().toMutableMap()
                loadBlockingConfig()

                try {
                    while (cursor != 0L) {
                        rateLimitStatus.sleepIfNeeded()
                        print("Fetching followers")
                        val ids = try {
                            friendsFollowers.getFollowersIDs(cursor)
                        } catch (e: TwitterException) {
                            handleTwitterException(e)
                            rateLimitStatus = e.rateLimitStatus ?: RateLimit.FiveMinutes
                            continue
                        }
                        cursor = ids.nextCursor
                        rateLimitStatus = ids.rateLimitStatus ?: RateLimit.Unlimited
                        println("Done. (count=${ids.iDs.size}, hasMore=${cursor != 0L})\n")
                        if (!idsFile.exists() || idsFile.length().toInt() == 0) {
                            saveIdsToFile(ids.iDs.toList())
                        }
                        rateLimitStatus.sleepIfNeeded(callCount = 2)
                        val cachedIds = loadIdsFromFile().toMutableSet()
                        val idsToRemove = mutableListOf<Long>()
                        var retryCount = 0

                        while (retryCount < MAX_RETRY) {
                            var success = true // 标记此次循环是否成功
                            for (id in cachedIds) {
                                var user: User? = null
                                try {
                                    user = users.showUser(id)
                                    delay(80)
                                } catch (e: TwitterException) {
                                    handleTwitterException(e)
                                    retryCount++
                                    success = false
                                    break
                                } catch (e: SSLHandshakeException) {
                                    println("SSL Handshake failed, retrying in 5 seconds...")
                                    delay(5000)
                                    retryCount++
                                    success = false
                                    break
                                } catch (e: Exception) {
                                    println("Unexpected error occurred, retrying in 10 seconds...")
                                    delay(10000)
                                    retryCount++
                                    success = false
                                    break
                                } finally {
                                    cachedIds.removeAll(idsToRemove.toSet())
                                    refreshFileWithCachedIds(idsFile, cachedIds)
                                }

                                if (user != null) {
                                    val result = shouldBlock(user, picture, registerConverted, spamConverted, locked, includeSite, includeLocation, ratioConverted)
                                    if (result.shouldBlock) {
                                        val keywords = result.matchingKeywords.joinToString(", ")
                                        println("ID:${user.screenName}, Username:${user.name}, $id: ${RED_TEXT}matches criteria: $keywords${RESET_TEXT}")
                                        usersToBlock[user.id] = arrayOf(user.name, user.screenName)
                                        saveUsersToBlock(user.id, user.name, user.screenName)
                                    } else {
                                        println("ID:${user.screenName}, Username:${user.name}, $id: does not match criteria.")
                                    }
                                }
                            }
                            if (success) {
                                break
                            }
                        }
                        if (retryCount >= MAX_RETRY) {
                            throw Exception("Maximum retry limit reached.")
                        }
                    }
                } catch (e: Exception) {
                    println("Error during execution: ${e.message}")
                    throw e
                }
                if (usersToBlock.isNotEmpty()) {
                    println("\nUsers to be blocked:")
                    usersToBlock.forEach { (id, details) ->
                        val (screenName, name) = details
                        println("Username: $screenName")
                        println("User ID: $id")
                        println("Profile URL: https://twitter.com/$name\n")
                    }
                    blocker(usersToBlock, users, dryRun)
                } else {
                    println("No users match the criteria for blocking.")
                }
                println("\nAll done!")
                if (dryRun) {
                    println("THIS IS A DRY RUN")
                }
            } catch (e: TwitterException) {
                handleTwitterException(e)
            } catch (e: SSLHandshakeException) {
                println("SSL Handshake failed, retrying in 5 seconds...")
                delay(5000)
            } catch (e: Exception) {
                println("Unexpected error occurred, retrying in 10 seconds...")
                delay(10000)
            }
        }
    }
}