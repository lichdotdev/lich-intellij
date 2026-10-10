package dev.lich.intellij.core

/** What to do about where the text goes. */
sealed interface Decision {
    /** One session is the obvious place. */
    data class Use(val peer: Peer) : Decision

    /** The person has to pick among [peers]; [reason] says why they are being asked. */
    data class Ask(val peers: List<Peer>, val reason: String) : Decision

    /** There is nowhere to send it; [reason] is worded for the person. */
    data class None(val reason: String) : Decision
}

object Chooser {
    /**
     * Picks the session for the folder the IDE has open.
     *
     * One session beside the folder is used without asking — but never one that
     * works in another checkout than a worktree the IDE has open, nor one whose
     * project merely contains the folder: pasting there unasked is pasting
     * somewhere wrong. Several are asked
     * about, unless the person already chose one for this folder and it is still
     * among them — then the same again, because asking every time about a choice
     * made once is the part of this nobody forgives. With none beside it, every
     * session is offered: the folder may simply be somewhere lich does not know
     * as one project (a checkout reached through a symlink, say).
     */
    fun decide(peers: List<Peer>, dir: String?, remembered: String?, alwaysAsk: Boolean): Decision {
        if (peers.isEmpty()) {
            return Decision.None("No lich session is running.")
        }
        val near = dir?.let { SessionMatcher.nearest(peers, it) }
            ?: return Decision.Ask(peers, "No lich session is open on this project. Pick one:")
        if (alwaysAsk) return Decision.Ask(near.peers, "Send to which session?")
        // Asked every time, the remembered one included: a choice remembered here may be one made
        // without asking, by a release that used a lone session of these kinds unasked.
        when (near.fit) {
            Fit.WORKTREE -> return Decision.Ask(near.peers, "No lich session is open in this worktree, only in its project. Send to which?")
            Fit.INSIDE -> return Decision.Ask(near.peers, "No lich session is open on this project, only on one around it. Send to which?")
            Fit.EXACT, Fit.PROJECT -> Unit
        }
        val again = near.peers.firstOrNull { it.id == remembered }
        if (again != null) return Decision.Use(again)
        val single = near.peers.singleOrNull()
        if (single != null) return Decision.Use(single)
        return Decision.Ask(near.peers, "Several sessions are open on this project. Send to which?")
    }
}
