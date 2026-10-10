package dev.lich.intellij.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionWatchTest {
    private fun peer(id: String, state: String) = Peer(id, "Session $id", "p", "claude", state, "/p", "/p")

    @Test
    fun `a session that stops working has finished`() {
        val changes = SessionWatch.changes(listOf(peer("1", "busy")), listOf(peer("1", "done")))
        assertEquals(listOf(SessionWatch.Change(peer("1", "done"), SessionWatch.Event.FINISHED)), changes)
    }

    @Test
    fun `a session that starts waiting on a permission asks for you`() {
        val changes = SessionWatch.changes(listOf(peer("1", "busy")), listOf(peer("1", "waiting")))
        assertEquals(listOf(SessionWatch.Change(peer("1", "waiting"), SessionWatch.Event.WAITING)), changes)
    }

    @Test
    fun `nothing is news on the first look, nor for a session that stays as it was`() {
        assertEquals(emptyList(), SessionWatch.changes(null, listOf(peer("1", "waiting"))))
        assertEquals(emptyList(), SessionWatch.changes(listOf(peer("1", "done")), listOf(peer("1", "done"))))
    }

    @Test
    fun `a session that appears already waiting asks for you`() {
        val changes = SessionWatch.changes(emptyList(), listOf(peer("2", "waiting")))
        assertEquals(listOf(SessionWatch.Change(peer("2", "waiting"), SessionWatch.Event.WAITING)), changes)
    }

    @Test
    fun `the summary counts what needs you first`() {
        val peers = listOf(peer("1", "busy"), peer("2", "waiting"), peer("3", "busy"), peer("4", "done"))
        assertEquals("1 waiting · 2 working · 1 done", SessionWatch.summary(peers))
        assertEquals("1 idle", SessionWatch.summary(listOf(peer("1", ""))))
        assertEquals("no session", SessionWatch.summary(emptyList()))
    }
}
