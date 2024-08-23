package killua.dev

import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import java.io.File
import java.io.InputStream
import java.util.*

const val apiKey = "3rJOl1ODzm9yZy63FACdg"
const val apiSecret = "5jPoQ5kQvMJFDYRNE8bQ4rHuds4xJqhvgNJM4awaE8"
lateinit var internalKeywords: Set<String>

fun main(vararg args: String) {
    // 初始化时加载资源文件
    initialize("sensitive_word_dict.txt")
    NoOpCliktCommand(name = "<FileName.jar>")
        .apply {
            subcommands(AuthCommand(), RunCommand())
        }
        .main(args)
}

fun readLineWithPrompt(prompt: String): String {
    print(prompt)
    return Scanner(System.`in`).nextLine()
}

fun initialize(keywordsFileName: String) {
    internalKeywords = loadKeywords(keywordsFileName)
}

private fun loadKeywords(fileName: String): Set<String> {
    val inputStream: InputStream? = object {}.javaClass.classLoader.getResourceAsStream(fileName)
        ?: throw IllegalArgumentException("Resource not found: $fileName")
    return inputStream?.bufferedReader().use { it?.readLines()?.toSet()!! }
}

fun isKeywordPresent(text: String): Boolean {
    if (!::internalKeywords.isInitialized) {
        throw IllegalStateException("Keywords have not been initialized")
    }
    return internalKeywords.any { text.contains(it, ignoreCase = true) }
}
