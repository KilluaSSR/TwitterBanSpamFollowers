package dev.hylas.telegram.extension

import eu.vendeli.tgbot.types.msg.Message

fun Message.params() =
    text?.split("\\s+".toRegex()).orEmpty().drop(1)
