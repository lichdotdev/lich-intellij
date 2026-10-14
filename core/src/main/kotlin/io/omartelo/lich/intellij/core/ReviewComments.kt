package io.omartelo.lich.intellij.core

/**
 * A note written while reading code, waiting in a batch to reach the session in one paste. [location] is where it
 * was written, as [Snippets.location] names it; null for a remark about the change as a whole.
 */
data class ReviewComment(val location: String?, val text: String)

/** The text a batch of review comments becomes at the prompt — the same paste lich's own review panel sends. */
object ReviewComments {
    /**
     * A `Review comments:` list, one bullet per comment in the order written, each anchored like a selection's
     * header. [note], when not blank, joins the list last, unanchored: a remark typed but not yet added still
     * travels with the batch it was written for.
     */
    fun compose(comments: List<ReviewComment>, note: String = ""): String {
        val items = comments.map { item(it.location, it.text) } +
            listOfNotNull(note.trim().takeIf { it.isNotEmpty() }?.let { item(null, it) })
        return "Review comments:\n\n" + items.joinToString("\n")
    }

    // A comment written across several lines stays one list item: its own newlines are indented under the bullet.
    private fun item(location: String?, text: String): String {
        val body = text.trim().replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\n  ")
        return if (location == null) "- $body" else "- $location — $body"
    }
}
