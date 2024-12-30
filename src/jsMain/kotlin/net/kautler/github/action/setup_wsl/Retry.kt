package net.kautler.github.action.setup_wsl

import actions.core.debug
import actions.core.info

internal suspend inline fun <T> retry(amount: Int, crossinline block: suspend () -> T): T {
    (1..amount).map { i ->
        runCatching {
            return block()
        }.onFailure {
            if (i != 5) {
                debug(it.stackTraceToString())
                info("Failure happened, retrying (${it.message ?: it})")
            }
        }
    }.last().getOrThrow<Nothing>()
}
