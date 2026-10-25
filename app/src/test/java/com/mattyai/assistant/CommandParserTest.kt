package com.mattyai.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandParserTest {
    @Test fun stripsWakeNameAndOpensWifiSettings() {
        assertEquals(MattyCommandType.OPEN_WIFI_SETTINGS, CommandParser.parse("Matty, open Wi-Fi").type)
    }
    @Test fun opensMobileDataSettings() {
        assertEquals(MattyCommandType.OPEN_MOBILE_DATA_SETTINGS, CommandParser.parse("Matty open mobile data").type)
    }
    @Test fun sendsPlayToActiveMedia() {
        assertEquals(MattyCommandType.START_PLAYBACK, CommandParser.parse("Matty start this song").type)
    }
    @Test fun parsesInstalledAppName() {
        assertEquals(ParsedMattyCommand(MattyCommandType.OPEN_APP, "whatsapp"), CommandParser.parse("open WhatsApp"))
    }
    @Test fun volumeAndNavigationAreLocalCommands() {
        assertEquals(MattyCommandType.VOLUME_DOWN, CommandParser.parse("turn volume down").type)
        assertEquals(MattyCommandType.GO_HOME, CommandParser.parse("Matty go home").type)
    }
    @Test fun parsesTapByVisibleLabel() {
        assertEquals(ParsedMattyCommand(MattyCommandType.TAP_TEXT, "Wi-Fi"), CommandParser.parse("Matty tap Wi-Fi"))
        assertEquals(ParsedMattyCommand(MattyCommandType.TAP_TEXT, "Send"), CommandParser.parse("click Send"))
        assertEquals(ParsedMattyCommand(MattyCommandType.TAP_TEXT, "Send"), CommandParser.parse("Matty tap the Send button"))
    }
    @Test fun parsesGenericLinkClick() {
        assertEquals(MattyCommandType.CLICK_FIRST_LINK, CommandParser.parse("Matty click the link").type)
        assertEquals(MattyCommandType.CLICK_FIRST_LINK, CommandParser.parse("open this link").type)
        assertEquals(MattyCommandType.CLICK_FIRST_LINK, CommandParser.parse("open the link").type)
        assertEquals(
            MattyCommandType.CLICK_FIRST_LINK,
            CommandParser.parse("there's a link there, click to open the link").type
        )
    }
    @Test fun parsesInstallAppRequests() {
        assertEquals(
            ParsedMattyCommand(MattyCommandType.INSTALL_APP, "WhatsApp"),
            CommandParser.parse("Matty install WhatsApp")
        )
        assertEquals(
            ParsedMattyCommand(MattyCommandType.INSTALL_APP, "Signal"),
            CommandParser.parse("download and install Signal")
        )
    }
    @Test fun parsesWebSearchRequests() {
        assertEquals(
            ParsedMattyCommand(MattyCommandType.SEARCH_WEB, "weather in Bungoma"),
            CommandParser.parse("Matty search for weather in Bungoma")
        )
        assertEquals(
            ParsedMattyCommand(MattyCommandType.SEARCH_WEB, "nearby pharmacies"),
            CommandParser.parse("look up nearby pharmacies")
        )
    }
    @Test fun parsesCompoundOpenAppThenClickLink() {
        assertEquals(
            ParsedMattyCommand(MattyCommandType.OPEN_APP_AND_CLICK_LINK, "whatsapp"),
            CommandParser.parse("open WhatsApp at this time there's a link there click to open the link")
        )
        assertEquals(
            ParsedMattyCommand(MattyCommandType.OPEN_APP_AND_CLICK_LINK, "whatsapp"),
            CommandParser.parse("open WhatsApp and click the link")
        )
    }
    @Test fun parsesTextEntryLocally() {
        assertEquals(ParsedMattyCommand(MattyCommandType.TYPE_TEXT, "Hello There!"), CommandParser.parse("Matty type Hello There!"))
        assertEquals(ParsedMattyCommand(MattyCommandType.TYPE_TEXT, "good morning"), CommandParser.parse("enter text good morning"))
    }
    @Test fun parsesScrollingCommands() {
        assertEquals(MattyCommandType.SCROLL_DOWN, CommandParser.parse("scroll down").type)
        assertEquals(MattyCommandType.SCROLL_UP, CommandParser.parse("Matty scroll up").type)
    }
    @Test fun unknownCommandsDoNotCreateArbitraryActions() {
        assertEquals(MattyCommandType.UNKNOWN, CommandParser.parse("transfer all my money").type)
        assertEquals(MattyCommandType.UNKNOWN, CommandParser.parse("Matty do not open Wi-Fi").type)
    }
}
