package killua.dev

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.runBlocking


class Execute : CliktCommand(
    name = "execute",
    help = "Block the rest!"
) {
    private val accessToken by option(metavar = "KEY").help("OAuth access token")
    private val accessSecret by option(metavar = "KEY").help("OAuth access token secret")
    private val dryRun by option().flag().help("Print destructive actions instead of performing them")

    override fun run() = runBlocking {
        val usersToBlock = loadUserIdsToBlock().toMutableMap()
        if(usersToBlock.isEmpty()) throw Exception("Nothing to block. ")
        val (token, secret) = if (accessToken == null || accessSecret == null) {
            readCredentialsFromFile() ?: throw IllegalArgumentException("No valid credentials found. Run auth first.")
        } else {
            accessToken to accessSecret
        }

        val twitter = initializeTwitterClient(token, secret) ?: return@runBlocking

        val twitterV1 = twitter.v1()
        val users = twitterV1.users()

        blocker(usersToBlock,users,dryRun)

    }
}
