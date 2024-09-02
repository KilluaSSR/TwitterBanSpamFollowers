package dev.hylas.telegram

import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommandHandler
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User

suspend fun main() {
    tgBot()
        .handleUpdates()
}

@CommandHandler(["/start"])
suspend fun start(bot: TelegramBot, user: User) {
    message { "Welcome, ${user.firstName}." }.send(user, bot)
}
