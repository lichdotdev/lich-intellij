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
     * One session beside the folder is used without asking. Several are asked
     * about, unless the person already chose one for this folder and it is still
     * among them — then the same again, because asking every time about a choice
     * made once is the part of this nobody forgives. With none beside it, every
     * session is offered: the folder may simply be somewhere lich does not know
     * as one project (a checkout reached through a symlink, say).
     */
    fun decide(peers: List<Peer>, dir: String?, remembered: String?, alwaysAsk: Boolean): Decision {
        if (peers.isEmpty()) {
            return Decision.None("No lich session is running. Open one in lich first.")
        }
        val near = dir?.let { SessionMatcher.nearest(peers, it) }
            ?: return Decision.Ask(peers, "No lich session is open on this project. Pick one:")
        if (alwaysAsk) return Decision.Ask(near.peers, "Send to which session?")
        val single = near.peers.singleOrNull()
        if (single != null) return Decision.Use(single)
        val again = near.peers.firstOrNull { it.id == remembered }
        if (again != null) return Decision.Use(again)
        return Decision.Ask(near.peers, "Several sessions are open on this project. Send to which?")
    }
}
