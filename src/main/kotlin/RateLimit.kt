package killua.dev

import twitter4j.v1.RateLimitStatus
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
