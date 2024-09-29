package killua.dev

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.coroutines.*
import twitter4j.TwitterException

class KillAll : CliktCommand(
    name = "killall",
    help = "Kill his/her followers and/or followings!"
) {
    private val accessToken by option(metavar = "KEY").help("OAuth access token")
    private val accessSecret by option(metavar = "KEY").help("OAuth access token secret")
    private val sinner by option(help = "Whom shall you judge among the sinners? Tell my his/her username AFTER @").required()
    private val following by option(help = "followings").flag()
    private val follower by option(help = "followers").flag()
    override fun run(): Unit = runBlocking {
        if (sinner.isBlank()) {
            throw CliktError("You need to specify who you are going to kill.")
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
                    val myFollowings = if(followingsIdsFile.exists() && followingsIdsFile.length() > 0) {
                        loadFollowingsIdsFromFile()} else getMyFollowings(twitterV1)
                    val myFollowers = if(followersIdsFile.exists() && followersIdsFile.length() > 0) {
                        loadFollowersIdsFromFile() } else getMyFollowers(twitterV1)
                    val protected = myFollowers + myFollowings
                    var sinFollowings: Set<Long>
                    var sinFollowers: Set<Long>
                    val myBlock = getMyBlocked(twitterV1)
                    var toBlock = emptySet<Long>()
                    if((following && follower) || (!following && !follower)) {
                        sinFollowings = getOthersFollowings(twitterV1,sinUserID)
                        sinFollowers = getOthersFollowers(twitterV1,sinUserID)
                        toBlock = sinFollowings + sinFollowers - protected - myBlock
                    }else if(!following && follower) {
                        sinFollowers = getOthersFollowers(twitterV1,sinUserID)
                        toBlock = sinFollowers - protected - myBlock
                    }else if(following && !follower) {
                        sinFollowings = getOthersFollowings(twitterV1,sinUserID)
                        toBlock = sinFollowings - protected - myBlock
                    }
                    saveFollowersIdsToFile(myFollowers.toList())
                    saveFollowingsIdsToFile(myFollowings.toList())
                    println("${toBlock.size} sinners are waiting to die, waiting to live. Waiting for an absolution, that would never come.")
                    var i = 1
                    for (id in toBlock) {
                        if(i%30 == 0) delay(10000)
                        users.createBlock(id)
                        val progress = i.toLong() / toBlock.size.toLong().toDouble() * 100
                        val formattedProgress = String.format("%.2f", progress)
                        println("$i in ${toBlock.size}, Progress: ${formattedProgress}%, Blocking: $id")
                        i++
                    }
                }catch (e: TwitterException) {
                    handleTwitterException(e)
                }
            }
        }
    }
}
