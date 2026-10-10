package io.omartelo.lich.intellij.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProtocolTest {
    @Test
    fun `reads a session list and ignores fields it does not know`() {
        val peers = Protocol.parsePeers(
            """[{"label":"docs","name":"lich-a1","project":"lich","kind":"codex","state":"busy","id":"s2",
               "path":"/src/lich","projectPath":"/src/lich","future":{"x":1}}]""",
        )
        assertEquals(
            listOf(Peer("s2", "docs", "lich", "codex", "busy", "/src/lich", "/src/lich")),
            peers,
        )
    }

    @Test
    fun `an empty roster is an empty list`() {
        assertEquals(emptyList(), Protocol.parsePeers("[]"))
    }

    @Test
    fun `a lich that predates the directories reads as empty ones`() {
        val peer = Protocol.parsePeers("""[{"label":"a","id":"s1"}]""").single()
        assertEquals("", peer.path)
        assertEquals("", peer.projectPath)
    }

    @Test
    fun `reads what an insert did`() {
        val inserted = Protocol.parseInserted("""{"id":"s2","label":"docs","project":"lich","kind":"codex","bytes":42}""")
        assertEquals(Inserted("s2", "docs", "lich", "codex", 42), inserted)
    }

    @Test
    fun `refuses what is not the shape it expects`() {
        assertFailsWith<LichException> { Protocol.parsePeers("{}") }
        assertFailsWith<LichException> { Protocol.parseInserted("[]") }
        assertFailsWith<LichException> { Protocol.parsePeers("not json {") }
    }
}
