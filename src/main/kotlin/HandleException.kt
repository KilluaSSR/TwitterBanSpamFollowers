package killua.dev

import twitter4j.TwitterException

fun handleTwitterException(e: TwitterException) {
    if (e.exceededRateLimitation()) {
        println("Failed: Exceeded Rate Limitation.")
    } else if (e.statusCode == 503) {
        println("Failed: Service Unavailable.")
        rateLimitStatus = RateLimit.FiveMinutes
    } else {
        throw e
    }
}