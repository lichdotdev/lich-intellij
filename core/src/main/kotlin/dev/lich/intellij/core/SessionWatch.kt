package dev.lich.intellij.core

/**
 * What changed between two looks at a project's sessions. lich has no
 * supported way to push a change (its `/events` endpoint is internal), so the
 * IDE looks again every few seconds and compares.
 */
object SessionWatch {
    enum class Event { FINISHED, WAITING }

    data class Change(val peer: Peer, val event: Event)

    /**
     * The sessions that finished a turn or started waiting on a permission since
     * [before]. Null [before] is the first look, which reports nothing: what was
     * already so when the IDE opened is not news.
     */
    fun changes(before: List<Peer>?, after: List<Peer>): List<Change> {
        if (before == null) return emptyList()
        val was = before.associateBy { it.id }
        return after.mapNotNull { peer ->
            val previous = was[peer.id]?.state
            when {
                peer.state == previous -> null
                peer.state == WAITING -> Change(peer, Event.WAITING)
                peer.state == DONE && previous == BUSY -> Change(peer, Event.FINISHED)
                else -> null
            }
        }
    }

    /** `1 waiting · 2 working · 1 done`: what needs the person comes first. */
    fun summary(peers: List<Peer>): String {
        if (peers.isEmpty()) return "no session"
        // A state this plugin does not know yet is counted with the idle ones rather than dropped.
        val counts = peers.groupingBy { if (it.state in KNOWN) it.state else "" }.eachCount()
        return listOf(WAITING to "waiting", BUSY to "working", DONE to "done", "" to "idle")
            .mapNotNull { (state, word) -> counts[state]?.let { "$it $word" } }
            .joinToString(" · ")
    }

    // The states `lich sessions --json` reports; "" is a session that has not run a turn yet.
    private const val BUSY = "busy"
    private const val DONE = "done"
    private const val WAITING = "waiting"
    private val KNOWN = setOf(BUSY, DONE, WAITING)
}
