package com.example

import com.example.crypto.Identity
import com.example.crypto.hexToBytesOrNull
import com.example.crypto.toHex
import com.example.data.AppConfig
import com.example.data.Command
import com.example.data.Commands
import com.example.data.ConfigRepository
import com.example.data.Density
import com.example.data.FontChoice
import com.example.data.FontScaleOption
import com.example.data.Panel
import com.example.data.Wallpapers
import com.example.proto.Announcement
import com.example.proto.Capabilities
import com.example.proto.ChatMessage
import com.example.proto.Compress
import com.example.proto.Fragment
import com.example.proto.Limits
import com.example.proto.MeshFrag
import com.example.proto.MsgType
import com.example.proto.Packet
import com.example.proto.Padding
import com.example.proto.ProtoException
import com.example.proto.Reassembler
import com.example.proto.UnknownTlv
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.net.BleMeshEngine
import com.example.ui.BlueBlUiState
import com.example.ui.theme.ThemeCategory
import com.example.ui.theme.Themes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.UUID

class ProtocolCompatTest {

    // Exact test vectors from BlueBl reference implementation (Python + cryptography)
    private val vector1Hex =
        "0102070000018bcfe5680000000a1111111111111111686f6c61206d616c6c61" +
            "e0".repeat(224)

    private val vector2Hex =
        "0102070000018bcfe5680005000a1111111111111111222222222222222201904b4c1c0583090000" +
            "d8".repeat(216)

    private val vectorFirmaHex =
        "0102070000018bcfe568000200111111111111111111686f6c6120646573646520707974686f6e" +
            "f2efeebe84352fc5932d48889feb57c1c64dcf9b75d3e52d2abf94816167ce6e" +
            "4828a54bd229a39f080d9d03843104e629db37c5fb44f15c266bfe07f0518c05" +
            "99".repeat(153)

    private val firmaPubHex = "197f6b23e16c8532c6abc838facd5ea789be0c76b2920334039bfa8b3d368d61"
    private val firmaSigHex =
        "f2efeebe84352fc5932d48889feb57c1c64dcf9b75d3e52d2abf94816167ce6e" +
            "4828a54bd229a39f080d9d03843104e629db37c5fb44f15c266bfe07f0518c05"

    @Test
    fun vector1BroadcastDecodeAndEncodeByteExact() {
        val wire = vector1Hex.hexToBytesOrNull()!!
        val p = Packet.decode(wire)
        assertEquals(1, p.version)
        assertEquals(MsgType.MESSAGE, p.msgType)
        assertEquals(7, p.ttl)
        assertEquals(1_700_000_000_000L, p.timestamp)
        assertArrayEquals(ByteArray(8) { 0x11 }, p.sender)
        assertTrue(p.isBroadcast())
        assertEquals("hola malla", p.payload.toString(Charsets.UTF_8))
        assertNull(p.signature)
        assertArrayEquals(wire, p.encode(true))
    }

    @Test
    fun vector2CompressedRecipientRoundTripByteExact() {
        val wire = vector2Hex.hexToBytesOrNull()!!
        val p = Packet.decode(wire)
        assertArrayEquals(ByteArray(8) { 0x22 }, p.recipient)
        assertArrayEquals(ByteArray(400) { 'a'.code.toByte() }, p.payload)
        assertTrue(p.wire?.compressed == true)
        assertArrayEquals(wire, p.encode(true))
    }

    @Test
    fun vectorFirmaVerifiesAcrossTtlDecrement() {
        val wire = vectorFirmaHex.hexToBytesOrNull()!!
        val pubBytes = firmaPubHex.hexToBytesOrNull()!!
        val p = Packet.decode(wire)
        assertEquals(7, p.ttl)
        assertEquals("hola desde python", p.payload.toString(Charsets.UTF_8))
        assertTrue(p.verify(pubBytes))

        val relayed = p.copy(ttl = 3)
        assertTrue(relayed.verify(pubBytes))
    }

