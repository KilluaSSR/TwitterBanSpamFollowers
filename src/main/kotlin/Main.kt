package killua.dev.com.killuadev.twitterbanspamfollowers

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.scribejava.apis.TwitterApi
import com.github.scribejava.core.builder.ServiceBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.v1.RateLimitStatus
import twitter4j.v1.User
import twitter4j.v1.UsersResources
import java.util.*
import kotlin.time.Duration.Companion.seconds

fun main(vararg args: String) {
    NoOpCliktCommand(name = "BanSpamFollowers")
        .apply {
            subcommands(AuthCommand(), RunCommand())
        }
        .main(args)
}


private class AuthCommand : CliktCommand(
    name = "auth",
    help = "Perform interactive authentication to get an access token and secret"
) {
    private val apiKey = "3rJOl1ODzm9yZy63FACdg"
    private val apiSecret = "5jPoQ5kQvMJFDYRNE8bQ4rHuds4xJqhvgNJM4awaE8"

    override fun run() {
        val service = createTwitterService(apiKey, apiSecret)
        val requestToken = service.requestToken
        val authorizationUrl = service.getAuthorizationUrl(requestToken)

        println("\nVisit the following URL in your browser to authorize the app:")
        println("    $authorizationUrl")
        println("\nOnce completed, you should see a PIN. Paste that below:\n")

        val code = readLineWithPrompt("PIN: ")
        val accessToken = service.getAccessToken(requestToken, code)

        displayAccessTokenDetails(apiKey, apiSecret, accessToken)
    }

    private fun createTwitterService(apiKey: String, apiSecret: String) =
        ServiceBuilder(apiKey)
            .apiSecret(apiSecret)
            .build(TwitterApi.instance())

    private fun readLineWithPrompt(prompt: String): String {
        print(prompt)
        return Scanner(System.`in`).nextLine()
    }

    private fun displayAccessTokenDetails(apiKey: String, apiSecret: String, accessToken: com.github.scribejava.core.model.OAuth1AccessToken) {
        println("\nSUCCESS!\n")
        println("Consumer API key: $apiKey")
        println("Consumer API secret: $apiSecret")
        println("Access token: ${accessToken.token}")
        println("Access token secret: ${accessToken.tokenSecret}")
    }
}



private class RunCommand : CliktCommand(
    name = "run",
    help = "Block and quickly unblock all followers to keep count at zero",
) {
    private val accessToken by option(metavar = "KEY")
        .required()
        .help("OAuth access token")
    private val accessSecret by option(metavar = "KEY")
        .required()
        .help("OAuth access token secret")
    private val apiKey by option(metavar = "KEY")
        .required()
        .help("OAuth consumer API key")
    private val apiSecret by option(metavar = "KEY")
        .required()
        .help("OAuth consumer API secret")
    private val dryRun by option()
        .flag()
        .help("Print destructive actions instead of performing them")

    override fun run() = runBlocking {
        val twitter = Twitter.newBuilder()
            .oAuthAccessToken(accessToken, accessSecret)
            .oAuthConsumer(apiKey, apiSecret)
            .build()
        val twitterV1 = twitter.v1()

        val users = twitterV1.users()
        val friendsFollowers = twitterV1.friendsFollowers()

        if (dryRun) {
            println("DRY RUN\n")
        }

        var rateLimitStatus: RateLimitStatus = RateLimit.Unlimited
        var cursor = -1L
        while (cursor != 0L) {
            rateLimitStatus.sleepIfNeeded()

            print("Fetching followers…")
            val ids = try {
                friendsFollowers.getFollowersIDs(cursor)
            } catch (e: TwitterException) {
                if (e.exceededRateLimitation()) {
                    println(" failed.")
                    rateLimitStatus = e.rateLimitStatus ?: RateLimit.FiveMinutes
                    continue
                } else if (e.statusCode == 503) {
                    println(" service unavailable!")
                    rateLimitStatus = RateLimit.FiveMinutes
                    continue
                } else {
                    throw e
                }
            }
            cursor = ids.nextCursor
            rateLimitStatus = ids.rateLimitStatus ?: RateLimit.Unlimited
            println(" done. (count=${ids.iDs.size}, hasMore=${cursor != 0L})\n")

            rateLimitStatus.sleepIfNeeded(callCount = 2)

            for (id in ids.iDs) {
                try {
                    val user = users.showUser(id)
                    if (shouldBlock(user)) {
                        blockUser(id, users)
                    } else {
                        println("$id: does not match criteria.")
                    }
                } catch (e:Throwable){
                    if(e is TwitterException){
                        when(e.statusCode){
                            404->{
                                println(" user not found!")
                                rateLimitStatus = e.rateLimitStatus ?: RateLimit.Unlimited
                                continue
                            }
                            503->{
                                println(" service unavailable!")
                                rateLimitStatus = RateLimit.FiveMinutes
                                continue
                            }
                        }
                    }
                    println("Failed!")
                    throw e
                }
            }
        }
        println("\nAll done!")
        if (dryRun) {
            println("!!! DRY RUN !!!")
        }
    }
    private fun shouldBlock(user: User): Boolean {
        // Implement your filtering logic here
        // Example: Block users with specific keywords in their description
        val keywords = setOf("exampleKeyword1", "exampleKeyword2")
        return keywords.any { keyword -> user.description.contains(keyword, ignoreCase = true) }
    }

    private fun blockUser(id: Long, user: UsersResources) {
        print("$id: blocking…")
        if (!dryRun) {
            try {
                user.createBlock(id)
                println(" done.")
            } catch (e: Throwable) {
                println(" failed! MANUAL INTERVENTION NEEDED!!")
                throw e
            }
        } else {
            println(" (dry run)")
        }
    }

    private suspend fun RateLimitStatus.sleepIfNeeded(callCount: Int = 1) {
        if (remaining > callCount) return

        println()
        val totalSeconds = secondsUntilReset
        repeat(totalSeconds) { i ->
            val message = "\rRate limited! Cooling off ${totalSeconds - i} seconds…"
            print(message.padEnd(30)) // Ensure that old messages are cleared
            delay(1.seconds)
        }
        delay(totalSeconds.seconds) // Final wait to ensure complete cooldown
        print("\rRate limited! Cooling off… done\n")
    }

}