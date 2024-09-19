package killua.dev

import kotlinx.coroutines.delay

suspend fun <T> withRetry(
    maxRetries: Int = MAX_RETRY,
    initialDelay: Long = 1000L,
    maxDelay: Long = 10000L,
    factor: Double = 2.0,
    block: suspend () -> T
): T? {
    var currentDelay = initialDelay
    repeat(maxRetries - 1) {
        try {
            return block()
        } catch (e: Exception) {
            println("Attempt ${it + 1} failed: ${e.message}. Retrying in $currentDelay ms...")
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }
    return try {
        block()
    } catch (e: Exception) {
        println("Final attempt failed: ${e.message}. No more retries.")
        null
    }
}