    @Test
    fun deterministicEd25519SignatureMatchesPythonFixture() {
        val seed = ByteArray(32) { 42 }
        val id = Identity.fromBytes(ByteArray(32) { 1 }, seed)
        val p = Packet(
            msgType = MsgType.MESSAGE,
            sender = ByteArray(8) { 0x11 },
            timestamp = 1_700_000_000_000L,
            payload = "hola desde python".toByteArray(Charsets.UTF_8)
        )
        val sig = id.sign(p.forSigning())
        assertEquals(firmaSigHex, sig.toHex())
        p.sign(id)
        assertEquals(vectorFirmaHex, p.encode(true).toHex())
    }

    @Test
    fun peerIdVectorMatchesKnownSha256() {
        val noisePublic = ByteArray(32) { 7 }
        val id = Identity.derivePeerId(noisePublic)
        assertEquals("4bb06f8e4e3a7715", id.toHex())
        assertTrue(Identity.matchesClaimedPeerId("4bb06f8e4e3a7715", noisePublic))
        assertFalse(Identity.matchesClaimedPeerId("4BB06F8E4E3A7715", noisePublic))
    }

    @Test
    fun paddingAndCompressionAndBombProtection() {
        val data = ByteArray(100) { 0xAB.toByte() }
        val padded = Padding.pad(data, Padding.optimalBlockSize(data.size))
        assertEquals(256, padded.size)
        assertArrayEquals(data, Padding.unpad(padded))

        val repetitive = ByteArray(4096) { 'h'.code.toByte() }
        assertTrue(Compress.shouldCompress(repetitive))
        val comp = Compress.compress(repetitive)!!
        assertTrue(comp.size < repetitive.size)
        assertArrayEquals(repetitive, Compress.decompress(comp, repetitive.size))

        var threwBomb = false
        try {
            Compress.decompress(comp, 4096 * 2000)
        } catch (_: ProtoException.CompressionBomb) {
            threwBomb = true
        }
        assertTrue(threwBomb)
    }

    @Test
    fun fragmentAndReassembleAndDedup() {
        val big = ByteArray(2000) { i -> ((i * 1103515245 + 12345) ushr 16).toByte() }
        val pkt = Packet(msgType = MsgType.MESSAGE, sender = ByteArray(8) { 1 }, payload = big)
        val k1 = MeshFrag.dedupKey(pkt)
        val k2 = MeshFrag.dedupKey(pkt.copy(ttl = 2))
        assertEquals(k1, k2)

        val frags = MeshFrag.fragmentPacket(pkt)!!
        assertTrue(frags.size >= 4)

        val reassembler = Reassembler()
        var done: Pair<Int, ByteArray>? = null
        for (f in frags) {
            val res = reassembler.push(Fragment.decode(f.payload))
            if (res != null) done = res
        }
        assertNotNull(done)
        assertEquals(MsgType.MESSAGE, done!!.first)
        val decoded = Packet.decode(done.second)
        assertArrayEquals(big, decoded.payload)
    }

    @Test
    fun announceTlvAndChatMessageRoundTrip() {
        val ann = Announcement(
            nickname = "akaomi",
            noisePublic = ByteArray(32) { 3 },
            signingPublic = ByteArray(32) { 4 },
            capabilities = Capabilities(0b101L),
            unknown = listOf(UnknownTlv(0x04, byteArrayOf(9, 9, 9)))
        )
        val decodedAnn = Announcement.decode(ann.encode())
        assertEquals(ann, decodedAnn)

        val msg = ChatMessage(
            id = "ab12cd34",
            sender = "neon",
            content = "hola ñandú ✦",
            timestampMs = 1_700_000_000_123L,
            isRelay = true,
            mentions = listOf("luna"),
            channel = "#general"
        )
        assertEquals(msg, ChatMessage.decode(msg.encode()))
    }

