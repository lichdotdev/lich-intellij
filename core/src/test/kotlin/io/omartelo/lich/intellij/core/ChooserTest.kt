package io.omartelo.lich.intellij.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChooserTest {
    private val root = Files.createTempDirectory("lich-chooser").toRealPath().toString()
    private val elsewhere = Files.createTempDirectory("lich-chooser-else").toRealPath().toString()

    private fun peer(id: String, path: String = root) = Peer(id, id, "p", "claude", "", path, path)

    @Test
    fun `nothing running is nowhere to send`() {
        assertIs<Decision.None>(Chooser.decide(emptyList(), root, null, false))
    }

    @Test
    fun `the one session beside the folder is used without asking`() {
        val decision = Chooser.decide(listOf(peer("a"), peer("far", elsewhere)), root, null, false)
        assertEquals(Decision.Use(peer("a")), decision)
    }

    @Test
    fun `several are asked about`() {
        val decision = Chooser.decide(listOf(peer("a"), peer("b")), root, null, false)
        assertEquals(listOf(peer("a"), peer("b")), assertIs<Decision.Ask>(decision).peers)
    }

    @Test
    fun `a choice already made for this folder is kept while it is still there`() {
        val peers = listOf(peer("a"), peer("b"))
        assertEquals(Decision.Use(peer("b")), Chooser.decide(peers, root, "b", false))
        assertIs<Decision.Ask>(Chooser.decide(peers, root, "gone", false))
    }

    @Test
    fun `always ask asks even for one`() {
        assertIs<Decision.Ask>(Chooser.decide(listOf(peer("a")), root, "a", true))
    }

    @Test
    fun `a folder lich does not know is offered every session`() {
        val all = listOf(peer("a", elsewhere), peer("b", elsewhere))
        val ask = assertIs<Decision.Ask>(Chooser.decide(all, root, null, false))
        assertEquals(all, ask.peers)
        assertTrue(ask.reason.contains("No lich session is open on this project"))
    }

    @Test
    fun `no folder at all offers every session`() {
        assertIs<Decision.Ask>(Chooser.decide(listOf(peer("a")), null, null, false))
    }

    @Test
    fun `a session whose project only contains the folder is asked about, even alone`() {
        val inside = Files.createDirectories(java.nio.file.Path.of(root, "modules/core")).toString()
        val ask = assertIs<Decision.Ask>(Chooser.decide(listOf(peer("home")), inside, null, false))
        assertEquals(listOf(peer("home")), ask.peers)
    }

    @Test
    fun `a containing session is asked about even when it was the one used last`() {
        val inside = Files.createDirectories(java.nio.file.Path.of(root, "modules/core")).toString()
        assertIs<Decision.Ask>(Chooser.decide(listOf(peer("home")), inside, "home", false))
    }
}
