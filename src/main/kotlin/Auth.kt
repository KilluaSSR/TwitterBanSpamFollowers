package killua.dev

import com.github.ajalt.clikt.core.CliktCommand
import com.github.scribejava.apis.TwitterApi
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth1AccessToken
import java.io.File
import java.io.PrintWriter

class AuthCommand : CliktCommand(
    name = "auth",
    help = "Get authorized!"
) {
    override fun run() {
        val service = createTwitterService(apiKey, apiSecret)
        val requestToken = service.requestToken
        val authorizationUrl = service.getAuthorizationUrl(requestToken)

        println("\nTo authorize the application, open the following URL in your web browser:")
        println("    $authorizationUrl")
        println("\nAfter authorizing the app, you will be provided with a PIN code.")
        println("Please enter that PIN code below to complete the authentication process:\n")

        val code = readLineWithPrompt("PIN: ")
        val accessToken = service.getAccessToken(requestToken, code)

        displayAccessTokenDetails(accessToken)
        saveAccessTokenToFile(accessToken)
        RunCommand()
    }
    private fun createTwitterService(apiKey: String, apiSecret: String) =
        ServiceBuilder(apiKey)
            .apiSecret(apiSecret)
            .build(TwitterApi.instance())

    private fun displayAccessTokenDetails( accessToken: com.github.scribejava.core.model.OAuth1AccessToken) {
        println("\nSUCCESS!\n")
        println("Access token: ${accessToken.token}")
        println("Access token secret: ${accessToken.tokenSecret}")
    }
    private fun saveAccessTokenToFile(accessToken: OAuth1AccessToken) {
        val file = File("twitter_credentials.properties")
        PrintWriter(file).use { writer ->
            writer.println("accessToken=${accessToken.token}")
            writer.println("accessTokenSecret=${accessToken.tokenSecret}")
        }
        println("Credentials saved to ${file.absolutePath}")
    }
}