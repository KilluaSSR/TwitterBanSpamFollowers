package killua.dev

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.*
import twitter4j.TwitterException
import twitter4j.v1.RateLimitStatus
import twitter4j.v1.UsersResources
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

    override fun run(): Unit = runBlocking {
        val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            println("Caught $exception")
        }

        supervisorScope {
            try {
                val (token, secret) = if (accessToken == null || accessSecret == null) {
                    readCredentialsFromFile()
                        ?: throw IllegalArgumentException("No valid credentials found. Run auth first.")
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

                launch(exceptionHandler) {
                    val twitter = initializeTwitterClient(token, secret) ?: return@launch
                    val twitterV1 = twitter.v1()
                    val users = twitterV1.users()
                    val friendsFollowers = twitterV1.friendsFollowers()

                    if (dryRun) {
                        println("DRY RUN\n")
                    }

                    var cursor = -1L
                    val usersToBlock = loadUserIdsToBlock().toMutableMap()
                    loadBlockingConfig()

                    while (cursor != 0L) {
                        rateLimitStatus.sleepIfNeeded()
                        print("Fetching followers")

                        val ids = withRetry {
                            friendsFollowers.getFollowersIDs(cursor)
                        } ?: break // Exit loop if failed after retries

                        cursor = ids.nextCursor
                        rateLimitStatus = ids.rateLimitStatus ?: RateLimit.Unlimited
                        println("Done. (count=${ids.iDs.size}, hasMore=${cursor != 0L})\n")

                        if (!idsFile.exists() || idsFile.length().toInt() == 0) {
                            saveIdsToFile(ids.iDs.toList())
                        }

                        rateLimitStatus.sleepIfNeeded(callCount = 2)
                        val cachedIds = loadIdsFromFile().toMutableSet()

                        val idsToRemove = mutableListOf<Long>()
                        processCachedIds(
                            cachedIds,
                            idsToRemove,
                            users,
                            registerConverted,
                            spamConverted,
                            ratioConverted,
                            usersToBlock
                        )
                        cachedIds.removeAll(idsToRemove.toSet())
                        refreshFileWithCachedIds(idsFile, cachedIds)
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

    // Retry logic for Twitter API calls
    private suspend fun <T> withRetry(
        maxRetries: Int = 3,
        initialDelay: Long = 1000L,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T? {
        var currentDelay = initialDelay
        repeat(maxRetries) {
            try {
                return block()
            } catch (e: TwitterException) {
                handleTwitterException(e)
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            } catch (e: SSLHandshakeException) {
                println("SSL Handshake failed, retrying in 5 seconds...")
                delay(5000)
            } catch (e: Exception) {
                println("Unexpected error occurred, retrying in 10 seconds...")
                delay(10000)
            }
        }
        return null
    }

    private suspend fun processCachedIds(
        cachedIds: MutableSet<Long>,
        idsToRemove: MutableList<Long>,
        users: UsersResources,
        registerConverted: Int?,
        spamConverted: Int?,
        ratioConverted: Double?,
        usersToBlock: MutableMap<Long, Array<String>>
    ) {
        for (id in cachedIds) {
            try {
                val user = withRetry {
                    users.showUser(id)
                } ?: continue
                delay(80)
                val result = shouldBlock(
                    user,
                    picture,
                    registerConverted,
                    spamConverted,
                    locked,
                    includeSite,
                    includeLocation,
                    ratioConverted
                )
                if (result.shouldBlock) {
                    val keywords = result.matchingKeywords.joinToString(", ")
                    println("ID:${user.screenName}, Username:${user.name}, $id: ${RED_TEXT}matches criteria: $keywords${RESET_TEXT}")
                    usersToBlock[user.id] = arrayOf(user.name, user.screenName)
                    idsToRemove.add(id)
                    saveUsersToBlock(user.id, user.name, user.screenName)
                } else {
                    println("ID:${user.screenName}, Username:${user.name}, $id: does not match criteria.")
                }
            } catch (e: TwitterException) {
                handleTwitterException(e)
                continue
            } catch (e: Exception) {
                delay(10000)
            }
        }
    }
}