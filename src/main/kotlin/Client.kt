package killua.dev

import twitter4j.Twitter

fun initializeTwitterClient(token: String?, secret: String?): Twitter? {
    return try {
        Twitter.newBuilder()
            .oAuthAccessToken(token, secret)
            .oAuthConsumer(apiKey, apiSecret)
            .build()
    } catch (e: Exception) {
        println("Failed to initialize Twitter client: ${e.message}")
        deleteCredentialsFile()
        null
    }
}