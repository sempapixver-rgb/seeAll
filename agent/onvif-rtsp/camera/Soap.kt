package com.seeall.agent.camera

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

private val XML_FACTORY = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = true
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    setFeature("http://xml.org/sax/features/external-general-entities", false)
    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
}

internal fun parseXml(xml: String): Element =
    XML_FACTORY.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))).documentElement

internal fun Element.childText(name: String): String? =
    getElementsByTagName(name).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

internal fun Element.descendants(localName: String): List<Element> {
    val out = mutableListOf<Element>()
    fun walk(node: Node) {
        if (node is Element && node.localName == localName) out += node
        val children = node.childNodes ?: return
        for (i in 0 until children.length) walk(children.item(i))
    }
    walk(this)
    return out
}

internal fun escapeXml(value: String): String = buildString {
    for (c in value) when (c) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&apos;")
        else -> append(c)
    }
}

internal fun soapEnvelope(body: String, header: String = ""): String = buildString {
    append("""<?xml version="1.0" encoding="UTF-8"?>""")
    append("""<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" """)
    append("""xmlns:tds="http://www.onvif.org/ver10/device/wsdl" """)
    append("""xmlns:trt="http://www.onvif.org/ver10/media/wsdl" """)
    append("""xmlns:tt="http://www.onvif.org/ver10/schema">""")
    if (header.isNotEmpty()) append("<s:Header>$header</s:Header>")
    append("<s:Body>$body</s:Body></s:Envelope>")
}

internal fun wsseUsernameToken(user: String, password: String): String {
    val nonce = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
    val nonceB64 = java.util.Base64.getEncoder().encodeToString(nonce)
    val created = java.time.Instant.now().toString()
    // PasswordDigest = Base64(SHA1(NonceBytes + CreatedUtf8 + PasswordUtf8))
    val digestInput = nonce + created.toByteArray(Charsets.UTF_8) + password.toByteArray(Charsets.UTF_8)
    val digestB64 = java.util.Base64.getEncoder().encodeToString(
        java.security.MessageDigest.getInstance("SHA-1").digest(digestInput)
    )
    return """
        <s:Security s:mustUnderstand="1">
          <wsse:UsernameToken xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
            <wsse:Username>${escapeXml(user)}</wsse:Username>
            <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">$digestB64</wsse:Password>
            <wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">$nonceB64</wsse:Nonce>
            <wsu:Created xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">$created</wsu:Created>
          </wsse:UsernameToken>
        </s:Security>
    """.trimIndent()
}
