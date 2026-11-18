package com.seeall.agent.camera

import org.junit.Assert.assertTrue
import org.junit.Test

class SmokeTest {
    @Test
    fun soapEnvelopeIsWellFormed() {
        val xml = soapEnvelope("<tds:GetDeviceInformation/>")
        assertTrue(xml.contains("s:Envelope"))
        assertTrue(xml.contains("s:Body"))
        assertTrue(xml.contains("GetDeviceInformation"))
    }

    @Test
    fun xmlEscapeHandlesSpecialCharacters() {
        assertTrue(escapeXml("a&b<c>\"d'") == "a&amp;b&lt;c&gt;&quot;d&apos;")
    }
}
