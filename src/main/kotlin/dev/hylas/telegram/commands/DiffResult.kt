package dev.hylas.telegram.commands

import dev.hylas.telegram.extension.tgDescription
import twitter4j.v1.User

/**
 * @author <a href="x.com/aka_hylas">Hylas</a>
 * @date 2024/08/26 00:48
 */
data class DiffResult<T>(
    var added: Collection<T>? = null,
    var changed: Collection<T>? = null,
    var deleted: Collection<T>? = null,
) {
    fun count() = (added?.count() ?: 0) + (changed?.count() ?: 0) + (deleted?.count() ?: 0)
}

fun <T> DiffResult<T>?.allResults(): Collection<T> {
    this ?: return emptySet()
    return HashSet<T>().apply {
        addAll(added.orEmpty())
        addAll(changed.orEmpty())
        addAll(deleted.orEmpty())
    }
}

class UserDiffResult<ID> {
    var currentSnap: FollowerSnapshot? = null
    var diff: DiffResult<ID>? = null
}

fun UserDiffResult<User>.tgDescription() =
    """
     |Time: ${currentSnap?.createdTime ?: "#UNKNOWN#"}
     |New Followers: [${diff?.added?.joinToString(", ") { it.tgDescription() }}]
     |Unfollowers: [${diff?.deleted?.joinToString(", ") { it.tgDescription() } ?: "None"}]
     |Total: ${currentSnap?.followerIds?.size ?: "#UNKNOWN#"}
    """.trimMargin()
