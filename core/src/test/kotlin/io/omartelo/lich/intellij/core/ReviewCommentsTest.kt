package io.omartelo.lich.intellij.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewCommentsTest {
    @Test
    fun `every comment goes in one list, anchored like a selection`() {
        val comments = listOf(
            ReviewComment(Snippets.location("src/a.kt", 12, 30), "leaks the listener"),
            ReviewComment(Snippets.location("src/b.kt", 44, 44), "rename to X"),
        )
        assertEquals(
            "Review comments:\n\n- src/a.kt:12-30 — leaks the listener\n- src/b.kt:44 — rename to X",
            ReviewComments.compose(comments),
        )
    }

    @Test
    fun `a remark about the whole change has no anchor and keeps its place`() {
        val comments = listOf(ReviewComment("src/a.kt:12", "leaks the listener"), ReviewComment(null, "all the same bug"))
        assertEquals(
            "Review comments:\n\n- src/a.kt:12 — leaks the listener\n- all the same bug",
            ReviewComments.compose(comments),
        )
    }

    @Test
    fun `a note not yet added travels last, and a blank one not at all`() {
        val comments = listOf(ReviewComment("src/a.kt:12", "leaks the listener"))
        assertEquals(
            "Review comments:\n\n- src/a.kt:12 — leaks the listener\n- same bug everywhere",
            ReviewComments.compose(comments, "  same bug everywhere  "),
        )
        assertEquals("Review comments:\n\n- src/a.kt:12 — leaks the listener", ReviewComments.compose(comments, " \n "))
    }

    @Test
    fun `a multi-line comment stays one list item`() {
        assertEquals(
            "Review comments:\n\n- a.kt:1 — first\n  second\n  third",
            ReviewComments.compose(listOf(ReviewComment("a.kt:1", "first\r\nsecond\nthird\n"))),
        )
    }
}
