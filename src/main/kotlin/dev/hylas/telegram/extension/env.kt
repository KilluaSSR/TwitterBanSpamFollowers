package dev.hylas.telegram.extension

fun env(name: String) =
    System.getenv(name) ?: throw RuntimeException("Fail to fetch environment variable [$name]")

inline fun <reified T> T?.use(block: (T) -> Unit) {
    if (this != null) {
        block(this)
    }
}
