package dev.lich.intellij.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class GitWorktreeTest {
    private val root: Path = Files.createTempDirectory("git-worktree").toRealPath()
    private val main: Path = root.resolve("centralcotacoes").also { it.resolve(".git").createDirectories() }

    /** A linked worktree as `git worktree add` lays it out: a `.git` file pointing into the main repository. */
    private fun worktree(name: String, head: String): Path {
        val gitDir = main.resolve(".git/worktrees/$name").createDirectories()
        gitDir.resolve("commondir").writeText("../..\n")
        gitDir.resolve("HEAD").writeText("$head\n")
        return root.resolve("worktrees/82bda532049a/$name").createDirectories().also {
            it.resolve(".git").writeText("gitdir: $gitDir\n")
        }
    }

    @Test
    fun `a linked worktree resolves to the main checkout and the branch it holds`() {
        val dir = worktree("lucky-sparrow", "ref: refs/heads/feat/lucky-sparrow")

        assertEquals(GitWorktree.Linked(main.toString(), "feat/lucky-sparrow"), GitWorktree.of(dir.toString()))
    }

    @Test
    fun `the main checkout is not a linked worktree`() {
        assertNull(GitWorktree.of(main.toString()))
    }

    @Test
    fun `a folder outside git is not a linked worktree`() {
        assertNull(GitWorktree.of(root.toString()))
    }

    @Test
    fun `a detached worktree still belongs to its main checkout, with no branch`() {
        val dir = worktree("detached", "3f2a9c0d1e")

        assertEquals(GitWorktree.Linked(main.toString(), null), GitWorktree.of(dir.toString()))
    }

    @Test
    fun `a session cannot be opened in a detached worktree, which lich opens by branch`() {
        val dir = worktree("detached", "3f2a9c0d1e")
        val runner = CommandRunner { _, _, _ -> Run(0, """{"id":"s9","label":"x"}""", "") }

        assertFailsWith<LichException> { LichClient("lich", runner).open(dir.toString(), "claude") }
    }

    @Test
    fun `a worktree finds its project's sessions before one that merely contains it`() {
        val dir = worktree("lucky-sparrow", "ref: refs/heads/lucky-sparrow")
        val project = Peer("s7", "Session 7", "central", "claude", "", main.toString(), main.toString())
        val home = Peer("s152", "Session 152", "home", "shell", "", root.toString(), root.toString())

        val found = SessionMatcher.nearest(listOf(home, project), dir.toString())!!

        assertEquals(Fit.WORKTREE, found.fit)
        assertEquals(listOf(project), found.peers)
    }

    @Test
    fun `a session of the main checkout is asked about, not used, from a worktree with none of its own`() {
        val dir = worktree("lucky-sparrow", "ref: refs/heads/lucky-sparrow")
        val project = Peer("s7", "Session 7", "central", "claude", "", main.toString(), main.toString())

        val ask = assertIs<Decision.Ask>(Chooser.decide(listOf(project), dir.toString(), "s7", false))
        assertEquals(listOf(project), ask.peers)
    }

    @Test
    fun `a session opened from a worktree opens in its project, on its branch`() {
        val dir = worktree("lucky-sparrow", "ref: refs/heads/lucky-sparrow")
        var command = emptyList<String>()
        val runner = CommandRunner { args, _, _ ->
            command = args
            Run(0, """{"id":"s9","label":"lucky-sparrow"}""", "")
        }

        LichClient("lich", runner).open(dir.toString(), "claude")

        assertEquals(
            listOf("lich", "open", "--project", main.toString(), "--worktree", "lucky-sparrow", "--kind", "claude", "--json"),
            command,
        )
    }
}
