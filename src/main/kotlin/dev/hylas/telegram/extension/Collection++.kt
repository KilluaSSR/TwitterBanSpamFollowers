package dev.hylas.telegram.extension

import dev.hylas.telegram.commands.DiffResult

private fun <T> checkEmptyAndReturn(a: Collection<T>, b: Collection<T>): DiffResult<T>? {
    if (a.isEmpty() && b.isEmpty()) {
        return DiffResult()
    }

    if (a.isEmpty()) {
        return DiffResult(b)
    }

    if (b.isEmpty()) {
        return DiffResult(deleted = a)
    }
    return null
}

fun <E, K> Collection<E>.diff(
    other: Collection<E>,
    keyExtractor: (E) -> K,
): DiffResult<E> where E : Comparable<E>, K : Any {
    val checkResult = checkEmptyAndReturn(this, other)
    if (checkResult != null) {
        return checkResult
    }

    val map = associateBy { keyExtractor(it) }.toMutableMap()
    val added = ArrayList<E>()
    val changed = ArrayList<E>()
    val deleted = ArrayList<E>()

    // 找出新增的和需要更新的
    for (b in other) {
        val key = keyExtractor(b)
        val a = map[key]
        if (a == null) {
            added.add(b)
        }
        else {
            map.remove(key)
            if (a.compareTo(b) != 0) {
                changed.add(b)
            }
        }
    }

    // 剩余的就是需要删除的
    map.entries.forEach { deleted.add(it.value) }

    return DiffResult(added, changed, deleted)
}
