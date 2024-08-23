package killua.dev

import kotlinx.coroutines.delay
import twitter4j.v1.RateLimitStatus
import kotlin.time.Duration.Companion.seconds

data class RateLimit(
    private val remaining: Int,
    private val secondsUntilReset: Int,
) : RateLimitStatus {
    override fun getRemaining() = remaining
    override fun getLimit() = throw AssertionError()
    override fun getResetTimeInSeconds() = throw AssertionError()
    override fun getSecondsUntilReset() = secondsUntilReset

    companion object {
        val Unlimited = RateLimit(remaining = Int.MAX_VALUE, secondsUntilReset = 0)
        val FiveMinutes = RateLimit(remaining = 0, secondsUntilReset = 5 * 60)
    }
}

suspend fun RateLimitStatus.sleepIfNeeded(callCount: Int = 1) {
    if (remaining > callCount) return

    println()
    val totalSeconds = secondsUntilReset
    println("Rate limit exceeded. Pausing for $totalSeconds seconds to avoid further issues...")

    repeat(totalSeconds) { i ->
        val secondsRemaining = totalSeconds - i
        val message = "\rRate limit exceeded! Cooling off for $secondsRemaining seconds..."
        print(message.padEnd(50))
        delay(1.seconds)
    }

    delay(totalSeconds.seconds)
    print("\rRate limit cooldown complete. You can resume your actions.\n")
}