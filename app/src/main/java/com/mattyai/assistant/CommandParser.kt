package com.mattyai.assistant

/** Pure local parser: no network calls and no model-generated actions. */
enum class MattyCommandType {
    OPEN_SYSTEM_SETTINGS, OPEN_WIFI_SETTINGS, OPEN_MOBILE_DATA_SETTINGS, OPEN_BLUETOOTH_SETTINGS,
    OPEN_NETWORK_SETTINGS, OPEN_NOTIFICATIONS, OPEN_QUICK_SETTINGS,
    GO_BACK, GO_HOME, VOLUME_DOWN, VOLUME_UP, START_PLAYBACK,
    OPEN_MUSIC, OPEN_APP, INSTALL_APP, SEARCH_WEB, OPEN_APP_AND_CLICK_LINK, TAP_TEXT, LONG_PRESS_TEXT, CLICK_FIRST_LINK, TYPE_TEXT, SCROLL_UP, SCROLL_DOWN, UNKNOWN
}

data class ParsedMattyCommand(val type: MattyCommandType, val argument: String = "")
data class WakePhraseMatch(val detected: Boolean, val command: String)

object CommandParser {
    fun extractWakeCommand(raw: String): WakePhraseMatch {
        val match = Regex("(?i)\\bmatty\\b").find(raw)
            ?: return WakePhraseMatch(false, "")
        val command = raw.substring(match.range.last + 1)
            .trim()
            .trimStart(',', ':', ';', '-', ' ')
            .trim()
        return WakePhraseMatch(true, command)
    }

    fun parse(raw: String): ParsedMattyCommand {
        val original = raw.trim().replace(Regex("(?i)^(hey\\s+)?matty[ ,]*"), "").trim()
        val command = original.lowercase()
        return when {
            command.startsWith("don't ") || command.startsWith("do not ") || command.startsWith("never ") ->
                ParsedMattyCommand(MattyCommandType.UNKNOWN)
            command.contains("link") && (command.contains("click") || command.contains("open") || command.contains("follow")) &&
                command.startsWith("open ") -> {
                val appStart = "open ".length
                val separator = Regex("(?i)\\b(?:at this time|and then|then|and|there's|there is|click|follow|open)\\b")
                    .find(command, appStart)
                val appName = (separator?.let { command.substring(appStart, it.range.first) }
                    ?: command.substring(appStart)).trim()
                if (appName.isNotEmpty() && !appName.matches(Regex("(?i)(the|this|that)?\\s*link"))) {
                    ParsedMattyCommand(MattyCommandType.OPEN_APP_AND_CLICK_LINK, appName)
                } else {
                    ParsedMattyCommand(MattyCommandType.CLICK_FIRST_LINK)
                }
            }
            command.contains("link") && (command.contains("click") || command.contains("open") || command.contains("follow")) ->
                ParsedMattyCommand(MattyCommandType.CLICK_FIRST_LINK)
            command.startsWith("install ") || command.startsWith("download and install ") -> {
                val appName = original.replace(Regex("(?i)^(download and install|install)\\s+"), "").trim()
                if (appName.isNotBlank()) ParsedMattyCommand(MattyCommandType.INSTALL_APP, appName)
                else ParsedMattyCommand(MattyCommandType.UNKNOWN)
            }
            command.startsWith("search for ") || command.startsWith("search the web for ") ||
                command.startsWith("google ") || command.startsWith("look up ") -> {
                val query = original.replace(Regex("(?i)^(search the web for|search for|google|look up)\\s+"), "").trim()
                if (query.isNotBlank()) ParsedMattyCommand(MattyCommandType.SEARCH_WEB, query)
                else ParsedMattyCommand(MattyCommandType.UNKNOWN)
            }
            command == "like" || command == "like this" || command == "unlike" ||
                command == "comment" || command == "share" || command == "share this" ||
                command == "post" || command == "update status" || command == "open status" ->
                ParsedMattyCommand(
                    MattyCommandType.TAP_TEXT,
                    when (command) {
                        "like this" -> "Like"
                        "share this" -> "Share"
                        "update status", "open status" -> "Status"
                        else -> original.replaceFirstChar { it.uppercase() }
                    }
                )
            command.startsWith("long press ") || command.startsWith("press and hold ") -> ParsedMattyCommand(
                MattyCommandType.LONG_PRESS_TEXT,
                original.replace(Regex("(?i)^(long press|press and hold)\\s+(the\\s+)?"), "").trim()
            )
            command.startsWith("tap ") || command.startsWith("click ") ->
                ParsedMattyCommand(
                    MattyCommandType.TAP_TEXT,
                    original.replace(Regex("(?i)^(tap|click)\\s+(on\\s+)?(the\\s+)?"), "")
                        .replace(Regex("(?i)\\s+button$"), "").trim()
                )
            command.startsWith("enter text ") -> ParsedMattyCommand(MattyCommandType.TYPE_TEXT, original.replace(Regex("(?i)^enter text\\s+"), "").trim())
            command.startsWith("type ") -> ParsedMattyCommand(MattyCommandType.TYPE_TEXT, original.replace(Regex("(?i)^type\\s+"), "").trim())
            command.contains("scroll down") || command == "swipe up" -> ParsedMattyCommand(MattyCommandType.SCROLL_DOWN)
            command.contains("scroll up") || command == "swipe down" -> ParsedMattyCommand(MattyCommandType.SCROLL_UP)
            command == "settings" || command == "open settings" || command == "phone settings" || command == "system settings" -> ParsedMattyCommand(MattyCommandType.OPEN_SYSTEM_SETTINGS)
            command.contains("wifi") || command.contains("wi-fi") -> ParsedMattyCommand(MattyCommandType.OPEN_WIFI_SETTINGS)
            command.contains("mobile data") || command.contains("cellular data") || command == "data" -> ParsedMattyCommand(MattyCommandType.OPEN_MOBILE_DATA_SETTINGS)
            command.contains("bluetooth") -> ParsedMattyCommand(MattyCommandType.OPEN_BLUETOOTH_SETTINGS)
            command.contains("internet settings") || command.contains("network settings") -> ParsedMattyCommand(MattyCommandType.OPEN_NETWORK_SETTINGS)
            command.contains("notification") -> ParsedMattyCommand(MattyCommandType.OPEN_NOTIFICATIONS)
            command.contains("quick settings") || command.contains("control center") -> ParsedMattyCommand(MattyCommandType.OPEN_QUICK_SETTINGS)
            command.contains("go back") || command == "back" -> ParsedMattyCommand(MattyCommandType.GO_BACK)
            command.contains("go home") || command == "home" -> ParsedMattyCommand(MattyCommandType.GO_HOME)
            command.contains("volume down") || command.contains("turn down volume") || command.contains("lower volume") -> ParsedMattyCommand(MattyCommandType.VOLUME_DOWN)
            command.contains("volume up") || command.contains("turn up volume") || command.contains("raise volume") -> ParsedMattyCommand(MattyCommandType.VOLUME_UP)
            command.contains("start this song") || command == "start song" -> ParsedMattyCommand(MattyCommandType.START_PLAYBACK)
            command.contains("play music") || command.contains("start music") || command.startsWith("play ") || command.contains("open music") -> ParsedMattyCommand(MattyCommandType.OPEN_MUSIC)
            command.startsWith("open ") || command.startsWith("launch ") || command.startsWith("start ") ->
                ParsedMattyCommand(MattyCommandType.OPEN_APP, command.substringAfter(' ').trim())
            else -> ParsedMattyCommand(MattyCommandType.UNKNOWN)
        }
    }
}
