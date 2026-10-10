package dev.lich.intellij.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionMatcherTest {
    private val root: Path = Files.createTempDirectory("lich-root").toRealPath()
    private val worktree: Path = Files.createDirectory(root.resolve("wt"))
    private val module: Path = Files.createDirectories(root.resolve("modules/core"))

    private fun peer(id: String, path: Path, project: Path = root, state: String = "done") =
        Peer(id, id, "lich", "claude", state, path.toString(), project.toString())

    @Test
    fun `a session running in the folder beats the rest of its project`() {
        val inRoot = peer("root", root)
        val inWorktree = peer("wt", worktree)

        val found = SessionMatcher.nearest(listOf(inRoot, inWorktree), worktree.toString())!!
        assertEquals(Fit.EXACT, found.fit)
        assertEquals(listOf(inWorktree), found.peers)
    }

    @Test
    fun `without one there, the project's sessions are the answer`() {
        val inWorktree = peer("wt", worktree)

        val found = SessionMatcher.nearest(listOf(inWorktree), root.toString())!!
        assertEquals(Fit.PROJECT, found.fit)
        assertEquals(listOf(inWorktree), found.peers)
    }

    @Test
    fun `a module opened on its own finds the session of the repository holding it`() {
        val inRoot = peer("root", root)

        val found = SessionMatcher.nearest(listOf(inRoot), module.toString())!!
        assertEquals(Fit.INSIDE, found.fit)
        assertEquals(listOf(inRoot), found.peers)
    }

    @Test
    fun `nothing near is nothing, not a guess`() {
        val elsewhere = Files.createTempDirectory("lich-elsewhere").toRealPath()
        assertNull(SessionMatcher.nearest(listOf(peer("root", root)), elsewhere.toString()))
        assertNull(SessionMatcher.nearest(emptyList(), root.toString()))
    }

    @Test
    fun `a session with no directory, as an older lich reports it, matches nothing`() {
        val bare = Peer("s", "s", "lich", "claude", "", "", "")
        assertNull(SessionMatcher.nearest(listOf(bare), root.toString()))
    }

    @Test
    fun `a session waiting on a permission cannot take text`() {
        assertFalse(SessionMatcher.takesText(peer("a", root, state = "waiting")))
        assertTrue(SessionMatcher.takesText(peer("a", root, state = "busy")))
        assertTrue(SessionMatcher.takesText(peer("a", root, state = "")))
    }

    @Test
    fun `a project at the home directory does not contain every folder under it`() {
        val atHome = peer("home", root, project = root)

        assertNull(SessionMatcher.nearest(listOf(atHome), module.toString(), home = root.toString()))
    }

    @Test
    fun `a session at the home directory still matches the home directory itself`() {
        val atHome = peer("home", root, project = root)

        assertEquals(Fit.EXACT, SessionMatcher.nearest(listOf(atHome), root.toString(), home = root.toString())!!.fit)
    }
}
