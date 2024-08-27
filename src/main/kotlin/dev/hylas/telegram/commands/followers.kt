package dev.hylas.telegram.commands

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.FindFlow
import dev.hylas.telegram.extension.diff
import dev.hylas.telegram.extension.params
import dev.hylas.telegram.extension.use
import dev.hylas.telegram.mongoDB
import dev.hylas.telegram.twitter
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommonHandler
import eu.vendeli.tgbot.api.message.SendMessageAction
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.LinkPreviewOptions
import eu.vendeli.tgbot.types.ParseMode
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.internal.MessageUpdate
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId
import java.time.LocalDateTime

/**
 * diff followers
 *
 * @author <a href="x.com/aka_hylas">Hylas</a>
 */
@CommonHandler.Regex("^/difffo.*\$")
suspend fun difffo(update: MessageUpdate, bot: TelegramBot, user: User) {
    val params = update.message.params()
    check(params).use { return it.send(user, bot) }
    val screenName = params.first().lowercase()
    val collection = mongoDB.getCollection<FollowerSnapshot>("follower_snapshots")
    try {
        val snaps = collection
            .find(Filters.eq("twitterScreenName", screenName))
            .sort(Sorts.descending("createdTime"))
            .limit(6)

        val results = diffUserResults(snaps).filter { (it.diff?.count() ?: 0) != 0 } // 过滤粉丝没有变动的
        val allUserIds = results.map { it.diff.allResults() }.flatten().toLongArray()

        val userMap = twitter.v1().users().lookupUsers(*allUserIds).associateBy { it.id }
        val diffUsers = results.map {
            UserDiffResult<twitter4j.v1.User>().apply {
                this.diff = DiffResult(
                    added = it.diff?.added?.mapNotNull { userMap[it] }.orEmpty(),
                    changed = it.diff?.changed?.mapNotNull { userMap[it] }.orEmpty(),
                    deleted = it.diff?.deleted?.mapNotNull { userMap[it] }.orEmpty(),
                )
                this.currentSnap = it.currentSnap
            }
        }

        val returnedText = diffUsers.joinToString("\n========\n") { it.tgDescription() } //! 如果文本过长，消息无法返回
        message { returnedText }
            .options {
                parseMode = ParseMode.HTML
                linkPreviewOptions = LinkPreviewOptions(isDisabled = true)
            }
            .send(user, bot)
    }
    catch (e: Exception) {
        e.printStackTrace()
        message { "No data." }.send(user, bot)
    }
}


/**
 * @author <a href="x.com/aka_hylas">Hylas</a>
 */
@CommonHandler.Regex("^/snapfo.*$")
suspend fun snap(update: MessageUpdate, bot: TelegramBot, user: User) {
    val params = update.message.params()
    check(params).use { return it.send(user, bot) }

    val screenName = params.first().lowercase()
    val twitter = twitter.v1()
    val screenUser =
        twitter.users().lookupUsers(screenName).firstOrNull() ?: return message { "No user found." }.send(user, bot)
    val followerIds = twitter.friendsFollowers().getFollowersIDs(screenName, -1)
    val followerSnapshot = FollowerSnapshot(
        ObjectId(),
        screenUser.id,
        screenUser.screenName.lowercase(),
        screenUser.name.lowercase(),
        followerIds.iDs.toList(),
        LocalDateTime.now()
    )
    val result = mongoDB.getCollection<FollowerSnapshot>("follower_snapshots").insertOne(followerSnapshot)
    message {
        """
        There are ${followerIds.iDs.size} users following @$screenName.
        ${
            if (result.wasAcknowledged()) {
                "They have been saved to database successfully!"
            }
            else {
                "But saving them failed."
            }
        }
    """.trimIndent()
    }.send(user, bot)
}

private suspend fun diffUserResults(snaps: FindFlow<FollowerSnapshot>): List<UserDiffResult<Long>> {
    var last: Set<Long> = HashSet()
    var current: Set<Long>
    val diffResults = snaps.toList().reversed().map { snap ->
        current = snap.followerIds?.toSet().orEmpty()
        val diff = last.diff(current) { it }
        val r = UserDiffResult<Long>().apply {
            this.diff = diff
            this.currentSnap = snap
        }
        last = current
        r
    }.drop(1).toList()
    return diffResults.reversed()
}

private fun check(params: List<String>): SendMessageAction? {
    if (params.isEmpty()) {
        return message { "Please send me a username." }
    }
    if (params.count() != 1) {
        return message { "ONLY ONE username can be accepted." }
    }
    return null
}
