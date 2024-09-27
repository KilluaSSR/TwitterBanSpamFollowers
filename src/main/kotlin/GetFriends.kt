package killua.dev

import twitter4j.v1.TwitterV1

enum class RelationshipType {
    Followers,
    Following,
    Blocked
}
suspend fun getUserIDs(
    twitterV1: TwitterV1,
    relationshipType: RelationshipType,
    userID: Long? = null
): MutableSet<Long> {
    val idsSet = mutableSetOf<Long>()
    var cursor = -1L
    val friendsFollowers = twitterV1.friendsFollowers()
    while (cursor != 0L) {
        val ids = withRetry {
            when (relationshipType) {
                RelationshipType.Followers -> {
                    if (userID != null) {
                        println("Getting his/her followers.")
                        friendsFollowers.getFollowersIDs(userID, cursor)
                    } else {
                        println("Getting my followers.")
                        friendsFollowers.getFollowersIDs(cursor)
                    }
                }
                RelationshipType.Following -> {
                    if (userID != null) {
                        println("Getting his/her followings.")
                        friendsFollowers.getFriendsIDs(userID, cursor)
                    } else {
                        println("Getting my followings.")
                        friendsFollowers.getFriendsIDs(cursor)
                    }
                }
                RelationshipType.Blocked -> {
                    println("Getting my blocked ids.")
                    twitterV1.users().getBlocksIDs(cursor)
                }
            }
        } ?: break
        cursor = ids.nextCursor
        rateLimitStatus = ids.rateLimitStatus ?: RateLimit.Unlimited
        println("Done. (count=${ids.iDs.size}, hasMore=${cursor != 0L})\n")
        idsSet.addAll(ids.iDs.toSet())
    }
    return idsSet
}

suspend fun getMyFollowings(twitterV1: TwitterV1): MutableSet<Long> =
    getUserIDs(twitterV1, RelationshipType.Following)

suspend fun getMyFollowers(twitterV1: TwitterV1): MutableSet<Long> =
    getUserIDs(twitterV1, RelationshipType.Followers)

suspend fun getOthersFollowers(twitterV1: TwitterV1, userID: Long): MutableSet<Long> =
    getUserIDs(twitterV1, RelationshipType.Followers, userID)

suspend fun getOthersFollowings(twitterV1: TwitterV1, userID: Long): MutableSet<Long> =
    getUserIDs(twitterV1, RelationshipType.Following, userID)

suspend fun getMyBlocked(twitterV1: TwitterV1):MutableSet<Long> =
    getUserIDs(twitterV1, RelationshipType.Blocked)