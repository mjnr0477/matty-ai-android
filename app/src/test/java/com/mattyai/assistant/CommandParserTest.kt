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
    @Test fun unknownCommandsDoNotCreateArbitraryActions() {
        assertEquals(MattyCommandType.UNKNOWN, CommandParser.parse("transfer all my money").type)
    }
}
