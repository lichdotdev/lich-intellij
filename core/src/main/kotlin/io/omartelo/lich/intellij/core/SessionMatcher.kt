package io.omartelo.lich.intellij.core

/** How closely a session sits to the folder the IDE has open. */
enum class Fit {
    /** The session runs in the open folder itself. */
    EXACT,

    /** The open folder is the root of the session's project, or the project's other checkout. */
    PROJECT,

    /** The open folder is a git worktree of the session's project, and the session runs in another checkout of it. */
    WORKTREE,

    /** The open folder lies inside the session's project or checkout (a module opened on its own). */
    INSIDE,
}

data class Candidates(val fit: Fit, val peers: List<Peer>)

object SessionMatcher {
    /**
     * The sessions beside [dir], the closest kind only: a session running in the
     * folder beats the project's others, which beat those of the project a
     * worktree belongs to, which beat a project that merely contains it. Mirrors how `lich insert --project` reads a directory, plus
     * the last step, because an IDE is often opened on a module rather than on
     * the repository. Null when nothing is near, which is for the caller to
     * answer by offering every session rather than guessing.
     */
    fun nearest(peers: List<Peer>, dir: String, home: String = System.getProperty("user.home")): Candidates? {
        val exact = peers.filter { PathUtil.same(it.path, dir) }
        if (exact.isNotEmpty()) return Candidates(Fit.EXACT, exact)
        val project = peers.filter { PathUtil.same(it.projectPath, dir) }
        if (project.isNotEmpty()) return Candidates(Fit.PROJECT, project)
        val mainCheckout = GitWorktree.of(dir)?.mainCheckout
        val worktreeOf = if (mainCheckout == null) emptyList() else peers.filter { PathUtil.same(it.projectPath, mainCheckout) }
        if (worktreeOf.isNotEmpty()) return Candidates(Fit.WORKTREE, worktreeOf)
        // A project at the home directory contains every folder, which says nothing about where the text belongs.
        val inside = peers.filter { !PathUtil.same(it.projectPath, home) }
            .filter { PathUtil.isWithin(dir, it.path) || PathUtil.isWithin(dir, it.projectPath) }
        if (inside.isNotEmpty()) return Candidates(Fit.INSIDE, inside)
        return null
    }

    /**
     * Whether a session can be typed at now. `waiting` is a permission prompt:
     * text pasted there goes to the dialog. lich refuses it too; knowing here
     * lets the chooser say so before the person picks it.
     */
    fun takesText(peer: Peer): Boolean = peer.state != "waiting"
}
