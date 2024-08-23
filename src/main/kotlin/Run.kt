package killua.dev

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.v1.RateLimitStatus
import twitter4j.v1.UsersResources
import java.io.FileReader
import javax.net.ssl.SSLHandshakeException
import kotlin.time.Duration.Companion.seconds

var rateLimitStatus: RateLimitStatus = RateLimit.Unlimited
lateinit var blockingConfig: BlockingConfig
// ANSI escape code for red text
const val RED_TEXT = "\u001B[31m"
const val RESET_TEXT = "\u001B[0m"
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
    override fun run() = runBlocking {
        val (token, secret) = if (accessToken == null || accessSecret == null) {
            readCredentialsFromFile() ?: throw IllegalArgumentException("No valid credentials found. Run auth first.")
        } else {
            accessToken to accessSecret
        }

        val twitter = try {
            Twitter.newBuilder()
                .oAuthAccessToken(token, secret)
                .oAuthConsumer(apiKey, apiSecret)
                .build()
        } catch (e: Exception) {
            println("Failed to initialize Twitter client: ${e.message}")
            deleteCredentialsFile()
            return@runBlocking
        }

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

                for (id in cachedIds) {
                        try {
                            val user = users.showUser(id)
                            val result = shouldBlock(user)
                            if (result.shouldBlock) {
                                val keywords = result.matchingKeywords.joinToString(", ")
                                println("ID:${user.screenName}, Username:${user.name}, $id: ${RED_TEXT}matches criteria: $keywords${RESET_TEXT}")
                                usersToBlock[user.id] = arrayOf(user.name, user.screenName)
                                saveUsersToBlock(user.id, user.name, user.screenName)
                            } else {
                                println("ID:${user.screenName}, Username:${user.name}, $id: does not match criteria.")
                            }
                        } catch (e: SSLHandshakeException) {
                            println("SSL Handshake failed, retrying in 5 seconds...")
                            delay(5000)
                        } catch (e: TwitterException) {
                            handleTwitterException(e)
                            continue
                        }
                }

                cachedIds.removeAll(idsToRemove.toSet())
                refreshFileWithCachedIds(idsFile, cachedIds)
            }
        } catch (e: Exception) {
            println("Error during execution: ${e.message}")
            throw e
        }

        if (usersToBlock.isNotEmpty()) {
            println("\nUsers to be blocked:")
            usersToBlock.forEach { (id, details) ->
                val (screenName, name) = details
                println("Username: $name")
                println("User ID: $id")
                println("Profile URL: https://twitter.com/$screenName\n")
            }

            if (!dryRun) {
                val confirmation = readLineWithPrompt("Enter 'ok' to block these users or any other key to cancel: ")
                if (confirmation.trim().equals("ok", ignoreCase = true)) {
                    println("Blocking users...")
                    val idsToRemove = mutableListOf<Long>()
                    val iterator = usersToBlock.keys.iterator() // Iterate over the keys to safely modify the map during iteration
                    while (iterator.hasNext()) {
                        val id = iterator.next()
                        try {
                            delay(500)
                            blockUser(id, users,dryRun)
                            idsToRemove.add(id)
                        } catch (e: Exception) {
                            println("Error blocking user $id: ${e.message}")
                            if(e is TwitterException){
                                when (e.statusCode) {
                                    404 -> {
                                        println("User not found!")
                                        rateLimitStatus = e.rateLimitStatus ?: RateLimit.Unlimited
                                        continue
                                    }
                                    503 -> {
                                        println("Service unavailable!")
                                        rateLimitStatus = RateLimit.FiveMinutes
                                        continue
                                    }
                                }
                            }
                        }
                    }
                    idsToRemove.forEach { id ->
                        usersToBlock.remove(id)
                    }

                    refreshUsersToBlockFile(usersToBlock)

                    println("All specified users have been blocked.")
                } else {
                    println("Operation cancelled.")
                }
            }
        } else {
            println("No users match the criteria for blocking.")
        }
        println("\nAll done!")
        if (dryRun) {
            println("THIS IS A DRY RUN")
        }
    }

    private suspend fun RateLimitStatus.sleepIfNeeded(callCount: Int = 1) {
        if (remaining > callCount) return

        println()
        val totalSeconds = secondsUntilReset
        println("Rate limit exceeded. Pausing for $totalSeconds seconds to avoid further issues...")

        repeat(totalSeconds) { i ->
            val secondsRemaining = totalSeconds - i
            val message = "\rRate limit exceeded! Cooling off for $secondsRemaining seconds..."
            print(message.padEnd(50))
            delay(1.seconds)
        }

        delay(totalSeconds.seconds)
        print("\rRate limit cooldown complete. You can resume your actions.\n")
    }

}