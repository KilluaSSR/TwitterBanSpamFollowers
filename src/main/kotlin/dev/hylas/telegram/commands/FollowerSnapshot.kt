package dev.hylas.telegram.commands

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.LocalDateTime

data class FollowerSnapshot(
    @BsonId
    val id: ObjectId? = null,
    var twitterUserId: Long? = null,
    var twitterScreenName: String? = null,
    var twitterName: String? = null,
    var followerIds: List<Long>? = emptyList(),
    var createdTime: LocalDateTime? = null,
)
