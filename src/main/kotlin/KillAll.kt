package killua.dev

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.coroutines.*
import twitter4j.TwitterException
import java.lang.Thread.sleep

class KillAll : CliktCommand(
    name = "killall",
    help = "Kill his/her followers and followings!"
) {
    private val accessToken by option(metavar = "KEY").help("OAuth access token")
    private val accessSecret by option(metavar = "KEY").help("OAuth access token secret")
    private val sinner by option(help = "Whom shall you judge among the sinners? Tell my his/her username AFTER @").required()
    override fun run(): Unit = runBlocking {
        if (sinner.isBlank()) {
            throw CliktError("you need to specify who you are going to kill.")
        }
        val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            println("Caught $exception")
        }
        val (token, secret) = if (accessToken == null || accessSecret == null) {
            readCredentialsFromFile()
                ?: throw IllegalArgumentException("No valid credentials found. Run auth first.")
        } else {
            accessToken to accessSecret
        }
        supervisorScope {
            launch(exceptionHandler) {
                try {
                    val twitter = initializeTwitterClient(token, secret) ?: return@launch
                    val twitterV1 = twitter.v1()
                    val users = twitterV1.users()
                    val sinUserID = twitter.v1().users().showUser(sinner).id
                    println("His/her is ${users.showUser(sinUserID).name}, right? Type OK to confirm.")
                    val input = readlnOrNull()?.trim()

                    if (!input.equals("ok", ignoreCase = true)) {
                        throw CliktError("Bye!")
                    }
                    val followings = getMyFollowings(twitterV1)
                    val followers = getMyFollowers(twitterV1)
                    val protected = followers + followings
                    val sinFollowings = getOthersFollowings(twitterV1,sinUserID)
                    //val sinFollowers = getOthersFollowers(twitterV1,sinUserID)
                    val myBlock = getMyBlocked(twitterV1)
                    val toBlock = sinFollowings - protected - myBlock
                    println("${toBlock.size} sinners are waiting to die, waiting to live. Waiting for an absolution, that would never come.")
                    var i = 1
                    for (id in toBlock) {
                        if(i%50 == 0) sleep(8000)
                        users.createBlock(id)
                        println("Blocking $id")
                        i++
                    }
                }catch (e: TwitterException) {
                    handleTwitterException(e)
                }
            }
        }
    }
}