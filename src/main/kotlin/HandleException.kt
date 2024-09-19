package killua.dev

import kotlinx.coroutines.delay
import twitter4j.TwitterException

suspend fun handleTwitterException(e: TwitterException) {
    when {
        e.exceededRateLimitation() -> {
            rateLimitStatus = RateLimit.FiveMinutes
            println("${RED_TEXT}Failed: Exceeded Rate Limitation.${RESET_TEXT}")

        }

        e.statusCode == 503 -> {
            println("${RED_TEXT}Failed: Service Unavailable, wait 10 seconds${RESET_TEXT}")
            delay(1000)
        }

        e.statusCode == 401 -> {
            println("${RED_TEXT}Invalid or expired token. Ensure that you have set valid consumer key/secret, access token/secret, and the system clock is in sync. Please try auth command.${RESET_TEXT}")
            throw e
        }

        e.statusCode == -1 -> {
            println("${RED_TEXT}Service error, wait 30 seconds.${RESET_TEXT}")
            delay(30 * 1000)
        }

        e.statusCode == 403 -> {
            println("${RED_TEXT}Request refused, wait 5 seconds.${RESET_TEXT}")
            delay(5 * 1000)
        }

        else -> {
            println(e)
        }
    }
}