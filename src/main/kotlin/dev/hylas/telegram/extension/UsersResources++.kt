package dev.hylas.telegram.extension

import twitter4j.v1.ResponseList
import twitter4j.v1.User
import twitter4j.v1.UsersResources
import java.util.concurrent.Executors
import kotlin.math.min

fun UsersResources.lookupUsersWithoutLimit(vararg ids: Long): List<User> {
    val users = mutableListOf<ResponseList<User>>()
    val tasks = ids.paginate(100).map {
        Runnable {
            users.add(lookupUsers(*it))
        }
    }
    Executors.newVirtualThreadPerTaskExecutor().apply {
        tasks.map { submit(it) }.forEach { it.get() } // start and wait all tasks
    }
    return users.flatten()
}

private fun LongArray.paginate(pageSize: Int): List<LongArray> {
    if (pageSize <= 0) {
        return emptyList()
    }
    var i = 0
    val pages = ArrayList<LongArray>()
    while (i < size) {
        val element = copyOfRange(i, min(i + pageSize, size))
        pages.add(element)
        i += pageSize
    }
    return pages
}
