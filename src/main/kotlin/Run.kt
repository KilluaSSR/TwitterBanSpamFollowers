package killua.dev

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.*
import twitter4j.TwitterException
import twitter4j.v1.RateLimitStatus
import twitter4j.v1.UsersResources
import withRetry
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

    private val delay by option(metavar = "INT")
        .help(
            "Delay between fetching two users must be specified in milliseconds. A high delay can significantly extend the processing time, " +
                    "but it will make the process more stable. Note that 1 second equals 1000 milliseconds. " +
                    "The default value is 100 milliseconds, and it must be greater than 80 milliseconds."
        )
    private val ratio by option(metavar = "INT")
        .help("Block users with a followings-to-followers ratio higher than the specified value")

    override fun run(): Unit = runBlocking {
        val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            println("Caught $exception")
        }
        val cachedIds = loadIdsFromFile().toMutableSet()
        val idsToRemove = mutableListOf<Long>()
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

                val delayConverted = delay?.toLongOrNull()
                if (delay?.isNotEmpty() == true && delayConverted == null) {
                    throw Exception("You need to input a number.")
                }
                if (delayConverted != null && delayConverted < 80) {
                    throw Exception("Delay must be grater than 80.")
                }

                val delayTime = delayConverted ?: 100


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

                        saveIdsToFile(ids.iDs.toList())

                        rateLimitStatus.sleepIfNeeded(callCount = 2)



                        processCachedIds(
                            cachedIds,
                            idsToRemove,
                            users,
                            registerConverted,
                            spamConverted,
                            ratioConverted,
                            usersToBlock,
                            delayTime
                        )
                    }

                    if (usersToBlock.isNotEmpty()) {
                        if (cursor != 0L) {
                            println("Warning: The previous steps did not complete successfully. Proceeding to block users... Wait 10 seconds.")
                        }
                        println("\nUsers to be blocked:")
                        if (cursor != 0L) {
                            println("Wait 10 seconds.")
                        }
                        delay(10000)
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
                when(e){
//                    e.rateLimitStatus ->{
//                        //refresh(cachedIds,idsToRemove)
//                        throw Exception("Run it later!")
//                    }
                    else->{
                        handleTwitterException(e)
                    }
                }

            } catch (e: SSLHandshakeException) {
                println("SSL Handshake failed, retrying in 5 seconds...")
                delay(5000)
            } catch (e: Exception) {
                println("Unexpected error occurred, retrying in 10 seconds...")
                delay(10000)
            }
        }
    }


    private suspend fun processCachedIds(
        cachedIds: MutableSet<Long>,
        idsToRemove: MutableList<Long>,
        users: UsersResources,
        registerConverted: Int?,
        spamConverted: Int?,
        ratioConverted: Double?,
        usersToBlock: MutableMap<Long, Array<String>>,
        delayTime: Long
    ) {
        for (id in cachedIds) {
            try {
                val user = withRetry {
                    users.showUser(id)
                } ?: continue // 如果重试后仍然失败，继续处理下一个ID
                delay(delayTime)
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
                    saveUsersToBlock(user.id, user.name, user.screenName,result)
                } else {
                    println("ID:${user.screenName}, Username:${user.name}, $id: does not match criteria.")
                }
                idsToRemove.add(id)
            } catch (e: Exception) {
                // 如果遇到错误，不退出，继续处理下一个ID
                delay(10000)
                continue
            }
        }
        //refresh(cachedIds, idsToRemove)
    }

//    private fun refresh(cachedIds: MutableSet<Long>, idsToRemove: MutableList<Long>) {
//        cachedIds.removeAll(idsToRemove.toSet())
//        refreshFileWithCachedIds(idsFile, cachedIds)
//    }
}