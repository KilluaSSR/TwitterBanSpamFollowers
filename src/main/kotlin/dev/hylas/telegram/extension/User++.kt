package dev.hylas.telegram.extension

import twitter4j.v1.User

fun User.tgDescription() =
    "<a href='twitter.com/$screenName'><b>$name</b></a>"
