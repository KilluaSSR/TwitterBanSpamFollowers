package dev.hylas.telegram

import com.github.scribejava.apis.TwitterApi
import com.github.scribejava.core.builder.ServiceBuilder
import twitter4j.Twitter

data class Config(
    var apiKey: String? = null,
    var apiSecret: String? = null,
    var token: String? = null,
    var tokenSecret: String? = null,
)

val config = Config(
    apiKey = "3rJOl1ODzm9yZy63FACdg",
    apiSecret = "5jPoQ5kQvMJFDYRNE8bQ4rHuds4xJqhvgNJM4awaE8",
    token = System.getenv("TWITTER_TOKEN").orEmpty(),
    tokenSecret = System.getenv("TWITTER_TOKEN_SECRET").orEmpty(),
)

val twitter by lazy {
    val srv = ServiceBuilder(config.apiKey)
        .apiSecret(config.apiSecret)
        .build(TwitterApi.instance())
    if (config.token.isNullOrBlank()) {
        val reqToken = srv.requestToken
        val authUrl = srv.getAuthorizationUrl(reqToken)
        println("open $authUrl in browser")
        print("your pin: ")
        val pin = readlnOrNull()
        val accessToken = srv.getAccessToken(reqToken, pin)
        config.token = accessToken.token
        config.tokenSecret = accessToken.tokenSecret
    }
    return@lazy initTwitter(config)!!
}

private fun initTwitter(config: Config): Twitter? {
    println(config)
    return try {
        Twitter.newBuilder()
            .oAuthAccessToken(config.token, config.tokenSecret)
            .oAuthConsumer(config.apiKey, config.apiSecret)
            .build()
    }
    catch (e: Exception) {
        println("Failed to initialize Twitter client: ${e.message}")
        null
    }
}
