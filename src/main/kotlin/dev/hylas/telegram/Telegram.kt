package dev.hylas.telegram

import dev.hylas.telegram.extension.env
import eu.vendeli.tgbot.TelegramBot

private val telegramBot by lazy {
    TelegramBot(env("TELEGRAM_BOT_TOKEN"))
}

fun tgBot(): TelegramBot {
    telegramBot
    twitter
    mongoDB
    return telegramBot
}
