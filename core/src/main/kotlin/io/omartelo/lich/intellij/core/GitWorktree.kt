package io.omartelo.lich.intellij.core

import java.nio.file.Files
import java.nio.file.Path

/**
 * Whether a folder is a linked git worktree, read from the files git keeps for
 * it. lich opens a session in a worktree by its project and branch: given the
 * worktree's own path it would open that folder as a project of its own.
 */
object GitWorktree {
    /** The main checkout a worktree belongs to, and the branch it has checked out: null on a detached HEAD. */
    data class Linked(val mainCheckout: String, val branch: String?)

    /** The worktree [dir] is, or null when it is a main checkout or not in git. */
    fun of(dir: String): Linked? {
        val dotGit = Path.of(dir).resolve(".git")
        if (!Files.isRegularFile(dotGit)) return null
        val gitDir = Files.readString(dotGit).trim().removePrefix("gitdir:").trim().let { Path.of(dir).resolve(it) }
        val commonDir = gitDir.resolve("commondir").takeIf { Files.isRegularFile(it) } ?: return null
        val mainGitDir = gitDir.resolve(Files.readString(commonDir).trim()).normalize()
        val head = Files.readString(gitDir.resolve("HEAD")).trim()
        val branch = head.removePrefix("ref: refs/heads/").takeIf { it != head }
        return Linked(mainGitDir.parent.toString(), branch)
    }
}