    @Test
    fun commandsAndThemesAndAccessibilityIntegrity() {
        assertEquals(Command.Help, Commands.parse("/help"))
        assertEquals(Command.Nick("luna"), Commands.parse("/Nick luna"))
        assertEquals(Command.Unknown("join"), Commands.parse("/join #neon"))
        assertNull(Commands.parse("hola mundo"))

        assertEquals(Density.Compacto, Density.parse("compacto"))
        assertEquals(Density.Comodo, Density.parse("cómodo"))
        assertEquals(Density.Grande, Density.parse("grande"))

        assertEquals(50, Themes.all.size)
        assertEquals(50, Themes.all.map { it.id }.toSet().size)
        assertEquals(50, Themes.all.map { it.name }.toSet().size)
        assertEquals("whatsapp-dia", Themes.default().id)
        val defPalette = Themes.default().palette
        assertTrue(defPalette.isLight)
        assertNotEquals(defPalette.accentGradientStart, defPalette.accentGradientEnd)
        assertNotEquals(defPalette.headerGradientStart, defPalette.headerGradientEnd)
        assertNotEquals(defPalette.ownBubbleGradientStart, defPalette.ownBubbleGradientEnd)
        assertNotNull(Themes.find("telegram-dia"))
        assertNotNull(Themes.find("telegram-noche"))
        for (cat in ThemeCategory.entries) {
            assertTrue(Themes.all.any { it.category == cat })
        }

        val hc = defPalette.withHighContrast()
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), hc.ownBubbleFg)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), hc.peerBubbleFg)
        assertEquals(hc.ownBubbleBg, hc.ownBubbleGradientStart)
        assertEquals(hc.ownBubbleBg, hc.ownBubbleGradientEnd)

        assertEquals(listOf(Panel.Pares, Panel.Chat), Panel.entries)
        assertEquals("Personas cerca", Panel.Pares.title)
        assertEquals("Chat", Panel.Chat.title)
        assertEquals(Panel.Pares, BlueBlUiState().activePanel)

        assertEquals(FontScaleOption.ExtraLarge, FontScaleOption.parse("xl"))
        assertEquals(FontChoice.Dyslexia, FontChoice.parse("dyslexia"))
        assertEquals("patron-doodle", Wallpapers.default().id)
        assertEquals(128 * 1024, Limits.maxPayloadBytes(MsgType.MESSAGE))
    }

    @Test
    fun configRepositoryRoundTripIncludesPersonalizationAndAccessibility() {
        val tempDir = Files.createTempDirectory("bluebl_cfg_test").toFile()
        try {
            val repo = ConfigRepository(tempDir)
            val custom = AppConfig(
                nick = "sofia",
                theme = "telegram-dia",
                density = "grande",
                wallpaperId = "degradado-cielo",
                wallpaperDim = 0.32f,
                fontScale = "xl",
                fontChoice = "dyslexia",
                highContrast = true,
                reduceMotion = true
            )
            repo.saveConfig(custom)
            val loaded = repo.loadConfig()
            assertEquals(custom, loaded)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun allThemesHaveReadableAccentAndBubbleContrast() {
        fun contrast(fg: Color, bg: Color): Float {
            val l1 = fg.luminance()
            val l2 = bg.luminance()
            return (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
        }

        Themes.all.forEach { def ->
            val p = def.palette
            val accentC1 = contrast(p.onAccent, p.accentGradientStart)
            val accentC2 = contrast(p.onAccent, p.accentGradientEnd)
            assertTrue(
                "Theme ${def.id} onAccent contrast too low on start ($accentC1)",
                accentC1 >= 3.0f
            )
            assertTrue(
                "Theme ${def.id} onAccent contrast too low on end ($accentC2)",
                accentC2 >= 3.0f
            )

            val ownC1 = contrast(p.ownBubbleFg, p.ownBubbleGradientStart)
            val ownC2 = contrast(p.ownBubbleFg, p.ownBubbleGradientEnd)
            assertTrue(
                "Theme ${def.id} ownBubbleFg contrast too low on start ($ownC1)",
                ownC1 >= 4.0f
            )
            assertTrue(
                "Theme ${def.id} ownBubbleFg contrast too low on end ($ownC2)",
                ownC2 >= 4.0f
            )

            val peerC = contrast(p.peerBubbleFg, p.peerBubbleBg)
            assertTrue(
                "Theme ${def.id} peerBubbleFg contrast too low ($peerC)",
                peerC >= 4.5f
            )

            val composerC = contrast(p.fg, p.composerFieldBg)
            assertTrue(
                "Theme ${def.id} composer text contrast too low ($composerC)",
                composerC >= 4.5f
            )

            val hc = p.withHighContrast()
            assertTrue(
                "Theme ${def.id} high-contrast onAccent contrast too low",
                contrast(hc.onAccent, hc.accent) >= 4.0f
            )
            assertTrue(
                "Theme ${def.id} high-contrast composer contrast too low",
                contrast(hc.fg, hc.composerFieldBg) >= 4.5f
            )
        }
    }

    @Test
    fun bleProtocolUuidsMatchBlueBlSpec() {
        assertEquals(
            UUID.fromString("f47b5e2d-4a9e-4c5a-9b3f-8e1d2c3a4b5c"),
            BleMeshEngine.SERVICE_UUID
        )
        assertEquals(
            UUID.fromString("f47b5e2d-4a9e-4c5a-9b3f-8e1d2c3a4b5a"),
            BleMeshEngine.TESTNET_SERVICE_UUID
        )
        assertEquals(
            UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d"),
            BleMeshEngine.CHAR_UUID
        )
        assertEquals(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BleMeshEngine.CCCD_UUID
        )
    }

    @Test
    fun mtuAdaptiveAnnounceFramingAndVerify() {
        val id = Identity.generate()
        val ann = Announcement(
            nickname = "pc-bluebl",
            noisePublic = id.noisePublic(),
            signingPublic = id.signingPublic()
        )
        val pkt = Packet(
            msgType = MsgType.ANNOUNCE,
            sender = id.peerId(),
            recipient = Packet.BROADCAST.copyOf(),
            payload = ann.encode()
        )
        pkt.sign(id)

        // Large MTU (517 -> 514 payload): keeps 256-byte padded block
        val frames517 = MeshFrag.fragmentForWireLimit(pkt, 514)
        assertEquals(1, frames517.size)
        assertEquals(256, frames517[0].size)
        val dec517 = Packet.decode(frames517[0])
        assertTrue(dec517.verify(id.signingPublic()))

        // Common BlueZ / BLE 4.2 MTU (247 -> 244 payload): uses compact unpadded frame (< 244B)
        val frames247 = MeshFrag.fragmentForWireLimit(pkt, 244)
        assertEquals(1, frames247.size)
        assertTrue(frames247[0].size <= 244)
        val dec247 = Packet.decode(frames247[0])
        assertTrue(dec247.verify(id.signingPublic()))
        assertEquals("pc-bluebl", Announcement.decode(dec247.payload).nickname)

        // Tight MTU (103 -> 100 payload): splits into reassemblable fragments
        val frames100 = MeshFrag.fragmentForWireLimit(pkt, 100)
        assertTrue(frames100.size >= 2)
        assertTrue(frames100.all { it.size <= 100 })
        val reassembler = Reassembler()
        var assembled: Pair<Int, ByteArray>? = null
        for (raw in frames100) {
            val outer = Packet.decode(raw)
            assertEquals(MsgType.FRAGMENT, outer.msgType)
            val res = reassembler.push(Fragment.decode(outer.payload))
            if (res != null) assembled = res
        }
        assertNotNull(assembled)
        val inner = Packet.decode(assembled!!.second)
        assertTrue(inner.verify(id.signingPublic()))
        assertEquals("pc-bluebl", Announcement.decode(inner.payload).nickname)
    }
}
